-- Produzioni di Banbury 1 (pagina Production), database [mondo] su srv-bambury. Da eseguire a mano da chi amministra SQL Server.
-- Equivale alla tabella D_PRODUCTION di WinCC: una produzione = nome + ricetta + quantita' di miscele.
USE [mondo];
GO

CREATE TABLE dbo.B1Productions (
    Id          INT           NOT NULL CONSTRAINT PK_B1Productions PRIMARY KEY,   -- stesso ID di D_PRODUCTION (WinCC)
    Nome        NVARCHAR(50)  NOT NULL,
    Descrizione NVARCHAR(50)  NOT NULL CONSTRAINT DF_B1Productions_Descr DEFAULT N'',
    NumRicetta  INT           NOT NULL CONSTRAINT DF_B1Productions_Num DEFAULT 0, -- = B1Recipes.NumRicetta (ID ricetta WinCC)
    Quantita    INT           NOT NULL CONSTRAINT DF_B1Productions_Qta DEFAULT 0, -- NUM_DATA1: miscele da fare
    Priorita    INT           NOT NULL CONSTRAINT DF_B1Productions_Prio DEFAULT 1,
    Stato       INT           NOT NULL CONSTRAINT DF_B1Productions_Stato DEFAULT 1, -- 1 nuova, 3 in uso, 4 terminata/cancellata
    Creazione   DATETIME2(0)  NOT NULL,
    Modifica    DATETIME2(0)  NOT NULL,
    DataCliente NVARCHAR(15)  NOT NULL CONSTRAINT DF_B1Productions_Cli DEFAULT N''
);
CREATE INDEX IX_B1Productions_Stato ON dbo.B1Productions (Stato, Priorita, Nome);
GO

-- Permessi: se il programma usa l'utente di SVC.DBBamburyReader / un gruppo gia' abilitato alle tabelle ricette,
-- dare gli stessi permessi anche a questa tabella, ad es.:
-- GRANT SELECT, INSERT, UPDATE ON OBJECT::dbo.B1Productions TO [<utente o gruppo>];
-- GO
