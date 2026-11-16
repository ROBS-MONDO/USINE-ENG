-- Guarda (SOLA LETTURA) le tabelle ricette trovate nel database [mondo] su srv-bambury:
-- colonne, numero di righe e poche righe di esempio. Serve a capire quali contengono le ricette di Banbury 1.
-- Eseguire in SSMS su srv-bambury (basta una query alla volta se il risultato e' lungo).
USE [mondo];
SET NOCOUNT ON;

-- 1) Colonne delle tabelle candidate
SELECT t.name AS tabella, c.column_id AS n, c.name AS colonna, ty.name AS tipo, c.max_length
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN ('Recipes', 'RecipesSteps', 'GOV_RECIPES', 'GOV_RECIPE_COMPONENTS', 'GOV_RECIPE_LEGACY_ARCHIVE',
                 'GOV_SYNC_TB_RECIPE', 'GOV_SYNC_TB_RECIPE_ROW', 'RecipePLC')
ORDER BY t.name, c.column_id;

-- 2) Numero di righe
SELECT t.name AS tabella, SUM(p.rows) AS righe
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
WHERE t.name IN ('Recipes', 'RecipesSteps', 'GOV_RECIPES', 'GOV_RECIPE_COMPONENTS', 'GOV_RECIPE_LEGACY_ARCHIVE',
                 'GOV_SYNC_TB_RECIPE', 'GOV_SYNC_TB_RECIPE_ROW', 'RecipePLC')
GROUP BY t.name
ORDER BY t.name;

-- 3) Poche righe di esempio (le piu' recenti non sono garantite: solo per vedere i dati)
SELECT TOP (5) * FROM dbo.Recipes;
SELECT TOP (5) * FROM dbo.RecipesSteps;
SELECT TOP (5) * FROM dbo.GOV_RECIPES;
SELECT TOP (5) * FROM dbo.GOV_RECIPE_LEGACY_ARCHIVE;
