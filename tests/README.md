# Test sources and safe execution

## Verified execution included in v1.3

The tests were executed on 2026-08-26 in the matching full private project using
Windows PowerShell, Java 17.0.12, Maven 3.8.6, and MySQL 8.0.45.  DB integration
tests used the disposable `gas_artifact_test` schema created from
`db/schema_artifact.sql` and `db/fixture_artifact.sql`.  The result was 17 tests,
zero failures, zero errors, and zero skips.

Verified reports are included under `tests/results/surefire-reports/`.  See
`tests/results/README.md` for suite totals and the privacy-sanitization note.

## Included tests

Unit tests:

- `TrbacPerRequestFilterTest`
- `IntegrityServiceTest`
- `SecurityUtilTest`

Database integration tests (guarded by `RUN_DB_IT=true`):

- `CheckpointVerificationIT`
- `IntegrityCheckpointAuditIT`
- `TamperingDetectionIT`

`MyUserDetailsServiceTrbacTest.java` is excluded because no current corrected
version exists and it does not represent the final per-request filter design.

## Prepare a disposable MySQL 8 database

Do not use the operational registry database.  Create a separate schema, then
apply the public schema and synthetic fixture:

```bash
mysql -u root -p -e "CREATE DATABASE gas_artifact_test CHARACTER SET utf8mb4;"
mysql -u root -p gas_artifact_test < db/schema_artifact.sql
mysql -u root -p gas_artifact_test < db/fixture_artifact.sql
```

Copy `config/application-artifact.properties.example` into an appropriate test
profile in the full private project, or provide all values through environment
variables.  Use non-production secrets.

## Unit tests

From this artifact directory, pass the path of the full project:

Linux/macOS:

```bash
bash tests/run_unit_tests.sh /path/to/private/GAS
```

PowerShell:

```powershell
& .\tests\run_unit_tests.ps1 -ProjectDir C:\path\to\private\GAS
```

## Database integration tests

The launchers refuse to run unless the disposable-database acknowledgement and
all connection/secret variables are set.

Linux/macOS:

```bash
export GAS_ARTIFACT_TEST_DB_ACK=YES
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/gas_artifact_test'
export SPRING_DATASOURCE_USERNAME='gas_artifact_test'
export SPRING_DATASOURCE_PASSWORD='local-test-password'
export SECURITY_SIGNATURE_SECRET='local-test-signature-secret'
export INTEGRITY_CHAIN_SECRET='local-test-chain-secret'
bash tests/run_db_integration_tests.sh /path/to/private/GAS
```

PowerShell:

```powershell
$env:GAS_ARTIFACT_TEST_DB_ACK = "YES"
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/gas_artifact_test"
$env:SPRING_DATASOURCE_USERNAME = "gas_artifact_test"
$env:SPRING_DATASOURCE_PASSWORD = "local-test-password"
$env:SECURITY_SIGNATURE_SECRET = "local-test-signature-secret"
$env:INTEGRITY_CHAIN_SECRET = "local-test-chain-secret"
& .\tests\run_db_integration_tests.ps1 -ProjectDir C:\path\to\private\GAS
```

The integration tests clear integrity-evidence tables and temporarily modify a
synthetic `freq` row; this is why a disposable database is mandatory.  The
minimal schema also contains a non-personal `roles` table required by the full
Spring application's startup initializer.

## Re-running and collecting evidence

After successful Maven runs, copy the complete directory:

`<private-GAS>/target/surefire-reports/`

to:

`tests/results/surefire-reports/`

Confirm that the expected classes were executed and that failures, errors, and
skips are zero.  A skipped DB test is not a passing integration-test result.
Before public redistribution, remove machine-local Surefire XML properties as
described in `tests/results/README.md`; do not alter suite or testcase results.
