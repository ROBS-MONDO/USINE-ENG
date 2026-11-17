-- Elenco di TUTTI i tag del progetto WinCC (nome e indirizzo PLC), SOLA LETTURA.
-- Serve a trovare gli indirizzi di: modo Auto/Manuale, ciclo auto, produzione auto, ricetta valida, silos aperti, ecc.
-- Stesso database degli altri script. Esegui con Results to Grid (Ctrl+D), poi tasto destro sui risultati -> Save Results As... (CSV)
-- e mandami il file. Non scrive nulla.
USE [CC_Bambury1_24_08_28_17_04_30];
SET NOCOUNT ON;
GO

DECLARE @t sysname, @sql nvarchar(max);
SELECT TOP (1) @t = t.name
FROM sys.tables t
WHERE EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'VARIABLEID')
  AND EXISTS (SELECT 1 FROM sys.columns c WHERE c.object_id = t.object_id AND c.name = 'ADDRESSPARAMETER');
IF @t IS NULL
    PRINT 'Tabella dei tag non trovata';
ELSE
BEGIN
    SET @sql = N'SELECT * FROM dbo.' + QUOTENAME(@t) + N' ORDER BY VARIABLEID';
    EXEC (@sql);
END
GO
