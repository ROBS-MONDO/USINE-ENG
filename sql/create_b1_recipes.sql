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

-- Valori della ricetta: Fase 0 = testata, 1..11 = fasi; Chiave = nome del campo (es. rpm, timeout, temp, press, ops)
CREATE TABLE dbo.B1RecipeValues (
    RecipeId INT          NOT NULL CONSTRAINT FK_B1RecipeValues_Recipe REFERENCES dbo.B1Recipes(Id),
    Fase     SMALLINT     NOT NULL,
    Chiave   NVARCHAR(40) NOT NULL,
    Valore   INT          NOT NULL,
    CONSTRAINT PK_B1RecipeValues PRIMARY KEY (RecipeId, Fase, Chiave)
);
GO

-- Utente dell'editor ricette: legge e scrive SOLO queste due tabelle (nessun altro oggetto, nessun DELETE/ALTER altrove)
USE [master];
CREATE LOGIN [b1_recipes] WITH PASSWORD = N'<PASSWORD>', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
GO
USE [mondo];
CREATE USER [b1_recipes] FOR LOGIN [b1_recipes];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1Recipes TO [b1_recipes];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1RecipeValues TO [b1_recipes];
GO

-- Verifica
-- SELECT * FROM dbo.B1Recipes;
-- SELECT permission_name, OBJECT_NAME(major_id) AS oggetto FROM sys.database_permissions WHERE grantee_principal_id = USER_ID('b1_recipes');
