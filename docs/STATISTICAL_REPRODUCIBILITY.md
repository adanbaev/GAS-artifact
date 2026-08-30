# Statistical reproducibility note

## What is exactly reproduced

From the four raw CSV files, the new analysis script reproduces the manuscript
point estimates (median TPS, overhead/reduction point estimates, added time per
update) and sample SD values, subject only to the manuscript's displayed
rounding.  It also validates all configured repeat counts, zero failed
operations, and mode-specific persisted-evidence counts.

## Bootstrap intervals

The confidence intervals printed in the manuscript were produced during the
revision analysis, but the random resampling stream was not preserved as a
script in the supplied historical materials.  The new v1.3 script therefore
uses a transparent neutral master seed of `0` with 200,000 percentile-bootstrap
replicates.  The seed is an analysis implementation detail, not an experiment
date or benchmark parameter.

Relative to the printed manuscript intervals, the fresh deterministic run has:

- a maximum CI-endpoint difference of 0.17 percentage points across Tables 3-5;
- a maximum CI-endpoint difference of 0.01 percentage points for the Table 6
  overhead intervals;
- a maximum difference of 1 microsecond for the Table 6 added-time intervals;
- a maximum CI-endpoint difference of 0.02 percentage points for K-sensitivity.

These changes are Monte Carlo resampling variation and do not change any point
estimate, ordering, conclusion, or reported performance range.  Both the exact
printed tables and the independently recomputed tables are retained so the
provenance is explicit rather than silently forcing a match.

## Sampling structure

- Tables 3-4: independent resampling of the 30 evaluated-mode and 30 STANDARD
  total-runtime observations; overhead recomputed from resampled medians.
- Table 5: independent resampling of 16 evaluated-mode and 16 STANDARD TPS
  observations at each thread count; reduction recomputed from medians.
- Table 6: paired resampling by repeat identifier; median-runtime difference and
  ratio recomputed for each resample.
- K-sensitivity: paired resampling of matched repeat blocks.
