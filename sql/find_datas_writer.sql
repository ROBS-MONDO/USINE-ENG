/* SOLA LETTURA - chi scrive le righe di Datas_B1 / Datas_B2? (eseguire in SSMS su DATA_BAMBURY, un blocco alla volta) */
USE DATA_BAMBURY;
GO

/* 1) Istruzioni che nominano le tabelle, dalla memoria di SQL Server (piano di esecuzione in cache):
      mostra l'INSERT usato da chi scrive, con le colonne, il numero di esecuzioni e l'ultima volta.
      (Se e' vuoto: il server e' stato riavviato di recente o l'istruzione non e' in cache.) */
SELECT TOP (20) qs.last_execution_time, qs.execution_count, LEFT(st.text, 1500) AS istruzione
  FROM sys.dm_exec_query_stats qs
 CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
 WHERE (st.text LIKE N'%Datas[_]B1%' OR st.text LIKE N'%Datas[_]B2%')
   AND st.text NOT LIKE N'%dm_exec_query_stats%'
 ORDER BY qs.last_execution_time DESC;
GO

/* 2) Tutte le connessioni utente (anche in attesa), senza filtro sul database */
SELECT s.session_id, s.host_name, s.program_name, s.login_name, DB_NAME(s.database_id) AS db,
       s.status, s.last_request_start_time
  FROM sys.dm_exec_sessions s
 WHERE s.is_user_process = 1
 ORDER BY s.last_request_start_time DESC;
GO

/* 3) Contenuto delle viste e della procedura trovate (cosa leggono e chi le usa) */
EXEC sp_helptext N'dbo.srvbambury1';
EXEC sp_helptext N'dbo.srvbambury2';
EXEC sp_helptext N'dbo.SendEmailIfYourTableHasntBeenUpdated';
GO

/* 4) Permessi e struttura: colonne con tipo e lunghezza di Datas_B1 (serve per scrivere i dati dei ticket) */
SELECT ORDINAL_POSITION, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'dbo' AND TABLE_NAME = N'Datas_B1'
 ORDER BY ORDINAL_POSITION;
GO
