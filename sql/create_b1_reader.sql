-- Utente SQL di SOLA LETTURA per lo storico del trend B1 (da eseguire a mano da chi amministra SQL Server).
-- Permette solo SELECT su dbo.Datas_B1: niente INSERT/UPDATE/DELETE, nessun'altra tabella.
-- Sostituire <PASSWORD> con una password robusta e NON salvarla in file del progetto:
-- va nella variabile d'ambiente B1_SQL_PASSWORD del PC che esegue il programma B1.
-- Lo script non viene eseguito dal programma.

USE [master];
CREATE LOGIN [b1_trend_reader] WITH PASSWORD = N'<PASSWORD>', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;

USE [DATA_BAMBURY];
CREATE USER [b1_trend_reader] FOR LOGIN [b1_trend_reader];
GRANT SELECT ON OBJECT::dbo.Datas_B1 TO [b1_trend_reader];

-- Verifica (deve restituire solo SELECT):
-- SELECT permission_name, state_desc FROM sys.database_permissions
--  WHERE grantee_principal_id = USER_ID('b1_trend_reader');
