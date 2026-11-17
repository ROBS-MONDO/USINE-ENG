-- Associa gli ID delle variabili di allarme (MSGVAR 209..217 e 636) al nome/indirizzo del PLC. SOLA LETTURA.
-- Esegui nello stesso database di progetto degli altri script: USE [CC_Bambury1_24_08_28_17_04_30];
-- Ctrl+T (Results to Text), poi mandami l'output. Non scrive nulla.
USE [CC_Bambury1_24_08_28_17_04_30];
SET NOCOUNT ON;
GO

-- 1) Tabelle che contengono le colonne della gestione tag (VARIABLEID, VARIABLENAME, ADDRESSPARAMETER)
SELECT t.name AS tabella
FROM sys.tables t
WHERE EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'VARIABLEID')
  AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'ADDRESSPARAMETER');
GO

-- 2) Le variabili usate dai messaggi di allarme: ID, nome, indirizzo
DECLARE @t sysname, @sql nvarchar(max);
SELECT TOP (1) @t = t.name
FROM sys.tables t
WHERE EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'VARIABLEID')
  AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'ADDRESSPARAMETER');
IF @t IS NULL
    PRINT 'Tabella dei tag non trovata';
ELSE
BEGIN
    SET @sql = N'SELECT VARIABLEID, VARIABLENAME, ADDRESSPARAMETER, CONNECTIONNAME FROM dbo.' + QUOTENAME(@t)
             + N' WHERE VARIABLEID IN (209,210,211,212,213,214,215,216,217,636) ORDER BY VARIABLEID';
    EXEC (@sql);
END
GO
