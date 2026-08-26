#!/usr/bin/env python3
"""Generate the revised graphical abstract for the GAS minor revision.

The concurrency panel uses the approved median aggregate TPS values from
Table 5. Scalability is computed as TPS(T) / TPS(1) for each strategy.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch
import numpy as np


THREADS = np.array([1, 2, 4, 8, 16, 32])
MEDIAN_TPS = {
    "STANDARD": np.array([480.0, 917.7, 1257.5, 1338.0, 1266.9, 1253.5]),
    "FINGERPRINT": np.array([162.9, 297.9, 470.4, 671.0, 685.8, 934.2]),
    "CHECKPOINT": np.array([272.2, 476.6, 686.6, 644.5, 521.2, 596.5]),
    "STRICT": np.array([145.5, 272.5, 303.1, 309.1, 297.7, 265.9]),
}

COLORS = {
    "ink": "#18334D",
    "arrow": "#36526F",
    "STANDARD": "#1F77B4",
    "FINGERPRINT": "#FF7F0E",
    "CHECKPOINT": "#2CA02C",
    "STRICT": "#D62728",
}


def add_box(ax, xy, width, height, text, *, face, edge, fontsize=9.2,
            linewidth=1.5, radius=0.02, weight="semibold", zorder=3):
    """Add a rounded labeled box in axes-fraction coordinates."""
    x, y = xy
    patch = FancyBboxPatch(
        (x, y), width, height,
        boxstyle=f"round,pad=0.012,rounding_size={radius}",
        transform=ax.transAxes,
        facecolor=face,
        edgecolor=edge,
        linewidth=linewidth,
        zorder=zorder,
    )
    ax.add_patch(patch)
    ax.text(
        x + width / 2,
        y + height / 2,
        text,
        transform=ax.transAxes,
        ha="center",
        va="center",
        fontsize=fontsize,
        color=COLORS["ink"],
        fontweight=weight,
        linespacing=1.05,
        zorder=zorder + 1,
    )
    return patch


def add_arrow(ax, start, end, *, connectionstyle="arc3,rad=0", color=None,
              mutation_scale=9.5, linewidth=1.45, zorder=6):
    """Add a directional arrow in axes-fraction coordinates."""
    arrow = FancyArrowPatch(
        start,
        end,
        transform=ax.transAxes,
        arrowstyle="-|>",
        mutation_scale=mutation_scale,
        linewidth=linewidth,
        color=color or COLORS["arrow"],
        connectionstyle=connectionstyle,
        shrinkA=0,
        shrinkB=0,
        zorder=zorder,
    )
    ax.add_patch(arrow)
    return arrow


def draw_architecture(ax):
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    add_box(
        ax, (0.015, 0.735), 0.215, 0.125,
        "Privileged\nwrite request",
        face="#EDF3F8", edge="#36526F", fontsize=9.4,
    )
    add_box(
        ax, (0.285, 0.735), 0.300, 0.125,
        "Per-request temporal\nauthority control",
        face="#FFF0E0", edge="#D98016", fontsize=9.0,
    )
    add_box(
        ax, (0.640, 0.735), 0.330, 0.125,
        "Service-layer\nregistry update",
        face="#F0EFF9", edge="#5A5A91", fontsize=9.2,
    )
    # Rounded-box padding extends beyond nominal coordinates. These endpoints
    # leave a deliberate white gap on both sides of every arrow.
    add_arrow(ax, (0.247, 0.797), (0.268, 0.797))
    add_arrow(ax, (0.602, 0.797), (0.623, 0.797))

    add_box(
        ax, (0.055, 0.445), 0.350, 0.145,
        "MySQL / InnoDB\nregistry record",
        face="#EDF3F8", edge="#36526F", fontsize=9.3,
    )
    add_box(
        ax, (0.525, 0.445), 0.420, 0.145,
        "Fresh DB re-read +\n23-field HMAC-SHA-256",
        face="#E4F3E8", edge="#31934F", fontsize=9.0,
    )
    add_arrow(
        ax, (0.805, 0.710), (0.360, 0.615),
    )
    add_arrow(ax, (0.429, 0.517), (0.501, 0.517))

    evidence = FancyBboxPatch(
        (0.035, 0.115), 0.925, 0.215,
        boxstyle="round,pad=0.012,rounding_size=0.02",
        transform=ax.transAxes,
        facecolor="#FBF7FB",
        edgecolor="#9A3A97",
        linewidth=1.45,
        zorder=1,
    )
    ax.add_patch(evidence)
    ax.text(
        0.497, 0.298, "Evidence strategy after a successful write",
        transform=ax.transAxes, ha="center", va="center",
        fontsize=9.0, color=COLORS["ink"], fontweight="semibold", zorder=4,
    )

    add_box(
        ax, (0.060, 0.145), 0.255, 0.110,
        "FINGERPRINT\nper-write event",
        face="#FFF1E5", edge=COLORS["FINGERPRINT"], fontsize=7.9,
        linewidth=1.25, radius=0.014,
    )
    add_box(
        ax, (0.355, 0.145), 0.300, 0.110,
        "CHECKPOINT\nbatched events; K = 100",
        face="#EAF5EA", edge=COLORS["CHECKPOINT"], fontsize=7.8,
        linewidth=1.25, radius=0.014,
    )
    add_box(
        ax, (0.695, 0.145), 0.235, 0.110,
        "STRICT\nper-write global chain",
        face="#FBEAEA", edge=COLORS["STRICT"], fontsize=7.7,
        linewidth=1.25, radius=0.014,
    )
    # Keep the straight connector clear of both the HMAC and evidence frames.
    add_arrow(ax, (0.735, 0.421), (0.505, 0.354))

    ax.text(
        0.50, 0.040,
        "Verification detects protected-field modification; same-domain evidence alone "
        "does not prove completeness or freshness.",
        transform=ax.transAxes,
        ha="center",
        va="center",
        fontsize=7.8,
        color="#4A4A4A",
        fontstyle="italic",
    )


def draw_scalability(ax):
    positions = np.arange(len(THREADS))
    scalability = {name: values / values[0] for name, values in MEDIAN_TPS.items()}

    for name in ("STANDARD", "FINGERPRINT", "CHECKPOINT", "STRICT"):
        ax.plot(
            positions,
            scalability[name],
            color=COLORS[name],
            marker="o",
            markersize=4.4,
            linewidth=1.8,
            label=name,
            zorder=3,
        )

    ax.axhline(1.0, color="#9A9A9A", linestyle="--", linewidth=1.0, zorder=1)
    ax.set_xticks(positions, [str(t) for t in THREADS])
    ax.set_xlim(-0.18, 5.18)
    ax.set_ylim(0.8, 6.15)
    ax.set_xlabel("Concurrent worker threads", fontsize=9.0)
    ax.set_ylabel("Scalability, TPS(T) / TPS(1)", fontsize=9.0)
    ax.tick_params(axis="both", labelsize=8.0)
    ax.grid(True, color="#C8C8C8", alpha=0.42, linewidth=0.7)
    ax.set_axisbelow(True)
    ax.set_title(
        "Measured concurrency profiles reflect\nevidence-persistence semantics",
        fontsize=10.4,
        fontweight="bold",
        pad=8,
    )
    legend = ax.legend(
        loc="upper left",
        frameon=True,
        framealpha=0.93,
        fontsize=7.4,
        borderpad=0.45,
        labelspacing=0.35,
        handlelength=2.2,
    )
    legend.get_frame().set_edgecolor("#C7C7C7")

    ax.annotate(
        "FINGERPRINT: 5.73x\nat 32 workers",
        xy=(5, scalability["FINGERPRINT"][5]),
        xytext=(3.20, 5.32),
        fontsize=7.3,
        color="#B65300",
        ha="left",
        va="center",
        arrowprops=dict(arrowstyle="->", color="#B65300", lw=1.0),
    )
    ax.annotate(
        "CHECKPOINT: peak TPS\nat 4 workers",
        xy=(2, scalability["CHECKPOINT"][2]),
        xytext=(2.65, 3.25),
        fontsize=7.2,
        color="#1E7D27",
        ha="left",
        va="center",
        arrowprops=dict(arrowstyle="->", color="#1E7D27", lw=1.0),
    )
    ax.annotate(
        "STRICT: synchronization-bound\nnear 300 TPS",
        xy=(5, scalability["STRICT"][5]),
        xytext=(3.05, 1.28),
        fontsize=7.2,
        color="#A41515",
        ha="left",
        va="center",
        arrowprops=dict(arrowstyle="->", color="#A41515", lw=1.0),
    )


def build_figure():
    mpl.rcParams.update({
        "font.family": "DejaVu Sans",
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
        "axes.unicode_minus": False,
    })
    fig = plt.figure(figsize=(13.28, 5.31), facecolor="white")
    fig.suptitle(
        "Application-layer integrity and temporal control for centralized state registries",
        x=0.5, y=0.975, fontsize=15.0, fontweight="bold",
    )
    architecture_ax = fig.add_axes([0.018, 0.080, 0.500, 0.820])
    scalability_ax = fig.add_axes([0.565, 0.145, 0.410, 0.710])
    draw_architecture(architecture_ax)
    draw_scalability(scalability_ax)
    return fig


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Directory for PNG and PDF outputs (default: script directory).",
    )
    parser.add_argument(
        "--basename",
        default="Graphical_Abstract_revised",
        help="Output filename stem.",
    )
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    fig = build_figure()
    png_path = args.output_dir / f"{args.basename}.png"
    pdf_path = args.output_dir / f"{args.basename}.pdf"
    fig.savefig(png_path, dpi=300, facecolor="white")
    fig.savefig(pdf_path, facecolor="white")
    plt.close(fig)

    print(png_path)
    print(pdf_path)


if __name__ == "__main__":
    main()
