-- Produzioni di B1 nel database delle ricette WinCC (copia "Q131014" o quella del PC di produzione), SOLA LETTURA.
-- Serve per la pagina Production: elenco produzioni (nome, descrizione, data, quantita' miscele, ricetta) e la procedura di avvio.
-- Esegui con Results to Grid (Ctrl+D), poi tasto destro sui risultati -> Save Results As... (CSV) per ogni risultato, e mandameli.
-- Cambia il nome del database qui sotto se serve. Non scrive nulla.
USE [Q131014_copia];
SET NOCOUNT ON;
GO

-- 1) Colonne delle tabelle delle produzioni
SELECT t.name AS tabella, c.column_id AS n, c.name AS colonna, ty.name AS tipo, c.max_length
FROM sys.tables t JOIN sys.columns c ON c.object_id = t.object_id JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name LIKE 'D_PRODU%' OR t.name LIKE 'D_BARCODE%'
ORDER BY t.name, c.column_id;
GO

-- 2) Le produzioni (tutte le colonne)
IF OBJECT_ID('dbo.D_PRODUCTION') IS NOT NULL SELECT * FROM dbo.D_PRODUCTION;
GO

-- 3) Altre tabelle di produzione, solo se esistono (anche se vuote, per vedere la struttura)
IF OBJECT_ID('dbo.D_PRODUCTION_BATCHES') IS NOT NULL SELECT TOP (50) * FROM dbo.D_PRODUCTION_BATCHES;
GO
IF OBJECT_ID('dbo.D_PRODUZIONE_MACCHINA') IS NOT NULL SELECT TOP (50) * FROM dbo.D_PRODUZIONE_MACCHINA;
GO

-- 4) Procedure memorizzate legate alle produzioni (il codice mostra cosa fa "avvia produzione")
SELECT o.name AS procedura, OBJECT_DEFINITION(o.object_id) AS codice
FROM sys.objects o
WHERE o.type IN ('P','FN','TF','V') AND (o.name LIKE '%PRODU%' OR o.name LIKE '%START%' OR o.name LIKE '%BATCH%');
GO
