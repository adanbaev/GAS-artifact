# Raw benchmark data

The four CSV files in `raw/` are copied byte-for-byte from the supplied v1.3
source-material archive.  `SUMMARY` rows are retained; the analysis script uses
only `RUN` rows.

| File | Purpose | Repeats |
|---|---|---:|
| `bench-results-revised-single-20260821-211651.csv` | Tables 3-4, Figures 3-4 | 30 per cell |
| `bench-results-revised-concurrent-20260823-100240.csv` | Table 5, Figures 5-6 | 16 per cell |
| `bench-results-revised-isolation-20260823-011349.csv` | Table 6 (`HMAC_COMPUTE`) | 30 per cell |
| `bench-results-revised-ksens-20260823-171330.csv` | K-sensitivity at N=100,000 | 30 per cell |

The statistical script verifies zero failed operations and the expected event,
strict-log, and checkpoint counts before accepting these data.
