-- Elenco allarmi di B1 dal database di progetto WinCC (Bambury1.mdf = database CC_Bambury1_...), SOLA LETTURA.
-- Da eseguire in SSMS sul PC dove hai collegato Bambury1.mdf. Sostituisci il nome qui sotto con quello del database collegato
-- (come per le ricette: copia "Bambury1_copia", ATTACH_REBUILD_LOG se manca il .ldf). Non scrive nulla.
-- Risultati: usa "Results to Text" (Ctrl+T) oppure salva i risultati in un file e mandameli.
USE [Bambury1_copia];
SET NOCOUNT ON;
GO

-- 1) Tabelle e viste dell'Alarm Logging con numero di righe (dammi questo risultato anche se il resto non va)
SELECT o.type_desc AS tipo, o.name AS oggetto
FROM sys.objects o
WHERE o.type IN ('U','V') AND (o.name LIKE 'AlgCS%' OR o.name LIKE 'MSG%' OR o.name LIKE 'MS%Msg%' OR o.name LIKE 'MESSAGE%' OR o.name LIKE '%Alarm%')
ORDER BY o.type, o.name;
GO

-- 2) Colonne di quegli oggetti
SELECT o.name AS oggetto, c.column_id AS n, c.name AS colonna, ty.name AS tipo, c.max_length
FROM sys.objects o
JOIN sys.columns c ON c.object_id = o.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE o.type IN ('U','V') AND (o.name LIKE 'AlgCS%' OR o.name LIKE 'MSG%' OR o.name LIKE 'MS%Msg%' OR o.name LIKE 'MESSAGE%')
ORDER BY o.name, c.column_id;
GO

-- 3) Messaggi: se la vista esiste (italiano, poi francese, poi inglese) tutte le righe, altrimenti la tabella MESSAGES
IF OBJECT_ID('dbo.AlgCSViewITA') IS NOT NULL SELECT * FROM dbo.AlgCSViewITA;
GO
IF OBJECT_ID('dbo.AlgCSViewFRA') IS NOT NULL SELECT * FROM dbo.AlgCSViewFRA;
GO
IF OBJECT_ID('dbo.AlgCSViewENU') IS NOT NULL SELECT * FROM dbo.AlgCSViewENU;
GO
IF OBJECT_ID('dbo.MESSAGES') IS NOT NULL SELECT * FROM dbo.MESSAGES;
GO
IF OBJECT_ID('dbo.MSMsgs') IS NOT NULL SELECT * FROM dbo.MSMsgs;
GO
