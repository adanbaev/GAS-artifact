# Changelog

## v1.3.1

- Synchronizes the machine-readable copies of manuscript Tables 1-2 with the
  final checked minor-revision manuscript.
- Records the normalization-scope boundary for the fixed 23-field subset and
  the request-boundary TRBAC restoration behavior in those tables.
- Adds the K-sensitivity bootstrap-interval comparison to the statistical
  reproducibility note.
- Updates citation and release metadata for the version-specific v1.3.1
  GitHub/Zenodo record.
- Normalizes text-file line endings to LF and adds `.gitattributes` so
  repository checkouts and the release archive use the same checksummed bytes.
- Leaves the benchmark inputs, implementation and test content, analysis logic,
  deterministic resampling namespace, recomputed numerical outputs, and figures
  semantically unchanged from v1.3.

## v1.3

- Aligns benchmark mode names with implemented strategies: STANDARD,
  FINGERPRINT (`HMAC_ONLY`), CHECKPOINT (`HMAC_CHECKPOINT`), STRICT
  (`HMAC_STRICT`), and diagnostic `HMAC_COMPUTE`.
- Replaces the v1.2 raw benchmark datasets with the verified revised runs.
- Adds Tables 1-2 and revised machine-readable Tables 3-6.
- Adds deterministic 200,000-replicate analysis with run/evidence validation.
- Adds revised Figures 3-6 and the final graphical abstract.
- Updates Figure 2 to the final code-faithful version used by the manuscript.
- Adds selected implementation and current test-source extracts.
- Adds anonymized configuration, minimal schema, and synthetic DB fixture.
- Adds verified Maven Surefire evidence for 17 passing unit and DB integration
  tests (zero failures, errors, or skips); machine-local properties are removed
  from the public XML copies for privacy.
- Adds the minimal `roles` table required by the full Spring application context
  used for DB integration testing.
- Excludes obsolete test code, private source archive, operational data,
  credentials, and manuscript metadata.

## v1.2

Historical release restored at:
<https://github.com/adanbaev/GAS-artifact/releases/tag/v1.2>
