-- Archivio ricette di Banbury 1 su SQL Server (come B2), database [mondo] su srv-bambury.
-- Da eseguire a mano da chi amministra SQL Server (il programma non crea ne' modifica lo schema).
-- Sostituire <PASSWORD> e NON salvarla in file del progetto: va nella variabile d'ambiente B1_RECIPES_SQL_PASSWORD.

USE [mondo];
GO

-- Una riga per ricetta
CREATE TABLE dbo.B1Recipes (
    Id            INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_B1Recipes PRIMARY KEY,
    RecipeUID     NVARCHAR(36)  NOT NULL CONSTRAINT UQ_B1Recipes_UID UNIQUE,
    Nome          NVARCHAR(32)  NOT NULL CONSTRAINT UQ_B1Recipes_Nome UNIQUE,
    NumRicetta    INT           NOT NULL CONSTRAINT DF_B1Recipes_Num DEFAULT 0,
    Revisione     BIGINT        NOT NULL,
    Creazione     DATETIME2(0)  NOT NULL,
    Modifica      DATETIME2(0)  NOT NULL,
    ModificatoDa  NVARCHAR(100) NOT NULL CONSTRAINT DF_B1Recipes_Da DEFAULT N''
);
GO

-- Valori della ricetta: Fase 0 = testata, 1..10 = fasi; Chiave = nome del campo (es. rpm, timeout, temp, press, ops)
CREATE TABLE dbo.B1RecipeValues (
    RecipeId INT          NOT NULL CONSTRAINT FK_B1RecipeValues_Recipe REFERENCES dbo.B1Recipes(Id),
    Fase     SMALLINT     NOT NULL,
    Chiave   NVARCHAR(40) NOT NULL,
    Valore   INT          NOT NULL,
    CONSTRAINT PK_B1RecipeValues PRIMARY KEY (RecipeId, Fase, Chiave)
);
GO

-- Registro degli invii al PLC (tracciabilita', come RecipePLC di B2): una riga per ogni invio, riuscito o no
CREATE TABLE dbo.B1RecipePLC (
    Id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_B1RecipePLC PRIMARY KEY,
    DataOra       DATETIME2(0)  NOT NULL,
    RecipeUID     NVARCHAR(36)  NULL,
    Nome          NVARCHAR(32)  NOT NULL,
    NumRicetta    INT           NOT NULL CONSTRAINT DF_B1RecipePLC_Num DEFAULT 0,
    Revisione     BIGINT        NOT NULL CONSTRAINT DF_B1RecipePLC_Rev DEFAULT 0,
    Utente        NVARCHAR(100) NOT NULL CONSTRAINT DF_B1RecipePLC_Utente DEFAULT N'',
    Postazione    NVARCHAR(100) NOT NULL CONSTRAINT DF_B1RecipePLC_Post DEFAULT N'',
    Esito         NVARCHAR(10)  NOT NULL,           -- OK oppure ERRORE
    ParoleScritte INT           NOT NULL CONSTRAINT DF_B1RecipePLC_Parole DEFAULT 0,
    FileBackup    NVARCHAR(260) NULL,               -- copia di sicurezza del DB201 prima dell'invio
    Messaggio     NVARCHAR(400) NULL
);
CREATE INDEX IX_B1RecipePLC_DataOra ON dbo.B1RecipePLC (DataOra DESC);
GO

-- OPZIONALE: utente dedicato per il programma B1 (consigliato: non usare un account amministrativo sulle postazioni).
-- Se vuoi usarlo, togli i commenti "-- " alle righe qui sotto e sostituisci <PASSWORD>.
-- In alternativa assegna gli stessi permessi (GRANT) a un utente gia' esistente.
-- Legge e scrive SOLO queste due tabelle (nessun altro oggetto).
-- USE [master];
-- CREATE LOGIN [b1_recipes] WITH PASSWORD = N'<PASSWORD>', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
-- GO
-- USE [mondo];
-- CREATE USER [b1_recipes] FOR LOGIN [b1_recipes];
-- GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1Recipes TO [b1_recipes];
-- GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1RecipeValues TO [b1_recipes];
-- GRANT SELECT, INSERT ON OBJECT::dbo.B1RecipePLC TO [b1_recipes];
-- GO


-- Verifica
-- SELECT * FROM dbo.B1Recipes;
-- SELECT permission_name, OBJECT_NAME(major_id) AS oggetto FROM sys.database_permissions WHERE grantee_principal_id = USER_ID('b1_recipes');
