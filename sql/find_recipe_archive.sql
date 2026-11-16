-- Trova dove si trova l'ARCHIVIO RICETTE di Banbury 1 (SOLA LETTURA: solo viste di sistema, nessuna modifica).
-- Il WinCC di B1 legge le ricette da un database SQL con le stored procedure sp_GetRecipeParams, sp_GetPhaseParams,
-- sp_GetSubRecipeParams e sp_GetTextList (script di WinCC: scriptlib\dbhandling*.bmo, recipe.bmo, productionlist.bmo).
-- Eseguire in SSMS su ogni server che puo' contenerlo: srv-bambury, SRV-BI e il PC WinCC di B1 (SQL Server locale).

SET NOCOUNT ON;

-- 1) In quale database stanno le stored procedure delle ricette
DECLARE @sql nvarchar(max) = N'';
SELECT @sql += N'UNION ALL SELECT ''' + REPLACE(name, '''', '''''') + N''' AS database_name, p.name AS procedura, '
             + N'p.create_date, p.modify_date FROM ' + QUOTENAME(name) + N'.sys.procedures p '
             + N'WHERE p.name IN (''sp_GetRecipeParams'', ''sp_GetPhaseParams'', ''sp_GetSubRecipeParams'', ''sp_GetTextList'') '
FROM sys.databases
WHERE state_desc = 'ONLINE' AND HAS_DBACCESS(name) = 1;
SET @sql = STUFF(@sql, 1, 10, '');   -- toglie il primo "UNION ALL "
EXEC (@sql);

-- 2) Tabelle con "recipe", "ricett", "phase" o "production" nel nome, in tutti i database
SET @sql = N'';
SELECT @sql += N'UNION ALL SELECT ''' + REPLACE(name, '''', '''''') + N''' AS database_name, s.name AS schema_name, t.name AS tabella '
             + N'FROM ' + QUOTENAME(name) + N'.sys.tables t JOIN ' + QUOTENAME(name) + N'.sys.schemas s ON s.schema_id = t.schema_id '
             + N'WHERE t.name LIKE ''%recipe%'' OR t.name LIKE ''%ricett%'' OR t.name LIKE ''%phase%'' OR t.name LIKE ''%production%'' '
FROM sys.databases
WHERE state_desc = 'ONLINE' AND HAS_DBACCESS(name) = 1;
SET @sql = STUFF(@sql, 1, 10, '');
EXEC (@sql);

-- 3) Quando hai trovato il database (es. [NOME_DB]) leggi il testo delle procedure: mostra quali tabelle usano
--    e quindi dove stanno davvero le ricette (togli i commenti ed esegui, una alla volta):
-- USE [NOME_DB];
-- EXEC sp_helptext 'sp_GetRecipeParams';
-- EXEC sp_helptext 'sp_GetPhaseParams';
-- EXEC sp_helptext 'sp_GetSubRecipeParams';
-- EXEC sp_helptext 'sp_GetTextList';
-- Colonne e numero di righe di una tabella delle ricette:
-- SELECT c.name, ty.name AS tipo, c.max_length FROM sys.columns c JOIN sys.types ty ON ty.user_type_id = c.user_type_id
--  WHERE c.object_id = OBJECT_ID('dbo.NOME_TABELLA') ORDER BY c.column_id;
-- SELECT COUNT(*) FROM dbo.NOME_TABELLA;
