@'
# GAS-artifact

Selected implementation artifacts supporting the manuscript:

**Application-Layer Integrity Protection and Temporal Administrative Control for Centralized State Registries: Implementation and Evaluation**

## Scope

This repository is a curated public artifact for journal inspection. It contains only the implementation fragments, benchmark outputs, figures, scripts, and selected tests necessary to support the manuscript claims.

It does **not** contain:
- operational registry data
- production credentials
- deployment-specific configuration
- UI templates, static assets, or non-essential application modules

## Included components

### Core code
- `src/main/java/kg/gov/nas/licensedb/bench/MicrobenchmarkRunner.java`
- `src/main/java/kg/gov/nas/licensedb/util/SecurityUtil.java`
- `src/main/java/kg/gov/nas/licensedb/service/IntegrityService.java`
- `src/main/java/kg/gov/nas/licensedb/security/TrbacPerRequestFilter.java`
- `src/main/java/kg/gov/nas/licensedb/service/FreqCrudService.java`

### Selected validation tests
- `src/test/java/kg/gov/nas/licensedb/service/IntegrityCheckpointAuditIT.java`
- `src/test/java/kg/gov/nas/licensedb/service/TamperingDetectionIT.java`
- `src/test/java/kg/gov/nas/licensedb/service/MyUserDetailsServiceTrbacTest.java`

### Configuration
- `src/main/resources/application.example.properties`

### Benchmark and figure artifacts
- `artifacts/bench-results.csv`
- `artifacts/figures/Figure_1.pdf`
- `artifacts/figures/Figure_2.pdf`
- `artifacts/figures/Figure_3.pdf`
- `artifacts/figures/Figure_4.pdf`
- `artifacts/figures/Figure_1.drawio`
- `artifacts/figures/Figure_2.drawio`
- `artifacts/scripts/grafik3_tuned.py`
- `artifacts/scripts/grafik4_tuned.py`

## What this artifact demonstrates

The included code reflects the revised implementation described in the manuscript:

- HMAC-SHA-256 for record fingerprinting
- length-prefixed deterministic protected input
- per-request TRBAC enforcement via request filter
- 30-run benchmark statistics
- concurrent benchmark with 2 and 4 threads
- checkpoint batch-size sensitivity experiment for `K ∈ {50, 100, 500}`

## Confidentiality note

Operational registry data and deployment secrets are excluded for confidentiality and security reasons. The included configuration file is an example file only and contains no production credentials.

## Release purpose

This repository is intended for editors, reviewers, and readers who need a focused, inspectable artifact aligned with the submitted Array manuscript.
'@ | Set-Content "D:\Repos\GAS-artifact\README.md" -Encoding UTF8