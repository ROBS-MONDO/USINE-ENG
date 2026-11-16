-- Solo la tabella registro invii (le altre due esistono gia'). Database [mondo] su srv-bambury.
USE [mondo];
GO
CREATE TABLE dbo.B1RecipePLC (
    Id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_B1RecipePLC PRIMARY KEY,
    DataOra       DATETIME2(0)  NOT NULL,
    RecipeUID     NVARCHAR(36)  NULL,
    Nome          NVARCHAR(32)  NOT NULL,
    NumRicetta    INT           NOT NULL CONSTRAINT DF_B1RecipePLC_Num DEFAULT 0,
    Revisione     BIGINT        NOT NULL CONSTRAINT DF_B1RecipePLC_Rev DEFAULT 0,
    Utente        NVARCHAR(100) NOT NULL CONSTRAINT DF_B1RecipePLC_Utente DEFAULT N'',
    Postazione    NVARCHAR(100) NOT NULL CONSTRAINT DF_B1RecipePLC_Post DEFAULT N'',
    Esito         NVARCHAR(10)  NOT NULL,
    ParoleScritte INT           NOT NULL CONSTRAINT DF_B1RecipePLC_Parole DEFAULT 0,
    FileBackup    NVARCHAR(260) NULL,
    Messaggio     NVARCHAR(400) NULL
);
CREATE INDEX IX_B1RecipePLC_DataOra ON dbo.B1RecipePLC (DataOra DESC);
GO
