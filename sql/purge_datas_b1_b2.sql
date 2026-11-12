/* =====================================================================================
   Pulizia dei dati vecchi di dbo.Datas_B1 e dbo.Datas_B2 (database DATA_BAMBURY)

   Le due tabelle sono log con una riga ogni ~2 secondi (~43.000 righe/giorno ciascuna).
   Strategia: conservare gli ultimi @RetentionDays giorni e cancellare il resto A BLOCCHI,
   di notte, cosi' i lock restano brevi e il log delle transazioni non esplode.

   SICUREZZA
   - La procedura parte in PROVA (@Execute = 0): conta soltanto, non cancella nulla.
   - Retention minima 7 giorni, blocchi da 1.000 a 100.000 righe.
   - Viene toccata SOLO la colonna Date_record di Datas_B1 e Datas_B2 (nessun'altra tabella).
   - Prima della prima cancellazione vera: fare un BACKUP completo di DATA_BAMBURY.
   - NON PROVATO su questo server: eseguire prima in prova, controllare i conteggi.

   ESECUZIONE
     1) Eseguire tutto questo script (crea la procedura; non cancella niente).
     2) Prova:   EXEC dbo.usp_PurgeDatasLog @RetentionDays = 90, @Execute = 0;
     3) Se i conteggi sono giusti:   EXEC dbo.usp_PurgeDatasLog @RetentionDays = 90, @Execute = 1;
     4) Pianificazione notturna: sezione "JOB" in fondo (creato DISABILITATO).
   ===================================================================================== */
USE DATA_BAMBURY;
GO

CREATE OR ALTER PROCEDURE dbo.usp_PurgeDatasLog
    @RetentionDays int = 90,      -- giorni da conservare
    @BatchSize     int = 20000,   -- righe cancellate per blocco
    @MaxBatches    int = 1000,    -- tetto di blocchi per esecuzione (1000 x 20000 = 20 M righe)
    @Execute       bit = 0        -- 0 = PROVA (solo conteggi) ; 1 = cancella davvero
AS
BEGIN
    SET NOCOUNT ON;

    IF @RetentionDays < 7
    BEGIN
        RAISERROR(N'RetentionDays minimo = 7', 16, 1);
        RETURN;
    END;
    IF @BatchSize < 1000 OR @BatchSize > 100000
    BEGIN
        RAISERROR(N'BatchSize deve essere tra 1000 e 100000', 16, 1);
        RETURN;
    END;

    DECLARE @cutoff date = DATEADD(DAY, -@RetentionDays, CAST(GETDATE() AS date));
    DECLARE @tables TABLE (id int IDENTITY(1,1), tname sysname);
    INSERT @tables (tname) VALUES (N'Datas_B1'), (N'Datas_B2');

    DECLARE @i int = 1;
    DECLARE @n int = (SELECT COUNT(*) FROM @tables);
    DECLARE @t sysname;
    DECLARE @dt sysname;
    DECLARE @pred nvarchar(400);
    DECLARE @sql nvarchar(max);
    DECLARE @sample nvarchar(60);
    DECLARE @tot bigint;
    DECLARE @old bigint;
    DECLARE @deleted bigint;
    DECLARE @rows int;
    DECLARE @loops int;

    PRINT CONCAT(N'Cutoff: si conservano le righe con Date_record >= ', CONVERT(nvarchar(10), @cutoff, 23),
                 N'  (', @RetentionDays, N' giorni).  Modalita'': ',
                 CASE WHEN @Execute = 1 THEN N'CANCELLAZIONE' ELSE N'PROVA (nessuna modifica)' END);

    WHILE @i <= @n
    BEGIN
        SELECT @t = tname FROM @tables WHERE id = @i;
        SET @i = @i + 1;

        SET @dt = NULL;
        SELECT @dt = DATA_TYPE
          FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = N'dbo' AND TABLE_NAME = @t AND COLUMN_NAME = N'Date_record';

        IF @dt IS NULL
        BEGIN
            PRINT CONCAT(N'[', @t, N'] tabella o colonna Date_record non trovata: saltata');
            CONTINUE;
        END;

        IF @dt IN (N'date', N'datetime', N'datetime2', N'smalldatetime')
            SET @pred = N'[Date_record] < @c';
        ELSE IF @dt IN (N'char', N'varchar', N'nchar', N'nvarchar')
        BEGIN
            -- data salvata come testo: ammessa solo se in formato ISO aaaa-mm-gg
            SET @sample = NULL;
            SET @sql = N'SELECT TOP (1) @s = CAST([Date_record] AS nvarchar(60)) FROM dbo.' + QUOTENAME(@t)
                     + N' ORDER BY [Date_record] DESC;';
            EXEC sp_executesql @sql, N'@s nvarchar(60) OUTPUT', @s = @sample OUTPUT;
            IF @sample IS NULL OR @sample NOT LIKE N'[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]%'
            BEGIN
                PRINT CONCAT(N'[', @t, N'] Date_record e'' testo ma non in formato aaaa-mm-gg (esempio: ',
                             ISNULL(@sample, N'vuota'), N'): saltata');
                CONTINUE;
            END;
            SET @pred = N'[Date_record] < CONVERT(varchar(10), @c, 23)';
        END
        ELSE
        BEGIN
            PRINT CONCAT(N'[', @t, N'] tipo di Date_record non gestito (', @dt, N'): saltata');
            CONTINUE;
        END;

        -- conteggi
        SET @tot = 0;
        SET @old = 0;
        SET @sql = N'SELECT @tot = COUNT_BIG(*), @old = COALESCE(SUM(CASE WHEN ' + @pred
                 + N' THEN CAST(1 AS bigint) ELSE 0 END), 0) FROM dbo.' + QUOTENAME(@t) + N';';
        EXEC sp_executesql @sql, N'@c date, @tot bigint OUTPUT, @old bigint OUTPUT',
             @c = @cutoff, @tot = @tot OUTPUT, @old = @old OUTPUT;
        PRINT CONCAT(N'[', @t, N'] righe totali: ', @tot, N' ; da cancellare: ', @old,
                     N' ; resterebbero: ', @tot - @old);

        IF @Execute = 0 OR @old = 0
            CONTINUE;

        -- cancellazione a blocchi
        SET @deleted = 0;
        SET @loops = 0;
        SET @sql = N'DELETE TOP (@b) FROM dbo.' + QUOTENAME(@t) + N' WHERE ' + @pred + N'; SET @r = @@ROWCOUNT;';
        WHILE @loops < @MaxBatches
        BEGIN
            SET @rows = 0;
            EXEC sp_executesql @sql, N'@b int, @c date, @r int OUTPUT',
                 @b = @BatchSize, @c = @cutoff, @r = @rows OUTPUT;
            IF @rows = 0 BREAK;
            SET @deleted = @deleted + @rows;
            SET @loops = @loops + 1;
            WAITFOR DELAY '00:00:01';   -- pausa tra i blocchi: lascia spazio alle scritture del logger
        END;
        PRINT CONCAT(N'[', @t, N'] cancellate ', @deleted, N' righe in ', @loops, N' blocchi',
                     CASE WHEN @loops >= @MaxBatches THEN N' (tetto raggiunto: proseguira'' alla prossima esecuzione)' ELSE N'' END);
    END;
END;
GO

/* -------------------------------------------------------------------------------------
   INDICE (consigliato, facoltativo): senza un indice su Date_record ogni blocco di DELETE
   scorre la tabella. Va creato UNA volta, in un momento di poco carico (la tabella riceve
   un inserimento ogni 2 secondi). Controllare prima che non esista gia' (Object Explorer ->
   Datas_B1 -> Indexes).  Scommentare per usarlo:

   -- CREATE NONCLUSTERED INDEX IX_Datas_B1_Date_Time ON dbo.Datas_B1 (Date_record, Time_record) WITH (ONLINE = OFF);
   -- CREATE NONCLUSTERED INDEX IX_Datas_B2_Date_Time ON dbo.Datas_B2 (Date_record, Time_record) WITH (ONLINE = OFF);
   ------------------------------------------------------------------------------------- */

/* -------------------------------------------------------------------------------------
   JOB (SQL Server Agent) - ogni notte alle 03:30. Viene creato DISABILITATO: abilitarlo
   solo dopo aver controllato la prova.  Scommentare per usarlo:

   -- USE msdb;
   -- EXEC dbo.sp_add_job @job_name = N'Purge Datas_B1_B2', @enabled = 0,
   --      @description = N'Cancella i log piu'' vecchi di 90 giorni da Datas_B1 e Datas_B2';
   -- EXEC dbo.sp_add_jobstep @job_name = N'Purge Datas_B1_B2', @step_name = N'Purge',
   --      @subsystem = N'TSQL', @database_name = N'DATA_BAMBURY',
   --      @command = N'EXEC dbo.usp_PurgeDatasLog @RetentionDays = 90, @Execute = 1;';
   -- EXEC dbo.sp_add_schedule @schedule_name = N'Ogni notte 03:30', @freq_type = 4, @freq_interval = 1,
   --      @active_start_time = 033000;
   -- EXEC dbo.sp_attach_schedule @job_name = N'Purge Datas_B1_B2', @schedule_name = N'Ogni notte 03:30';
   -- EXEC dbo.sp_add_jobserver @job_name = N'Purge Datas_B1_B2';
   ------------------------------------------------------------------------------------- */
