-- Esporta (SOLA LETTURA: solo SELECT) la struttura e le ricette del database ricette del WinCC di B1 (Q131014).
-- Esegui su una COPIA del file Q131014.mdf collegata (attach) a un SQL Server, per esempio dal prompt dei comandi:
--   sqlcmd -S .\WINCC -E -Q "CREATE DATABASE Q131014_copia ON (FILENAME='C:\Users\Public\Q131014.mdf') FOR ATTACH_REBUILD_LOG"
--   sqlcmd -S .\WINCC -E -d Q131014_copia -i export_wincc_recipes.sql -o recipes_out.txt -s ";" -W
-- Poi manda il file recipes_out.txt. Non usare il file originale in C:\_projects\dB: lavora sulla copia.

SET NOCOUNT ON;

PRINT '### A_RECIPE_PHASE_PARAMS (definizione dei parametri di fase: nome, tipo, scala, unita, limiti)';
SELECT * FROM dbo.A_RECIPE_PHASE_PARAMS ORDER BY RECIPE_TYPE, ID;

PRINT '### A_RECIPE_PHASE_PARAMS_TAGS (a quale variabile del PLC corrisponde ogni parametro)';
SELECT * FROM dbo.A_RECIPE_PHASE_PARAMS_TAGS ORDER BY RECIPE_TYPE, ID, RECIPE_PHASE_ID;

PRINT '### A_PHASE_DESCRIPTION';
SELECT * FROM dbo.A_PHASE_DESCRIPTION;

PRINT '### A_TEXT_LIST (elenchi di testi, es. nomi delle operazioni)';
SELECT * FROM dbo.A_TEXT_LIST;

PRINT '### D_RECIPES (testata delle ricette)';
SELECT ID, NAME, DESCRIPTION, DATE_CREATION, DATE_LAST_UPDATE, OPERATOR, FLAG_AVAILABLE, ID_COLOR,
       STR_DATA1, STR_DATA2, STR_DATA3, STR_DATA4, STR_DATA5,
       NUM_DATA1, NUM_DATA2, NUM_DATA3, NUM_DATA4, NUM_DATA5, NUM_DATA6, NUM_DATA7, NUM_DATA8, NUM_DATA9, NUM_DATA10
FROM dbo.D_RECIPES ORDER BY NAME;

PRINT '### D_RECIPE_PHASE_PARAMS: numero di righe';
SELECT COUNT(*) AS righe FROM dbo.D_RECIPE_PHASE_PARAMS;

PRINT '### D_RECIPE_PHASE_PARAMS: valori delle fasi (tutte le ricette)';
SELECT ID_RECIPE, RECIPE_TYPE, ID AS FASE_ID, ID_PARAM, NAME, DESCRIPTION, ID_COMPONENTS_LIST,
       VALUE_PARAM, VALUE_PARAM_STR, VALUE_PARAM_BOOL
FROM dbo.D_RECIPE_PHASE_PARAMS
ORDER BY ID_RECIPE, ID, ID_PARAM;
