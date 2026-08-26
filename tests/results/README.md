# Verified test results

The included Maven Surefire reports were generated on 2026-08-26 in the matching
full private project.  Unit tests ran without a database.  DB integration tests
ran only against the disposable `gas_artifact_test` schema populated with the
public synthetic fixture.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| TrbacPerRequestFilterTest | 4 | 0 | 0 | 0 |
| IntegrityServiceTest | 4 | 0 | 0 | 0 |
| SecurityUtilTest | 2 | 0 | 0 | 0 |
| CheckpointVerificationIT | 4 | 0 | 0 | 0 |
| IntegrityCheckpointAuditIT | 2 | 0 | 0 | 0 |
| TamperingDetectionIT | 1 | 0 | 0 | 0 |
| **Total** | **17** | **0** | **0** | **0** |

## Provenance and privacy sanitization

The user-supplied ZIP containing the original successful reports had SHA-256:

`dff7c5e5a9577fbe9c61972230a4e3638ce5f03547f7ec48d4e352e9d7adf2e6`

Standard Surefire XML records a `<properties>` element containing extensive
machine-local metadata.  In this run it included a Windows account name,
absolute project/home/temp paths, and dependency classpaths.  The public XML
copies remove only that complete `<properties>` element.  The `testsuite`
attributes, every `testcase` name/class/time, and the result counters are
unchanged.  The six concise TXT reports are included unchanged.  No datasource
password or generated integrity secret was present in the submitted reports.
