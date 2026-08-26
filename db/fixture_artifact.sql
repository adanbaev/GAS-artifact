-- One wholly synthetic record for a disposable artifact database.
-- No row or value was copied from the operational registry.

-- Non-personal authorization labels required by RoleDataInitializer at startup.
INSERT INTO roles (name) VALUES
  ('ADMIN'),
  ('OPERATOR'),
  ('PRINTER'),
  ('VIEWER'),
  ('AUDITOR')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO owner
  (ID, Name, state, type, area, HeadFlags, LICNUMnum, HasCertificateFlag, schet)
VALUES
  (1, 'Synthetic Registry Holder', 0, 0, 0, 9, 1001, '0', 'ARTIFACT-001')
ON DUPLICATE KEY UPDATE Name = VALUES(Name);

INSERT INTO site
  (ID, IDowner, SiteName, CALLsign, latitude0, latitude1, latitude2,
   longtitude0, longtitude1, longtitude2, h_umora, TransType, TransNum,
   AntName, AntType, polar, RECV_AntType)
VALUES
  (1, 1, 'Synthetic Test Site', 'TEST-01', 42, 0, 0,
   74, 0, 0, 1000, 'TEST', 'TX-001', 'TEST-ANT', 'OMNI', 0, 'TEST-RX')
ON DUPLICATE KEY UPDATE SiteName = VALUES(SiteName);

INSERT INTO freq
  (ID, IDsite, IDowner, nominal, band, deviation, channel, SNCH, Obozn,
   mob_stan, type, inco, mode, SatRadius, signature)
VALUES
  (1, 1, 1, 100000.0, 25.0, 0.0, 1, 0.0, 'SYNTHETIC',
   0, 2, 0, 0, 0.0, NULL)
ON DUPLICATE KEY UPDATE nominal = VALUES(nominal);
