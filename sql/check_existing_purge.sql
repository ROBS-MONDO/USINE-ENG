/* =====================================================================================
   SOLA LETTURA - nessuna modifica. Verifica se esiste gia' una pulizia/automatismo per
   dbo.Datas_B1 e dbo.Datas_B2 (database DATA_BAMBURY). Eseguire in SSMS, un blocco alla volta.
   ===================================================================================== */
USE DATA_BAMBURY;
GO

/* 1) Il dato piu' utile: da quando c'e' storico?
      MIN molto vecchio = nessuna pulizia mai fatta.
      MIN sempre ~N giorni fa (ripetere il controllo in giorni diversi) = esiste un automatismo. */
SELECT 'Datas_B1' AS tabella, MIN(Date_record) AS dal, MAX(Date_record) AS al, COUNT_BIG(*) AS righe FROM dbo.Datas_B1
UNION ALL
SELECT 'Datas_B2',            MIN(Date_record),        MAX(Date_record),       COUNT_BIG(*)           FROM dbo.Datas_B2;
GO

/* 2) Dimensione delle tabelle */
EXEC sp_spaceused N'dbo.Datas_B1';
EXEC sp_spaceused N'dbo.Datas_B2';
GO

/* 3) Job di SQL Server Agent: elenco completo e passi che nominano le tabelle o fanno DELETE */
SELECT j.name AS job, j.enabled, j.date_modified, s.step_id, s.database_name, LEFT(s.command, 400) AS comando
  FROM msdb.dbo.sysjobs j
  JOIN msdb.dbo.sysjobsteps s ON s.job_id = j.job_id
 ORDER BY j.name, s.step_id;
GO

/* 4) Piani di manutenzione (SSMS: Management -> Maintenance Plans) */
SELECT name, create_date FROM msdb.dbo.sysmaintplan_plans;
GO

/* 5) Oggetti nel database che citano le due tabelle o cancellano righe (procedure, viste, funzioni) */
SELECT o.type_desc, SCHEMA_NAME(o.schema_id) AS schema_name, o.name
  FROM sys.sql_modules m
  JOIN sys.objects o ON o.object_id = m.object_id
 WHERE m.definition LIKE N'%Datas[_]B1%' OR m.definition LIKE N'%Datas[_]B2%'
    OR m.definition LIKE N'%DELETE FROM%'  OR m.definition LIKE N'%TRUNCATE TABLE%';
GO

/* 6) Trigger nel database */
SELECT t.name AS trigger_name, OBJECT_NAME(t.parent_id) AS tabella, t.is_disabled FROM sys.triggers t;
GO

/* 7) Chi sta scrivendo adesso nel database (host e programma: WinCC, Java, ecc.) */
SELECT s.session_id, s.host_name, s.program_name, s.login_name, s.last_request_start_time, r.command
  FROM sys.dm_exec_sessions s
  LEFT JOIN sys.dm_exec_requests r ON r.session_id = s.session_id
 WHERE s.is_user_process = 1 AND s.database_id = DB_ID(N'DATA_BAMBURY')
 ORDER BY s.last_request_start_time DESC;
GO
