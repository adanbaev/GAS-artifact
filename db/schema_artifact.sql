-- Minimal anonymized MySQL 8 schema for the article-related integrity tests.
-- Run only in a newly created disposable database, never in an operational registry.

-- The full Spring application initializes its supported role names at startup.
-- This minimal table is therefore required for the DB-backed integration-test context,
-- even though the tests do not authenticate an operational user.
CREATE TABLE IF NOT EXISTS roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS owner (
  ID INT UNSIGNED NOT NULL AUTO_INCREMENT,
  IDNUMBER INT UNSIGNED NULL,
  inn VARCHAR(255) NULL,
  addflag CHAR(1) NOT NULL DEFAULT '',
  date_pr0 INT UNSIGNED NOT NULL DEFAULT 0,
  date_pr1 INT UNSIGNED NOT NULL DEFAULT 0,
  date_pr2 INT UNSIGNED NOT NULL DEFAULT 0,
  date_p0 INT UNSIGNED NOT NULL DEFAULT 0,
  date_p1 INT UNSIGNED NOT NULL DEFAULT 0,
  date_p2 INT UNSIGNED NOT NULL DEFAULT 0,
  Name VARCHAR(255) NULL,
  oldIDNUMBER INT UNSIGNED NULL,
  passport VARCHAR(255) NULL,
  dopolnit VARCHAR(250) NULL,
  HeadFlags INT UNSIGNED NOT NULL DEFAULT 0,
  LICNUMnum INT UNSIGNED NOT NULL DEFAULT 0,
  dopolnit_p VARCHAR(127) NULL,
  town VARCHAR(127) NULL,
  street VARCHAR(127) NULL,
  house VARCHAR(63) NULL,
  flat VARCHAR(63) NULL,
  telefon VARCHAR(64) NULL,
  fax VARCHAR(64) NULL,
  date_v0 INT UNSIGNED NOT NULL DEFAULT 0,
  date_v1 INT UNSIGNED NOT NULL DEFAULT 0,
  date_v2 INT UNSIGNED NOT NULL DEFAULT 0,
  date_ok0 INT UNSIGNED NOT NULL DEFAULT 0,
  date_ok1 INT UNSIGNED NOT NULL DEFAULT 0,
  date_ok2 INT UNSIGNED NOT NULL DEFAULT 0,
  state INT UNSIGNED NOT NULL DEFAULT 0,
  type INT UNSIGNED NOT NULL DEFAULT 0,
  area INT UNSIGNED NOT NULL DEFAULT 0,
  date_sch0 INT UNSIGNED NOT NULL DEFAULT 0,
  date_sch1 INT UNSIGNED NOT NULL DEFAULT 0,
  date_sch2 INT UNSIGNED NOT NULL DEFAULT 0,
  schet VARCHAR(64) NOT NULL DEFAULT '',
  TypeOfUsing VARCHAR(64) NULL DEFAULT '',
  HasCertificateFlag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (ID)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site (
  ID INT UNSIGNED NOT NULL AUTO_INCREMENT,
  IDowner INT UNSIGNED NOT NULL DEFAULT 0,
  addflag CHAR(1) NOT NULL DEFAULT '',
  SiteName VARCHAR(63) NULL,
  CALLsign VARCHAR(63) NULL,
  longtitude0 INT UNSIGNED NOT NULL DEFAULT 0,
  longtitude1 INT UNSIGNED NOT NULL DEFAULT 0,
  longtitude2 INT UNSIGNED NOT NULL DEFAULT 0,
  latitude0 INT UNSIGNED NOT NULL DEFAULT 0,
  latitude1 INT UNSIGNED NOT NULL DEFAULT 0,
  latitude2 INT UNSIGNED NOT NULL DEFAULT 0,
  h_umora DOUBLE NOT NULL DEFAULT 0,
  TransType VARCHAR(63) NULL,
  TransNum VARCHAR(63) NULL,
  TransPower DOUBLE NOT NULL DEFAULT 0,
  freqStable DOUBLE NOT NULL DEFAULT 0,
  TransPowerType INT UNSIGNED NOT NULL DEFAULT 0,
  AntName VARCHAR(63) NULL,
  AntType VARCHAR(63) NULL,
  AntKU DOUBLE NOT NULL DEFAULT 0,
  AntKUrecv DOUBLE NOT NULL DEFAULT 0,
  highlight DOUBLE NOT NULL DEFAULT 0,
  polar INT UNSIGNED NOT NULL DEFAULT 0,
  beamwidth DOUBLE NOT NULL DEFAULT 0,
  RecvrType VARCHAR(64) NULL,
  ISZ VARCHAR(64) NULL,
  dolg_orbit DOUBLE NOT NULL DEFAULT 0,
  RECV_highlight DOUBLE NOT NULL DEFAULT 0,
  RECV_AntType VARCHAR(63) NOT NULL DEFAULT '',
  RECV_Sencitivity DOUBLE NOT NULL DEFAULT 0,
  PRIMARY KEY (ID),
  KEY idx_site_owner (IDowner)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS freq (
  ID INT UNSIGNED NOT NULL AUTO_INCREMENT,
  IDsite INT UNSIGNED NOT NULL DEFAULT 0,
  IDowner INT UNSIGNED NOT NULL DEFAULT 0,
  addflag CHAR(1) NOT NULL DEFAULT '',
  nominal DOUBLE NOT NULL DEFAULT 0,
  band DOUBLE NOT NULL DEFAULT 0,
  deviation DOUBLE NOT NULL DEFAULT 0,
  channel INT UNSIGNED NOT NULL DEFAULT 0,
  SNCH DOUBLE NOT NULL DEFAULT 0,
  Info VARCHAR(65) NULL,
  Obozn VARCHAR(65) NULL,
  mob_stan INT UNSIGNED NOT NULL DEFAULT 0,
  type INT UNSIGNED NOT NULL DEFAULT 0,
  inco INT UNSIGNED NOT NULL DEFAULT 0,
  mode INT UNSIGNED NOT NULL DEFAULT 0,
  asimut DOUBLE NOT NULL DEFAULT 0,
  SatRadius DOUBLE NOT NULL DEFAULT 0,
  signature VARCHAR(255) NULL,
  PRIMARY KEY (ID),
  KEY idx_freq_site (IDsite),
  KEY idx_freq_owner (IDowner)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS freq_integrity_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_ms BIGINT NOT NULL,
  actor_username VARCHAR(128) NOT NULL,
  action VARCHAR(16) NOT NULL,
  freq_id BIGINT NULL,
  data_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_event_freq_id (freq_id),
  KEY idx_event_ms (event_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS freq_integrity_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_ms BIGINT NOT NULL,
  actor_username VARCHAR(128) NOT NULL,
  action VARCHAR(16) NOT NULL,
  freq_id BIGINT NULL,
  data_hash CHAR(64) NOT NULL,
  prev_hash CHAR(64) NOT NULL,
  chain_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_freq_id (freq_id),
  KEY idx_log_event_ms (event_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integrity_chain_state (
  id INT NOT NULL,
  last_hash CHAR(64) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integrity_checkpoint (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_no BIGINT NOT NULL,
  start_event_id BIGINT NOT NULL,
  end_event_id BIGINT NOT NULL,
  event_count INT NOT NULL,
  root_hash CHAR(64) NOT NULL,
  prev_checkpoint_hash CHAR(64) NOT NULL,
  checkpoint_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_checkpoint_batch_no (batch_no),
  KEY idx_checkpoint_end_event_id (end_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integrity_checkpoint_state (
  id INT NOT NULL,
  last_checkpoint_hash CHAR(64) NOT NULL,
  next_batch_no BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integrity_incident (
  id BIGINT NOT NULL AUTO_INCREMENT,
  created_at_ms BIGINT NOT NULL,
  detected_by VARCHAR(128) NOT NULL,
  incident_type VARCHAR(32) NOT NULL,
  freq_id BIGINT NOT NULL,
  last_log_id BIGINT NULL,
  expected_hash CHAR(64) NULL,
  actual_hash CHAR(64) NULL,
  status VARCHAR(16) NOT NULL,
  comment TEXT NULL,
  resolved_at_ms BIGINT NULL,
  resolved_by VARCHAR(128) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_inc_open (freq_id, incident_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS security_trbac_settings (
  id BIGINT NOT NULL,
  enabled BIT(1) NULL,
  offhours_role VARCHAR(32) NULL,
  timezone VARCHAR(64) NULL,
  updated_at_ms BIGINT NULL,
  updated_by VARCHAR(64) NULL,
  work_end VARCHAR(5) NULL,
  work_start VARCHAR(5) NULL,
  PRIMARY KEY (id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

INSERT INTO integrity_chain_state (id, last_hash)
VALUES (1, 'GENESIS')
ON DUPLICATE KEY UPDATE last_hash = VALUES(last_hash);

INSERT INTO integrity_checkpoint_state (id, last_checkpoint_hash, next_batch_no)
VALUES (1, 'GENESIS', 1)
ON DUPLICATE KEY UPDATE last_checkpoint_hash = VALUES(last_checkpoint_hash), next_batch_no = VALUES(next_batch_no);

INSERT INTO security_trbac_settings
  (id, enabled, offhours_role, timezone, updated_at_ms, updated_by, work_end, work_start)
VALUES
  (1, b'0', 'VIEWER', 'Asia/Bishkek', 0, 'artifact', '18:00', '09:00')
ON DUPLICATE KEY UPDATE id = VALUES(id);
