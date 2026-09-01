# v1.3.1 release-readiness checklist

## Passed in the release-ready package

- Raw CSV inventory and checksums recorded.
- Expected repeat counts verified for every benchmark cell.
- Zero failed operations verified for every measured run.
- Evidence-row counts verified against STANDARD, FINGERPRINT, CHECKPOINT,
  STRICT, and HMAC_COMPUTE semantics.
- Tables 1-2 restored as public machine-readable files.
- Tables 3-6 and K-sensitivity recomputed from raw runs.
- Figures 1-6 and the final graphical abstract included in PDF/PNG form; the
  editable Draw.io sources for Figures 1 and 2 are also included.
- Full private archive, manuscript, credentials, and private user seed data
  excluded.
- Obsolete `MyUserDetailsServiceTrbacTest.java` excluded.
- Unit tests executed in the matching full project: 12 tests, zero failures,
  errors, or skips.
- DB integration tests executed against the disposable `gas_artifact_test`
  schema and synthetic fixture: 7 tests, zero failures, errors, or skips.
- Privacy-sanitized Surefire TXT/XML reports included under
  `tests/results/surefire-reports/`; suite/testcase results were preserved.
- The minimal public schema includes the `roles` table required by the full
  Spring application context.
- Required signature and integrity-chain secrets fail closed at startup; the
  built-in placeholder and cross-secret fallback have been removed.

## Final publication verification steps

1. Review the public Git diff and GitHub v1.3.1 release preview.
2. Verify that the reviewed ZIP is the asset associated with the GitHub v1.3.1
   release and the version-specific Zenodo record
   <https://doi.org/10.5281/zenodo.22164388>.
3. Confirm that the GitHub release asset and Zenodo deposit match the reviewed
   ZIP checksum.
