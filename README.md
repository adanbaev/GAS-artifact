# GAS-artifact

Selected implementation artifacts supporting the accompanying research manuscript:

**Application-Layer Integrity Protection and Temporal Administrative Control for Centralized State Registries: Implementation and Evaluation**

## Overview

This repository is a curated public research artifact intended for technical inspection of the accompanying manuscript. It contains selected implementation fragments, benchmark outputs, figures, scripts, and supporting tests necessary to evaluate the manuscript’s claims.

The artifact reflects a practical application-layer deployment model for strengthening integrity assurance and administrative control in centralized SQL-based registry systems.

## Scope

This repository contains only the materials needed to support the manuscript claims and public artifact release.

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

The included code and materials reflect the revised implementation described in the manuscript, including:

- HMAC-SHA-256 for record fingerprinting
- length-prefixed deterministic protected input construction
- per-request TRBAC enforcement via request filter
- 30-run benchmark statistics
- concurrent benchmark with 2 and 4 threads
- checkpoint batch-size sensitivity experiment for `K ∈ {50, 100, 500}`

## Data availability

Selected implementation artifacts, benchmark outputs, figure files, plotting scripts, and supporting test materials used in the study are publicly available in this repository. The underlying operational registry data cannot be publicly shared due to confidentiality and administrative constraints.

## Confidentiality note

Operational registry data and deployment secrets are excluded for confidentiality and security reasons. The included configuration file is an example file only and contains no production credentials.

## Versioning and citation

Please cite the specific tagged release used in your work. Release-based citation is preferred over citation of the repository root.

Citation metadata is provided in `CITATION.cff`.

Archived release DOI: https://doi.org/10.5281/zenodo.19478931

## Authors

- Aibek Adanbaev
- Abdisamat Sagymbaev

## License

This repository is distributed under the MIT License. See `LICENSE` for details.

## Purpose

This repository is intended for editors, reviewers, and readers who need a focused, inspectable research artifact aligned with the accompanying manuscript.
