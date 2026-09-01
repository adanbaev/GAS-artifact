#!/usr/bin/env python3
"""Recompute Tables 3-6 and K-sensitivity from the v1.3 raw CSV files.

The script never modifies raw measurements.  It validates run counts and
evidence counters, then writes machine-readable result tables and analysis
metadata.  Bootstrap sampling is deterministic: the neutral master seed is 0,
and each comparison receives a stable sub-seed derived from its label.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from statistics import median, stdev

import numpy as np


MODE_LABEL = {
    "HMAC_ONLY": "FINGERPRINT",
    "HMAC_CHECKPOINT": "CHECKPOINT",
    "HMAC_STRICT": "STRICT",
    "HMAC_COMPUTE": "HMAC_COMPUTE",
    "STANDARD": "STANDARD",
}


def read_runs(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        rows = [row for row in csv.DictReader(stream) if row["record_type"] == "RUN"]
    if not rows:
        raise ValueError(f"No RUN rows found in {path}")
    return rows


def as_float(rows: list[dict[str, str]], key: str) -> np.ndarray:
    return np.asarray([float(row[key]) for row in rows], dtype=np.float64)


def stable_rng(master_seed: int, label: str) -> np.random.Generator:
    material = f"GAS-artifact-v1.3|{master_seed}|{label}".encode("utf-8")
    sub_seed = int.from_bytes(hashlib.sha256(material).digest()[:8], "big")
    return np.random.default_rng(sub_seed)


def independent_ci(
    reference: np.ndarray,
    evaluated: np.ndarray,
    metric,
    reps: int,
    rng: np.random.Generator,
    batch: int = 10_000,
) -> tuple[float, float]:
    values = np.empty(reps, dtype=np.float64)
    for start in range(0, reps, batch):
        count = min(batch, reps - start)
        ref_idx = rng.integers(0, len(reference), size=(count, len(reference)))
        eval_idx = rng.integers(0, len(evaluated), size=(count, len(evaluated)))
        ref_med = np.median(reference[ref_idx], axis=1)
        eval_med = np.median(evaluated[eval_idx], axis=1)
        values[start : start + count] = metric(ref_med, eval_med)
    low, high = np.percentile(values, [2.5, 97.5])
    return float(low), float(high)


def paired_ci(
    reference: np.ndarray,
    evaluated: np.ndarray,
    metric,
    reps: int,
    rng: np.random.Generator,
    batch: int = 10_000,
) -> tuple[float, float]:
    if len(reference) != len(evaluated):
        raise ValueError("Paired bootstrap inputs have different lengths")
    values = np.empty(reps, dtype=np.float64)
    for start in range(0, reps, batch):
        count = min(batch, reps - start)
        idx = rng.integers(0, len(reference), size=(count, len(reference)))
        ref_med = np.median(reference[idx], axis=1)
        eval_med = np.median(evaluated[idx], axis=1)
        values[start : start + count] = metric(ref_med, eval_med)
    low, high = np.percentile(values, [2.5, 97.5])
    return float(low), float(high)


def overhead(ref_med, eval_med):
    return (eval_med / ref_med - 1.0) * 100.0


def reduction(ref_med, eval_med):
    return (1.0 - eval_med / ref_med) * 100.0


def group(rows: list[dict[str, str]], *keys: str):
    grouped = defaultdict(list)
    for row in rows:
        grouped[tuple(row[key] for key in keys)].append(row)
    return grouped


def sort_by_repeat(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return sorted(rows, key=lambda row: int(row["repeat"]))


def validate_rows(rows: list[dict[str, str]], expected_repeats: int, label: str) -> dict:
    cells = group(rows, "mode", "n_ops", "threads", "k_value")
    errors: list[str] = []
    for cell, cell_rows in sorted(cells.items()):
        mode, n_ops, _threads, k_value = cell
        n = int(n_ops)
        k = int(k_value)
        if len(cell_rows) != expected_repeats:
            errors.append(f"{label} {cell}: expected {expected_repeats} runs, found {len(cell_rows)}")
        repeats = sorted(int(row["repeat"]) for row in cell_rows)
        if repeats != list(range(1, expected_repeats + 1)):
            errors.append(f"{label} {cell}: repeat identifiers are not 1..{expected_repeats}")
        for row in cell_rows:
            planned = int(row["planned_ops"])
            success = int(row["successful_ops"])
            failed = int(row["failed_ops"])
            events = int(row["event_rows"])
            logs = int(row["log_rows"])
            checkpoints = int(row["checkpoint_rows"])
            if success != planned or failed != 0:
                errors.append(f"{label} {cell} repeat {row['repeat']}: operation-count mismatch")
            expected = (0, 0, 0)
            if mode == "HMAC_ONLY":
                expected = (success, 0, 0)
            elif mode == "HMAC_STRICT":
                expected = (0, success, 0)
            elif mode == "HMAC_CHECKPOINT":
                expected = (success, 0, success // k)
            if (events, logs, checkpoints) != expected:
                errors.append(
                    f"{label} {cell} repeat {row['repeat']}: evidence "
                    f"{(events, logs, checkpoints)} != {expected}"
                )
    if errors:
        raise ValueError("\n".join(errors))
    return {"dataset": label, "run_rows": len(rows), "cells": len(cells), "status": "PASS"}


def write_csv(path: Path, fieldnames: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def fmt(value: float, places: int) -> str:
    quantum = Decimal("1") if places == 0 else Decimal("1." + ("0" * places))
    return format(Decimal(str(value)).quantize(quantum, rounding=ROUND_HALF_UP), f".{places}f")


def table_3_and_4(rows, reps, seed, out_dir):
    cells = group(rows, "n_ops", "mode")
    t3, t4 = [], []
    for n_text in sorted({key[0] for key in cells}, key=int):
        n = int(n_text)
        standard = cells[(n_text, "STANDARD")]
        std_ms = as_float(standard, "total_ms")
        base_tps = median(float(row["tps_success"]) for row in standard)

        result = {"n_updates": n, "standard_median_tps": fmt(base_tps, 1),
                  "standard_sd_total_ms": fmt(stdev(float(row["total_ms"]) for row in standard), 0)}
        for mode, prefix in (("HMAC_ONLY", "fingerprint"), ("HMAC_CHECKPOINT", "checkpoint")):
            evaluated = cells[(n_text, mode)]
            eval_ms = as_float(evaluated, "total_ms")
            point = overhead(np.median(std_ms), np.median(eval_ms))
            ci = independent_ci(std_ms, eval_ms, overhead, reps,
                                stable_rng(seed, f"table3|{n}|{mode}"))
            result.update({
                f"{prefix}_median_tps": fmt(median(float(row["tps_success"]) for row in evaluated), 1),
                f"{prefix}_overhead_pct": fmt(point, 2),
                f"{prefix}_ci_low_pct": fmt(ci[0], 2),
                f"{prefix}_ci_high_pct": fmt(ci[1], 2),
                f"{prefix}_sd_total_ms": fmt(stdev(float(row["total_ms"]) for row in evaluated), 0),
            })
        t3.append(result)

        strict = cells[(n_text, "HMAC_STRICT")]
        strict_ms = as_float(strict, "total_ms")
        point = overhead(np.median(std_ms), np.median(strict_ms))
        ci = independent_ci(std_ms, strict_ms, overhead, reps,
                            stable_rng(seed, f"table4|{n}|HMAC_STRICT"))
        t4.append({
            "n_updates": n,
            "strict_median_tps": fmt(median(float(row["tps_success"]) for row in strict), 1),
            "strict_overhead_pct": fmt(point, 2),
            "strict_ci_low_pct": fmt(ci[0], 2),
            "strict_ci_high_pct": fmt(ci[1], 2),
            "strict_sd_total_ms": fmt(stdev(float(row["total_ms"]) for row in strict), 0),
        })

    write_csv(out_dir / "Table_3_recomputed.csv", list(t3[0]), t3)
    write_csv(out_dir / "Table_4_recomputed.csv", list(t4[0]), t4)


def table_5(rows, reps, seed, out_dir):
    cells = group(rows, "threads", "mode")
    output = []
    for threads_text in sorted({key[0] for key in cells}, key=int):
        threads = int(threads_text)
        standard = cells[(threads_text, "STANDARD")]
        std_tps = as_float(standard, "tps_success")
        result = {"threads": threads, "standard_median_tps": fmt(float(np.median(std_tps)), 1)}
        for mode, prefix in (("HMAC_ONLY", "fingerprint"), ("HMAC_CHECKPOINT", "checkpoint"),
                             ("HMAC_STRICT", "strict")):
            evaluated = cells[(threads_text, mode)]
            eval_tps = as_float(evaluated, "tps_success")
            point = reduction(np.median(std_tps), np.median(eval_tps))
            ci = independent_ci(std_tps, eval_tps, reduction, reps,
                                stable_rng(seed, f"table5|{threads}|{mode}"))
            result.update({
                f"{prefix}_median_tps": fmt(float(np.median(eval_tps)), 1),
                f"{prefix}_reduction_pct": fmt(point, 1),
                f"{prefix}_ci_low_pct": fmt(ci[0], 1),
                f"{prefix}_ci_high_pct": fmt(ci[1], 1),
            })
        output.append(result)
    write_csv(out_dir / "Table_5_recomputed.csv", list(output[0]), output)


def table_6(rows, reps, seed, out_dir):
    cells = group(rows, "n_ops", "mode")
    output = []
    for n_text in sorted({key[0] for key in cells}, key=int):
        n = int(n_text)
        standard = sort_by_repeat(cells[(n_text, "STANDARD")])
        compute = sort_by_repeat(cells[(n_text, "HMAC_COMPUTE")])
        if [r["repeat"] for r in standard] != [r["repeat"] for r in compute]:
            raise ValueError(f"Table 6 pairing mismatch at N={n}")
        std_ms = as_float(standard, "total_ms")
        comp_ms = as_float(compute, "total_ms")
        point_overhead = overhead(np.median(std_ms), np.median(comp_ms))
        ci_overhead = paired_ci(std_ms, comp_ms, overhead, reps,
                                stable_rng(seed, f"table6-overhead|{n}"))
        added_metric = lambda ref_med, eval_med: (eval_med - ref_med) / n * 1000.0
        point_added = added_metric(np.median(std_ms), np.median(comp_ms))
        ci_added = paired_ci(std_ms, comp_ms, added_metric, reps,
                             stable_rng(seed, f"table6-added|{n}"))
        output.append({
            "n_updates": n,
            "standard_median_tps": fmt(median(float(r["tps_success"]) for r in standard), 1),
            "hmac_compute_median_tps": fmt(median(float(r["tps_success"]) for r in compute), 1),
            "added_us_per_update": fmt(point_added, 0),
            "added_ci_low_us": fmt(ci_added[0], 0),
            "added_ci_high_us": fmt(ci_added[1], 0),
            "overhead_pct": fmt(point_overhead, 2),
            "overhead_ci_low_pct": fmt(ci_overhead[0], 2),
            "overhead_ci_high_pct": fmt(ci_overhead[1], 2),
            "standard_sd_total_ms": fmt(stdev(float(r["total_ms"]) for r in standard), 0),
            "hmac_compute_sd_total_ms": fmt(stdev(float(r["total_ms"]) for r in compute), 0),
        })
    write_csv(out_dir / "Table_6_recomputed.csv", list(output[0]), output)


def k_sensitivity(rows, reps, seed, out_dir):
    cells = group(rows, "mode", "k_value")
    standard = sort_by_repeat(cells[("STANDARD", "0")])
    std_ms = as_float(standard, "total_ms")
    output = [{
        "mode": "STANDARD", "k": 0,
        "median_tps": fmt(median(float(r["tps_success"]) for r in standard), 1),
        "overhead_pct": fmt(0.0, 2), "ci_low_pct": "", "ci_high_pct": "",
    }]
    for mode, k in (("HMAC_ONLY", 0), ("HMAC_CHECKPOINT", 50),
                    ("HMAC_CHECKPOINT", 100), ("HMAC_CHECKPOINT", 500)):
        evaluated = sort_by_repeat(cells[(mode, str(k))])
        if [r["repeat"] for r in standard] != [r["repeat"] for r in evaluated]:
            raise ValueError(f"K-sensitivity pairing mismatch for {mode}, K={k}")
        eval_ms = as_float(evaluated, "total_ms")
        point = overhead(np.median(std_ms), np.median(eval_ms))
        ci = paired_ci(std_ms, eval_ms, overhead, reps,
                       stable_rng(seed, f"ksens|{mode}|{k}"))
        output.append({
            "mode": MODE_LABEL[mode], "k": k,
            "median_tps": fmt(median(float(r["tps_success"]) for r in evaluated), 1),
            "overhead_pct": fmt(point, 2),
            "ci_low_pct": fmt(ci[0], 2), "ci_high_pct": fmt(ci[1], 2),
        })
    write_csv(out_dir / "K_sensitivity_recomputed.csv", list(output[0]), output)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, default=Path("data/raw"))
    parser.add_argument("--out-dir", type=Path, default=Path("tables/recomputed"))
    parser.add_argument("--bootstrap-reps", type=int, default=200_000)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()
    if args.bootstrap_reps < 1:
        parser.error("--bootstrap-reps must be positive")

    inputs = {
        "single": args.data_dir / "bench-results-revised-single-20260821-211651.csv",
        "concurrent": args.data_dir / "bench-results-revised-concurrent-20260823-100240.csv",
        "isolation": args.data_dir / "bench-results-revised-isolation-20260823-011349.csv",
        "ksens": args.data_dir / "bench-results-revised-ksens-20260823-171330.csv",
    }
    datasets = {name: read_runs(path) for name, path in inputs.items()}
    validations = [
        validate_rows(datasets["single"], 30, "single"),
        validate_rows(datasets["concurrent"], 16, "concurrent"),
        validate_rows(datasets["isolation"], 30, "isolation"),
        validate_rows(datasets["ksens"], 30, "ksens"),
    ]

    args.out_dir.mkdir(parents=True, exist_ok=True)
    table_3_and_4(datasets["single"], args.bootstrap_reps, args.seed, args.out_dir)
    table_5(datasets["concurrent"], args.bootstrap_reps, args.seed, args.out_dir)
    table_6(datasets["isolation"], args.bootstrap_reps, args.seed, args.out_dir)
    k_sensitivity(datasets["ksens"], args.bootstrap_reps, args.seed, args.out_dir)

    metadata = {
        "analysis": "GAS-artifact v1.3.1",
        "bootstrap_replicates": args.bootstrap_reps,
        "master_seed": args.seed,
        "seed_note": "Neutral analysis seed; not a benchmark date or experimental parameter.",
        "ci": "two-sided percentile bootstrap, 2.5th and 97.5th percentiles",
        "independent_comparisons": ["Tables 3-5"],
        "paired_comparisons": ["Table 6 by repeat", "K-sensitivity by repeat"],
        "validation": validations,
        "inputs": {name: {"file": path.name, "sha256": sha256(path)} for name, path in inputs.items()},
    }
    (args.out_dir / "analysis_metadata.json").write_text(
        json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print("PASS: raw-run and evidence-count validation")
    print(f"Wrote recomputed tables to {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
