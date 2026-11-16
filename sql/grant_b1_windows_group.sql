-- Accesso dei PC di B1 con l'AUTENTICAZIONE WINDOWS (nessuna password nei file ne' nelle variabili d'ambiente).
-- Da eseguire a mano da chi amministra SQL Server su srv-bambury. Sostituisci MONDO\NOME_GRUPPO con un gruppo di dominio
-- che contiene gli utenti (o gli account di servizio) che usano il programma B1; si crea una volta sola.
-- Il programma si collega con "auth = windows" nei file *.properties; serve la libreria nativa mssql-jdbc_auth (dll) sul PC.

-- 1) Il gruppo puo' entrare nel server
USE [master];
CREATE LOGIN [MONDO\NOME_GRUPPO] FROM WINDOWS;
GO

-- 2) Ricette B1: legge e scrive solo le due tabelle (database mondo)
USE [mondo];
CREATE USER [MONDO\NOME_GRUPPO] FOR LOGIN [MONDO\NOME_GRUPPO];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1Recipes TO [MONDO\NOME_GRUPPO];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.B1RecipeValues TO [MONDO\NOME_GRUPPO];
GRANT SELECT, INSERT ON OBJECT::dbo.B1RecipePLC TO [MONDO\NOME_GRUPPO];   -- registro degli invii al PLC
GO

-- 3) Storico del trend B1: solo lettura della tabella Datas_B1 (database DATA_BAMBURY)
USE [DATA_BAMBURY];
CREATE USER [MONDO\NOME_GRUPPO] FOR LOGIN [MONDO\NOME_GRUPPO];
GRANT SELECT ON OBJECT::dbo.Datas_B1 TO [MONDO\NOME_GRUPPO];
GO

-- (facoltativo) dati del ticket: abilita solo quando sapremo tabella e colonne
-- GRANT INSERT ON OBJECT::dbo.<TABELLA> TO [MONDO\NOME_GRUPPO];
