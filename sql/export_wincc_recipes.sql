-- Esporta (SOLA LETTURA: solo SELECT) la struttura e le ricette del database ricette del WinCC di B1 (Q131014).
-- Esegui su una COPIA del file Q131014.mdf collegata (attach) a un SQL Server, per esempio dal prompt dei comandi:
--   sqlcmd -S .\WINCC -E -Q "CREATE DATABASE Q131014_copia ON (FILENAME='C:\Users\Public\Q131014.mdf') FOR ATTACH_REBUILD_LOG"
--   sqlcmd -S .\WINCC -E -d Q131014_copia -i C:\Users\Public\export_wincc_recipes.sql -o C:\Users\Public\recipes_out.txt -s ";" -W
-- Poi manda il file recipes_out.txt. Non usare il file originale in C:\_projects\dB: lavora sulla copia.
-- Ogni query e' in un lotto a se' (GO): un errore in una non ferma le altre. SELECT * perche' le colonne cambiano tra le versioni.

SET NOCOUNT ON;
GO
PRINT '### TABELLE E NUMERO DI RIGHE';
SELECT t.name AS tabella, SUM(p.rows) AS righe
FROM sys.tables t JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY t.name ORDER BY t.name;
GO
PRINT '### COLONNE DELLE TABELLE RICETTE';
SELECT t.name AS tabella, c.column_id AS n, c.name AS colonna, ty.name AS tipo, c.max_length
FROM sys.tables t JOIN sys.columns c ON c.object_id = t.object_id JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN ('D_RECIPES', 'D_RECIPE_PHASE_PARAMS', 'A_RECIPE_PHASE_PARAMS', 'A_RECIPE_PHASE_PARAMS_TAGS',
                 'A_PHASE_DESCRIPTION', 'A_TEXT_LIST', 'A_SUB_RECIPES', 'D_COMPONENTS_LIST')
ORDER BY t.name, c.column_id;
GO
PRINT '### A_RECIPE_PHASE_PARAMS (definizione dei parametri di fase: nome, tipo, scala, unita, limiti)';
SELECT * FROM dbo.A_RECIPE_PHASE_PARAMS ORDER BY RECIPE_TYPE, ID;
GO
PRINT '### A_RECIPE_PHASE_PARAMS_TAGS (a quale variabile del PLC corrisponde ogni parametro)';
SELECT * FROM dbo.A_RECIPE_PHASE_PARAMS_TAGS ORDER BY RECIPE_TYPE, ID, RECIPE_PHASE_ID;
GO
PRINT '### A_PHASE_DESCRIPTION';
SELECT * FROM dbo.A_PHASE_DESCRIPTION;
GO
PRINT '### A_TEXT_LIST (elenchi di testi, es. nomi delle operazioni)';
SELECT * FROM dbo.A_TEXT_LIST;
GO
PRINT '### D_RECIPES (testata delle ricette)';
SELECT * FROM dbo.D_RECIPES ORDER BY NAME;
GO
PRINT '### D_RECIPE_PHASE_PARAMS: valori delle fasi (tutte le ricette)';
SELECT * FROM dbo.D_RECIPE_PHASE_PARAMS ORDER BY ID_RECIPE, ID, ID_PARAM;
GO
