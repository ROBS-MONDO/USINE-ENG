-- ============================================================================
-- Tracciabilita' materie prime - tabelle su SQL Server "Mondo" (SRV-BAMBURY)
-- ============================================================================
-- Script idempotente: si puo' rieseguire senza errori (crea solo cio' che manca).
-- Da eseguire con un utente che abbia CREATE TABLE / CREATE SEQUENCE (es. da SSMS).
-- Non e' stato provato su un'istanza SQL Server: eseguirlo prima su una copia/test.
-- Le quantita' sono FLOAT come in DistintaBaseManager (dbo.DistintaBaseComponenti).
-- ============================================================================

USE Mondo;
GO

-- ---------------------------------------------------------------------------
-- Progressivi (lotto interno e ID prelievo). Il prefisso con l'anno (L26-...)
-- lo compone l'applicazione; il numero non riparte da 1 a inizio anno.
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.SeqLottoInterno', 'SO') IS NULL
    CREATE SEQUENCE dbo.SeqLottoInterno AS INT START WITH 1 INCREMENT BY 1;
GO
IF OBJECT_ID('dbo.SeqPrelievo', 'SO') IS NULL
    CREATE SEQUENCE dbo.SeqPrelievo AS INT START WITH 1 INCREMENT BY 1;
GO

-- ---------------------------------------------------------------------------
-- Lotti di materia prima (uno per sacco/pallet in ingresso)
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.MpLotto', 'U') IS NULL
CREATE TABLE dbo.MpLotto (
    LottoInterno    NVARCHAR(20)  NOT NULL CONSTRAINT PK_MpLotto PRIMARY KEY,   -- es. L26-000123
    CodiceMP        NVARCHAR(50)  NOT NULL,
    Descrizione     NVARCHAR(255) NOT NULL CONSTRAINT DF_MpLotto_Descr   DEFAULT '',
    Fornitore       NVARCHAR(100) NOT NULL CONSTRAINT DF_MpLotto_Forn    DEFAULT '',
    BollaIngresso   NVARCHAR(50)  NOT NULL CONSTRAINT DF_MpLotto_Bolla   DEFAULT '',   -- da Oracle POKER
    LottoFornitore  NVARCHAR(100) NOT NULL CONSTRAINT DF_MpLotto_LottoF  DEFAULT '',
    Scadenza        DATE          NULL,
    QtaIniziale     FLOAT         NOT NULL,
    UM              NVARCHAR(10)  NOT NULL CONSTRAINT DF_MpLotto_UM      DEFAULT 'KG',
    Stato           NVARCHAR(20)  NOT NULL CONSTRAINT DF_MpLotto_Stato   DEFAULT 'DA VALIDARE',
    ValidatoDa      NVARCHAR(100) NOT NULL CONSTRAINT DF_MpLotto_ValDa   DEFAULT '',
    DataValidazione DATETIME2     NULL,
    DataIngresso    DATETIME2     NOT NULL CONSTRAINT DF_MpLotto_DataIng DEFAULT SYSDATETIME(),
    CONSTRAINT CK_MpLotto_Stato CHECK (Stato IN ('DA VALIDARE', 'OK', 'BLOCCATO', 'ESAURITO')),
    CONSTRAINT CK_MpLotto_Qta   CHECK (QtaIniziale > 0)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MpLotto_MP_Stato_Scad' AND object_id = OBJECT_ID('dbo.MpLotto'))
    CREATE INDEX IX_MpLotto_MP_Stato_Scad ON dbo.MpLotto (CodiceMP, Stato, Scadenza);   -- ricerca FEFO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MpLotto_LottoFornitore' AND object_id = OBJECT_ID('dbo.MpLotto'))
    CREATE INDEX IX_MpLotto_LottoFornitore ON dbo.MpLotto (LottoFornitore);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MpLotto_Bolla' AND object_id = OBJECT_ID('dbo.MpLotto'))
    CREATE INDEX IX_MpLotto_Bolla ON dbo.MpLotto (BollaIngresso);
GO

-- ---------------------------------------------------------------------------
-- Movimenti di magazzino: registro in sola aggiunta.
-- Residuo del lotto = SUM(Qta) per LottoInterno (INGRESSO positivo, PRELIEVO negativo).
-- Qta e' espressa nell'UM del lotto (MpLotto.UM): convertire g/kg prima di scrivere.
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.MpMovimento', 'U') IS NULL
CREATE TABLE dbo.MpMovimento (
    Id           INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_MpMovimento PRIMARY KEY,
    LottoInterno NVARCHAR(20)  NOT NULL CONSTRAINT FK_MpMovimento_Lotto REFERENCES dbo.MpLotto (LottoInterno),
    Tipo         NVARCHAR(20)  NOT NULL,
    Qta          FLOAT         NOT NULL,
    Rif          NVARCHAR(50)  NOT NULL CONSTRAINT DF_MpMov_Rif  DEFAULT '',   -- es. batch o ID prelievo
    Operatore    NVARCHAR(50)  NOT NULL CONSTRAINT DF_MpMov_Op   DEFAULT '',
    DataOra      DATETIME2     NOT NULL CONSTRAINT DF_MpMov_Data DEFAULT SYSDATETIME(),
    CONSTRAINT CK_MpMovimento_Tipo CHECK (Tipo IN ('INGRESSO', 'PRELIEVO', 'RETTIFICA', 'SCARTO'))
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MpMovimento_Lotto' AND object_id = OBJECT_ID('dbo.MpMovimento'))
    CREATE INDEX IX_MpMovimento_Lotto ON dbo.MpMovimento (LottoInterno) INCLUDE (Qta);
GO

-- ---------------------------------------------------------------------------
-- Batch di produzione (N. LOT del foglio, es. 26/0181)
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.Batch', 'U') IS NULL
CREATE TABLE dbo.Batch (
    BatchId        NVARCHAR(30) NOT NULL CONSTRAINT PK_Batch PRIMARY KEY,
    CodiceDistinta NVARCHAR(50) NOT NULL CONSTRAINT DF_Batch_Dist DEFAULT '',   -- dbo.DistintaBase.Codice
    Revisione      NVARCHAR(10) NOT NULL CONSTRAINT DF_Batch_Rev  DEFAULT '',
    NumMescole     INT          NOT NULL CONSTRAINT DF_Batch_Mesc DEFAULT 1,
    Stato          NVARCHAR(20) NOT NULL CONSTRAINT DF_Batch_Stato DEFAULT 'APERTO',
    DataCreazione  DATETIME2    NOT NULL CONSTRAINT DF_Batch_Data  DEFAULT SYSDATETIME(),
    DataChiusura   DATETIME2    NULL,
    CONSTRAINT CK_Batch_Stato CHECK (Stato IN ('APERTO', 'CHIUSO', 'ANNULLATO'))
);
GO

-- ---------------------------------------------------------------------------
-- Prelievi per batch e zona (una riga per pesata/lotto usato)
-- Nessuna FK verso dbo.Batch: oggi l'app registra i prelievi anche senza aver creato il batch.
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.BatchPrelievo', 'U') IS NULL
CREATE TABLE dbo.BatchPrelievo (
    Id           INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_BatchPrelievo PRIMARY KEY,
    IdPrelievo   NVARCHAR(20)  NOT NULL CONSTRAINT UQ_BatchPrelievo_Id UNIQUE,   -- es. P26-0000001 (QR etichetta dose)
    BatchId      NVARCHAR(30)  NOT NULL,
    Zona         NVARCHAR(30)  NOT NULL,
    CodiceMP     NVARCHAR(50)  NOT NULL,
    LottoInterno NVARCHAR(20)  NOT NULL CONSTRAINT FK_BatchPrelievo_Lotto REFERENCES dbo.MpLotto (LottoInterno),
    QtaPrevista  FLOAT         NOT NULL CONSTRAINT DF_BP_Prev DEFAULT 0,
    PesoMin      FLOAT         NOT NULL CONSTRAINT DF_BP_Min  DEFAULT 0,
    PesoMax      FLOAT         NOT NULL CONSTRAINT DF_BP_Max  DEFAULT 0,
    QtaPesata    FLOAT         NOT NULL,
    UM           NVARCHAR(10)  NOT NULL CONSTRAINT DF_BP_UM   DEFAULT 'G',     -- UM di previsto/pesato
    Esito        NVARCHAR(60)  NOT NULL CONSTRAINT DF_BP_Esito DEFAULT 'OK',   -- OK / FUORI TOL. / ... + DEROGA FEFO
    PesoManuale  BIT           NOT NULL CONSTRAINT DF_BP_Man  DEFAULT 0,       -- 1 = digitato, non letto dalla bilancia
    Operatore    NVARCHAR(50)  NOT NULL CONSTRAINT DF_BP_Op   DEFAULT '',
    DataOra      DATETIME2     NOT NULL CONSTRAINT DF_BP_Data DEFAULT SYSDATETIME()
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_BatchPrelievo_Batch' AND object_id = OBJECT_ID('dbo.BatchPrelievo'))
    CREATE INDEX IX_BatchPrelievo_Batch ON dbo.BatchPrelievo (BatchId, Zona);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_BatchPrelievo_Lotto' AND object_id = OBJECT_ID('dbo.BatchPrelievo'))
    CREATE INDEX IX_BatchPrelievo_Lotto ON dbo.BatchPrelievo (LottoInterno);   -- tracciabilita' in avanti
GO

-- ---------------------------------------------------------------------------
-- Lotti caricati nei silos (i consumi vengono dal DB GOVONI-Silos)
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.SiloCarichi', 'U') IS NULL
CREATE TABLE dbo.SiloCarichi (
    Id           INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_SiloCarichi PRIMARY KEY,
    Silo         NVARCHAR(30)  NOT NULL,
    LottoInterno NVARCHAR(20)  NOT NULL CONSTRAINT FK_SiloCarichi_Lotto REFERENCES dbo.MpLotto (LottoInterno),
    Qta          FLOAT         NULL,
    DataCarico   DATETIME2     NOT NULL CONSTRAINT DF_SiloC_DataC DEFAULT SYSDATETIME(),
    DataScarico  DATETIME2     NULL
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SiloCarichi_Silo' AND object_id = OBJECT_ID('dbo.SiloCarichi'))
    CREATE INDEX IX_SiloCarichi_Silo ON dbo.SiloCarichi (Silo, DataCarico);
GO

-- ---------------------------------------------------------------------------
-- Sincronizzazione con Quasar: numero di bolla MOMP da cui importare, per anno (chiave MOMP_AAAA).
-- L'applicazione la crea da sola se ha il permesso; in alternativa eseguire questo script.
-- ---------------------------------------------------------------------------
IF OBJECT_ID('dbo.QuasarSync', 'U') IS NULL
CREATE TABLE dbo.QuasarSync (
    Chiave   NVARCHAR(30) NOT NULL CONSTRAINT PK_QuasarSync PRIMARY KEY,
    Valore   INT          NOT NULL,
    DataAgg  DATETIME2    NOT NULL CONSTRAINT DF_QuasarSync_Data DEFAULT SYSDATETIME()
);
GO

-- ---------------------------------------------------------------------------
-- Indice per trovare subito il silo di un lotto (usato dall'elenco lotti).
-- ---------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_SiloCarichi_Lotto' AND object_id = OBJECT_ID('dbo.SiloCarichi'))
    CREATE INDEX IX_SiloCarichi_Lotto ON dbo.SiloCarichi (LottoInterno, DataCarico);
GO
