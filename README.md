# GAS-artifact

Selected implementation artifacts supporting the manuscript:

"Application-Layer Integrity Protection and Temporal Administrative Control for Centralized State Registries: Design, Implementation, and Performance Evaluation"

## Contents
- benchmark runner
- integrity-related service classes
- selected DAO and utility classes
- selected integration and security tests
- example application configuration without secrets

## Notes
This repository does not contain operational registry data, production credentials, or deployment-specific configuration.

## Reproducibility
The manuscript reports the performance benchmark with checkpoint batch size K = 100.
Integration tests use separate test-oriented settings.

### Performance benchmark
The performance results reported in Table 3, Fig. 3, and Fig. 4 are produced by:

`src/main/java/kg/gov/nas/licensedb/bench/MicrobenchmarkRunner.java`

The benchmark configuration reported in the manuscript uses checkpoint batch size `K = 100`.

### Selected validation tests
The integration-level validation discussed in the manuscript is supported by selected tests in:

- `src/test/java/kg/gov/nas/licensedb/service/IntegrityCheckpointAuditIT.java`
- `src/test/java/kg/gov/nas/licensedb/service/TamperingDetectionIT.java`
- `src/test/java/kg/gov/nas/licensedb/service/MyUserDetailsServiceTrbacTest.java`

### Configuration note
This repository contains `application.example.properties` only.
Operational registry data, production credentials, and deployment-specific configuration are not included.
