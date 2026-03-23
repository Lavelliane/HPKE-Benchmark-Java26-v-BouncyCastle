#!/usr/bin/env python3
"""
Parse JMH JSON outputs under results/raw/, export combined/per-benchmark CSVs,
and generate LaTeX table + PDF figures for the paper.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _flatten_jmh_entry(entry: dict) -> dict:
    pm = entry.get("primaryMetric") or {}
    params = entry.get("params") or {}
    row = {
        "benchmark": entry.get("benchmark", ""),
        "mode": entry.get("mode", ""),
        "library": params.get("library", ""),
        "suite": params.get("suite", ""),
        "payloadSize": params.get("payloadSize", ""),
        "score": pm.get("score"),
        "score_error": pm.get("scoreError"),
        "unit": pm.get("scoreUnit", ""),
    }
    sm = entry.get("secondaryMetrics") or {}
    gc = sm.get("gc.alloc.rate.norm") or {}
    if isinstance(gc, dict):
        row["gc_alloc_rate_norm"] = gc.get("score")
        row["gc_alloc_rate_norm_unit"] = gc.get("scoreUnit", "")
    else:
        row["gc_alloc_rate_norm"] = None
        row["gc_alloc_rate_norm_unit"] = ""
    return row


def load_jmh_json_files(raw_dir: Path) -> pd.DataFrame:
    rows: list[dict] = []
    for p in sorted(raw_dir.glob("*.json")):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"Skip invalid JSON {p}: {e}", file=sys.stderr)
            continue
        if not isinstance(data, list):
            continue
        for entry in data:
            if isinstance(entry, dict):
                rows.append(_flatten_jmh_entry(entry))
    return pd.DataFrame(rows)


def _normalize_jmh_csv(df: pd.DataFrame) -> pd.DataFrame:
    out = pd.DataFrame()
    out["benchmark"] = df.get("Benchmark", "")
    out["mode"] = df.get("Mode", "")
    out["library"] = df.get("Param: library", "")
    out["suite"] = df.get("Param: suite", "")
    out["payloadSize"] = df.get("Param: payloadSize", "")
    out["score"] = df.get("Score")
    out["score_error"] = df.get("Score Error (99.9%)")
    out["unit"] = df.get("Unit", "")
    out["gc_alloc_rate_norm"] = None
    out["gc_alloc_rate_norm_unit"] = ""
    return out


def load_jmh_csv_files(raw_dir: Path) -> pd.DataFrame:
    """Fallback: merge JMH-exported CSV files (e.g. from -rf csv)."""
    frames: list[pd.DataFrame] = []
    for p in sorted(raw_dir.glob("*.csv")):
        if p.name == "all_results.csv":
            continue
        try:
            raw = pd.read_csv(p)
            norm = _normalize_jmh_csv(raw)
            if not norm.empty:
                frames.append(norm)
        except Exception as e:
            print(f"Skip CSV {p}: {e}", file=sys.stderr)
    if not frames:
        return pd.DataFrame()
    return pd.concat(frames, ignore_index=True)


def csv_from_dataframe(df: pd.DataFrame, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out, index=False)


def write_latex_table_seal_1k(df: pd.DataFrame, out: Path) -> None:
    seal = df[df["benchmark"].str.contains("SealBenchmark", na=False)]
    if seal.empty or "payloadSize" not in seal.columns:
        out.write_text("% No SealBenchmark data for 1KB table\n", encoding="utf-8")
        return
    sub = seal[seal["payloadSize"].astype(str) == "1024"].copy()
    if sub.empty:
        out.write_text("% No payloadSize=1024 rows\n", encoding="utf-8")
        return
    pivot = sub.pivot_table(
        index="suite", columns="library", values="score", aggfunc="first"
    )
    lines = [
        r"\begin{tabular}{lrr}",
        r"\hline",
        r"Suite & JDK26 (us/op) & BC (us/op) \\",
        r"\hline",
    ]
    for suite in pivot.index:
        j = pivot.loc[suite].get("JDK26", float("nan"))
        b = pivot.loc[suite].get("BC", float("nan"))
        lines.append(f"{suite} & {j:.3f} & {b:.3f} \\\\")
    lines.extend([r"\hline", r"\end{tabular}"])
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def plot_seal_by_payload(df: pd.DataFrame, plots_dir: Path) -> None:
    seal = df[df["benchmark"].str.contains("SealBenchmark", na=False)]
    if seal.empty:
        return
    seal = seal.copy()
    seal["payloadSize"] = seal["payloadSize"].astype(str)
    suites = sorted(seal["suite"].dropna().unique())
    if not suites:
        return
    payloads = ["64", "1024", "65536"]
    fig, axes = plt.subplots(2, 4, figsize=(14, 7))
    axes = axes.flatten()
    width = 0.35
    x = np.arange(len(payloads))
    for ax, suite in zip(axes, suites[:8]):
        sub = seal[seal["suite"] == suite]
        vals_jdk = []
        vals_bc = []
        for ps in payloads:
            rj = sub[(sub["library"] == "JDK26") & (sub["payloadSize"] == ps)]
            rb = sub[(sub["library"] == "BC") & (sub["payloadSize"] == ps)]
            vals_jdk.append(float(rj["score"].iloc[0]) if len(rj) else 0.0)
            vals_bc.append(float(rb["score"].iloc[0]) if len(rb) else 0.0)
        ax.bar(x - width / 2, vals_jdk, width, label="JDK26", color="tab:blue", alpha=0.85)
        ax.bar(x + width / 2, vals_bc, width, label="BC", color="tab:orange", alpha=0.85)
        ax.set_title(suite, fontsize=9)
        ax.set_xticks(x)
        ax.set_xticklabels(payloads)
        ax.set_xlabel("payload bytes")
        ax.set_ylabel("us/op")
        ax.legend(fontsize=7)
    plt.tight_layout()
    plots_dir.mkdir(parents=True, exist_ok=True)
    fig.savefig(plots_dir / "figure_seal_by_payload.png", dpi=150, bbox_inches="tight")
    plt.close(fig)


def plot_keygen(df: pd.DataFrame, plots_dir: Path) -> None:
    kg = df[df["benchmark"].str.contains("KeygenBenchmark", na=False)]
    if kg.empty:
        return
    pivot = kg.pivot_table(index="suite", columns="library", values="score", aggfunc="first")
    ax = pivot.plot(kind="bar", figsize=(10, 5), rot=45)
    ax.set_ylabel("us/op")
    ax.set_title("Key generation latency")
    plt.tight_layout()
    plots_dir.mkdir(parents=True, exist_ok=True)
    plt.savefig(plots_dir / "figure_keygen.png", dpi=150, bbox_inches="tight")
    plt.close()


def main() -> int:
    ap = argparse.ArgumentParser(description="Analyze JMH JSON/CSV under results/raw/")
    ap.add_argument(
        "--raw-dir",
        type=Path,
        default=_repo_root() / "results" / "raw",
        help="Directory containing JMH .json and/or .csv outputs",
    )
    ap.add_argument(
        "--plots-dir",
        type=Path,
        default=_repo_root() / "results" / "plots",
        help="Output directory for PDF figures",
    )
    args = ap.parse_args()

    df = load_jmh_json_files(args.raw_dir)
    if df.empty:
        df = load_jmh_csv_files(args.raw_dir)
        if df.empty:
            print("No data: add JMH .json or .csv under", args.raw_dir, file=sys.stderr)
            return 1

    args.raw_dir.mkdir(parents=True, exist_ok=True)
    args.plots_dir.mkdir(parents=True, exist_ok=True)
    csv_from_dataframe(df, args.raw_dir / "all_results.csv")

    for bench in df["benchmark"].dropna().unique():
        short = str(bench).split(".")[-1]
        sub = df[df["benchmark"] == bench]
        csv_from_dataframe(sub, args.raw_dir / f"{short}.csv")

    write_latex_table_seal_1k(df, args.plots_dir / "table_seal_1kb.tex")
    plot_seal_by_payload(df, args.plots_dir)
    plot_keygen(df, args.plots_dir)

    print("Wrote:", args.raw_dir / "all_results.csv", args.plots_dir / "figure_seal_by_payload.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
