-- Importare da phpMyAdmin (scheda "Importa") nel database del sito.
CREATE TABLE IF NOT EXISTS tickets (
  id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
  code              VARCHAR(32)  NULL,
  stripe_session_id VARCHAR(255) NOT NULL,
  payment_intent    VARCHAR(255) NULL,
  seq               SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  holder_name       VARCHAR(255) NOT NULL,
  email             VARCHAR(255) NOT NULL,
  status            ENUM('valid','cancelled') NOT NULL DEFAULT 'valid',
  created_at        DATETIME NOT NULL,
  emailed_at        DATETIME NULL,
  checked_in_at     DATETIME NULL,
  checked_in_by     VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_code (code),
  UNIQUE KEY uq_session_seq (stripe_session_id, seq),
  KEY idx_email (email),
  KEY idx_pi (payment_intent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
