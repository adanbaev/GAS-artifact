# Verified test results

The included Maven Surefire reports were generated on 2026-08-30 in the matching
full private project.  Unit tests ran without a database.  DB integration tests
ran only against the disposable `gas_artifact_test` schema populated with the
public synthetic fixture.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| TrbacPerRequestFilterTest | 4 | 0 | 0 | 0 |
| IntegrityServiceTest | 4 | 0 | 0 | 0 |
| SecurityUtilTest | 4 | 0 | 0 | 0 |
| CheckpointVerificationIT | 4 | 0 | 0 | 0 |
| IntegrityCheckpointAuditIT | 2 | 0 | 0 | 0 |
| TamperingDetectionIT | 1 | 0 | 0 | 0 |
| **Total** | **19** | **0** | **0** | **0** |

## Provenance and privacy sanitization

The user-supplied ZIP containing the original successful reports had SHA-256:

`ee0603316b29847613346b29ad16589a01a9494a85da0f41caa05b18276b581e`

Standard Surefire XML records a `<properties>` element containing extensive
machine-local metadata.  In this run it included a Windows account name,
absolute project/home/temp paths, and dependency classpaths.  The public XML
copies remove only that complete `<properties>` element.  The `testsuite`
attributes, every `testcase` name/class/time, and the result counters are
unchanged.  The six concise TXT reports are included unchanged.  No datasource
password or generated integrity secret was present in the submitted reports.
The Surefire process-checker dump is excluded because it is not a test result
and contains only launcher-process diagnostics.
