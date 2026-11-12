/* Chi scrive in Datas_B1 / Datas_B2?  (eseguire in SSMS, DATA_BAMBURY)

   A) SOLA LETTURA - solo le istruzioni di scrittura rimaste nella cache di SQL Server: */
USE DATA_BAMBURY;
GO
SELECT TOP (10) qs.last_execution_time, qs.execution_count, LEFT(st.text, 2000) AS istruzione
  FROM sys.dm_exec_query_stats qs
 CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
 WHERE (st.text LIKE N'%INSERT%Datas[_]B%' OR st.text LIKE N'%UPDATE%Datas[_]B%' OR st.text LIKE N'%MERGE%Datas[_]B%')
   AND st.text NOT LIKE N'%dm_exec_query_stats%'
 ORDER BY qs.last_execution_time DESC;
GO

/* B) OPZIONALE, temporaneo (~20 secondi): se (A) e' vuoto, la scrittura avviene ogni 2 secondi, quindi
      una breve cattura la intercetta. Crea una sessione di Extended Events IN MEMORIA, la legge e
      la ELIMINA; non scrive nulla nelle tabelle. Se qualcosa va storto, eliminarla a mano con
      l'ultima istruzione (DROP EVENT SESSION). NON provato su questo server.

   Eseguire i quattro passi in ordine. */

-- 1) crea e avvia
CREATE EVENT SESSION [b1_find_writer] ON SERVER
ADD EVENT sqlserver.sql_batch_completed (
    ACTION (sqlserver.client_app_name, sqlserver.client_hostname, sqlserver.username)
    WHERE (sqlserver.database_name = N'DATA_BAMBURY'
           AND sqlserver.like_i_sql_unicode_string([batch_text], N'%Datas_B%'))),
ADD EVENT sqlserver.rpc_completed (
    ACTION (sqlserver.client_app_name, sqlserver.client_hostname, sqlserver.username)
    WHERE (sqlserver.database_name = N'DATA_BAMBURY'
           AND sqlserver.like_i_sql_unicode_string([statement], N'%Datas_B%')))
ADD TARGET package0.ring_buffer (SET max_memory = 4096)
WITH (MAX_DISPATCH_LATENCY = 1 SECONDS);
GO
ALTER EVENT SESSION [b1_find_writer] ON SERVER STATE = START;
GO
-- 2) attendere ~20 secondi
WAITFOR DELAY '00:00:20';
GO
-- 3) leggere cosa e' stato catturato (programma, computer, utente, istruzione)
SELECT n.x.value('(@timestamp)[1]', 'datetime2')                                          AS quando,
       n.x.value('(action[@name="client_app_name"]/value)[1]', 'nvarchar(200)')           AS programma,
       n.x.value('(action[@name="client_hostname"]/value)[1]', 'nvarchar(200)')           AS computer,
       n.x.value('(action[@name="username"]/value)[1]', 'nvarchar(200)')                  AS utente,
       LEFT(COALESCE(n.x.value('(data[@name="batch_text"]/value)[1]', 'nvarchar(max)'),
                     n.x.value('(data[@name="statement"]/value)[1]', 'nvarchar(max)')), 1500) AS istruzione
  FROM (SELECT CAST(t.target_data AS xml) AS td
          FROM sys.dm_xe_session_targets t
          JOIN sys.dm_xe_sessions s ON s.address = t.event_session_address
         WHERE s.name = N'b1_find_writer' AND t.target_name = N'ring_buffer') a
 CROSS APPLY a.td.nodes('RingBufferTarget/event') n(x)
 ORDER BY quando;
GO
-- 4) fermare ed ELIMINARE la sessione (non lasciarla attiva)
ALTER EVENT SESSION [b1_find_writer] ON SERVER STATE = STOP;
DROP EVENT SESSION [b1_find_writer] ON SERVER;
GO
