-- Guarda (SOLA LETTURA) il database di Alarm Logging di WinCC per ricavare l'elenco dei messaggi di allarme di B1
-- (numero, testo, classe, tag e bit che li attivano). Da eseguire in SSMS sul PC WinCC, istanza ...\WINCC.
-- Il database ha un nome con date, es. [PC-Q131014_Scada_ALG_202505282200_202506041243]: cambia il nome qui sotto
-- con quello del database di Alarm Logging che contiene le tabelle AlgCSData*.
USE [PC-Q131014_Scada_ALG_202505282200_202506041243];
SET NOCOUNT ON;

-- 1) Tutte le tabelle con il numero di righe
SELECT t.name AS tabella, SUM(p.rows) AS righe
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY t.name
ORDER BY t.name;

-- 2) Colonne delle tabelle di configurazione (AlgCS*) e dei testi (AlgRtTexts*)
SELECT t.name AS tabella, c.column_id AS n, c.name AS colonna, ty.name AS tipo, c.max_length
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name LIKE 'AlgCS%' OR t.name LIKE 'AlgRtTexts%FRA' OR t.name LIKE 'AlgRtTexts%ITA'
ORDER BY t.name, c.column_id;

-- 3) Poche righe di esempio in francese e italiano
SELECT TOP (20) * FROM dbo.AlgCSDataFRA;
SELECT TOP (20) * FROM dbo.AlgCSDataITA;
SELECT TOP (20) * FROM dbo.AlgRtTextsFRA;
