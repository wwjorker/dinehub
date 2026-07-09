#!/usr/bin/env python3
"""Summarize a JMeter JTL (CSV) result file: QPS, P50, P95, P99, error rate."""
import csv
import sys


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0
    k = max(0, min(len(sorted_vals) - 1, int(round(p / 100.0 * len(sorted_vals) + 0.5)) - 1))
    return sorted_vals[k]


def main(path):
    elapsed, errors = [], 0
    t_min, t_max = None, None
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ts = int(row["timeStamp"])
            el = int(row["elapsed"])
            elapsed.append(el)
            if row["success"] != "true":
                errors += 1
            t_min = ts if t_min is None else min(t_min, ts)
            t_max = max(t_max or 0, ts + el)
    n = len(elapsed)
    wall = (t_max - t_min) / 1000.0 if n else 0
    elapsed.sort()
    print("samples=%d wall=%.1fs qps=%.1f p50=%dms p95=%dms p99=%dms errors=%d(%.2f%%)" % (
        n, wall, n / wall if wall else 0, percentile(elapsed, 50),
        percentile(elapsed, 95), percentile(elapsed, 99), errors, 100.0 * errors / n if n else 0))


if __name__ == "__main__":
    main(sys.argv[1])
