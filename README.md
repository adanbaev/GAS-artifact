# GAS-artifact v1.3

Public research artifact for the manuscript:

**Application-Layer Integrity Protection and Temporal Administrative Control for Centralized State Registries: Implementation and Evaluation**

This package updates the public [GAS-artifact](https://github.com/adanbaev/GAS-artifact)
materials for the minor revision.  It is intentionally narrower than the
private `GAS` repository: it contains the article-relevant source extract,
tests, anonymized configuration and SQL fixtures, raw benchmark runs,
recomputed tables, figures, and plotting scripts.  It is not a replacement for
the deployable registry system.

## Release status

This package was published as GitHub release v1.3 and archived in Zenodo.  The
numerical-analysis and privacy checks pass.  The supplied Java tests were
executed in the matching full private project against a disposable MySQL
database: all 17 tests passed with zero failures, errors, or skips.  The
verified, privacy-sanitized Maven Surefire reports are included under
`tests/results/surefire-reports/`.

The version-specific Zenodo DOI for v1.3 is
<https://doi.org/10.5281/zenodo.22113859>.  This DOI is recorded in
`CITATION.cff` and should be used when citing this release.

The restored v1.2 release remains separately available at
<https://github.com/adanbaev/GAS-artifact/releases/tag/v1.2>.  It is not nested
inside this v1.3 package.

## Contents

- `code/` - selected, article-relevant Java implementation and benchmark
  sources; documented as a non-standalone extract.
- `tests/` - current unit/integration test sources, guarded launch scripts, and
  verified privacy-sanitized Surefire reports.
  The obsolete `MyUserDetailsServiceTrbacTest.java` is deliberately excluded.
- `config/` - public configuration template containing placeholders only.
- `db/` - minimal schema plus one wholly synthetic registry fixture.
- `data/raw/` - four unchanged run-level benchmark CSV files.
- `tables/manuscript/` - machine-readable copies of Tables 1-6 from the latest
  checked manuscript, including the previously missing Tables 1-2.
- `tables/recomputed/` - Tables 3-6 and K-sensitivity regenerated from raw data.
- `figures/` - Figures 1-6 and the revised graphical abstract.
- `scripts/` - statistical analysis and reproducible plotting scripts.
- `docs/` - provenance, privacy, statistical, and release-readiness notes.
- `SHA256SUMS.txt` - checksums for every release file except itself.

## Recompute Tables 3-6 and K-sensitivity

Requirements: Python 3.10+ and NumPy.

```bash
python -m pip install -r requirements.txt
python scripts/analyze_v1_3.py \
  --data-dir data/raw \
  --out-dir tables/recomputed \
  --bootstrap-reps 200000 \
  --seed 0
```

The script validates all run counts, successful/failed operation counts, and
mode-specific evidence counters before writing results.  Tables 3-5 use
independent percentile bootstrap comparisons.  Table 6 and K-sensitivity use
repeat-matched paired resampling.  Seed `0` is a neutral analysis parameter in
this new artifact script; it is not a date and is not a parameter of the
original benchmark execution.

See `docs/STATISTICAL_REPRODUCIBILITY.md` for the comparison with the printed
manuscript values.

## Java tests

The selected source extract is not a standalone application.  Run the tests in
the matching full/private GAS project, using only a disposable database.  See
`tests/README.md` for Windows and Linux/macOS commands and for the exact way to
collect Maven Surefire reports.

## Confidentiality boundary

No operational registry rows, production credentials, manuscript file,
tracked-change metadata, full private code archive, or private configuration is
included.  The public SQL fixture contains invented values only.  See
`docs/SECURITY_AND_PRIVACY.md`.

## Reference environment

- CPU: Intel Xeon E5-2680 v3 (2.50 GHz)
- Memory: 32 GB RAM
- OS: Windows 10 Pro 22H2 (build 19045)
- Storage: local NVMe SSD (Kingston SNVS250)
- Database: MySQL 8.0; MyISAM registry tables and InnoDB integrity tables
- JDK: Amazon Corretto 17.0.18
- Application: Spring Boot 2.7

## License and citation

The artifact is released under the MIT License.  Citation metadata are in
`CITATION.cff`.  The archived v1.3 record is available at
<https://doi.org/10.5281/zenodo.22113859>.
