/* SOLA LETTURA - dettagli dei job esistenti DELETE-DATA e Delete log (eseguire in SSMS, un blocco alla volta) */
USE msdb;
GO

/* 1) Testo COMPLETO dei passi (in SSMS: Results to Text, oppure copiare la cella) */
SELECT j.name AS job, s.step_id, s.step_name, s.database_name, s.command
  FROM msdb.dbo.sysjobs j
  JOIN msdb.dbo.sysjobsteps s ON s.job_id = j.job_id
 WHERE j.name IN (N'DELETE-DATA', N'Delete log')
 ORDER BY j.name, s.step_id;
GO

/* 2) Pianificazione (frequenza, orario) e prossima esecuzione */
SELECT j.name AS job, sc.name AS pianificazione, sc.enabled, sc.freq_type, sc.freq_interval,
       sc.freq_subday_type, sc.freq_subday_interval, sc.active_start_time,
       msdb.dbo.agent_datetime(js.next_run_date, js.next_run_time) AS prossima_esecuzione
  FROM msdb.dbo.sysjobs j
  JOIN msdb.dbo.sysjobschedules js ON js.job_id = j.job_id
  JOIN msdb.dbo.sysschedules sc ON sc.schedule_id = js.schedule_id
 WHERE j.name IN (N'DELETE-DATA', N'Delete log');
GO

/* 3) Ultime esecuzioni: quando, quanto ci mette, esito (run_status: 1 = ok, 0 = fallito) */
SELECT TOP (20) j.name AS job, h.step_id, msdb.dbo.agent_datetime(h.run_date, h.run_time) AS avvio,
       h.run_duration AS durata_hhmmss, h.run_status, LEFT(h.message, 300) AS messaggio
  FROM msdb.dbo.sysjobhistory h
  JOIN msdb.dbo.sysjobs j ON j.job_id = h.job_id
 WHERE j.name IN (N'DELETE-DATA', N'Delete log')
 ORDER BY h.instance_id DESC;
GO

/* 4) Dimensione del file di log e modello di recupero di DATA_BAMBURY */
SELECT name, recovery_model_desc FROM sys.databases WHERE name = N'DATA_BAMBURY';
SELECT name, type_desc, size * 8 / 1024 AS size_mb, growth FROM DATA_BAMBURY.sys.database_files;
GO
