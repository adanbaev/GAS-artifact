"""Generate revised manuscript Figure 6 as PDF and high-resolution PNG.

Scalability is computed deterministically from the approved median aggregate
TPS values in Table 5 as TPS(T) / TPS(1) for each benchmark mode.
"""

import os
import tempfile
from pathlib import Path

os.environ.setdefault(
    "MPLCONFIGDIR", str(Path(tempfile.gettempdir()) / "gas_artifact_matplotlib")
)

import matplotlib.pyplot as plt


OUTPUT_DIR = Path(__file__).resolve().parent
PDF_OUTPUT = OUTPUT_DIR / "Figure_6.pdf"
PNG_OUTPUT = OUTPUT_DIR / "Figure_6.png"
FIGSIZE = (7.2, 4.8)
PNG_DPI = 1000

THREADS = [1, 2, 4, 8, 16, 32]
X = list(range(len(THREADS)))
THREAD_LABELS = [str(value) for value in THREADS]

# Approved median aggregate TPS values from Table 5.
TPS = {
    "STANDARD": [480.0, 917.7, 1257.5, 1338.0, 1266.9, 1253.5],
    "FINGERPRINT": [162.9, 297.9, 470.4, 671.0, 685.8, 934.2],
    "CHECKPOINT (K=100)": [272.2, 476.6, 686.6, 644.5, 521.2, 596.5],
    "STRICT": [145.5, 272.5, 303.1, 309.1, 297.7, 265.9],
}

SERIES = {
    label: [value / values[0] for value in values] for label, values in TPS.items()
}

STYLE = {
    "STANDARD": {"color": "#1F77B4", "marker": "o", "linestyle": "-"},
    "FINGERPRINT": {"color": "#FF7F0E", "marker": "o", "linestyle": "-"},
    "CHECKPOINT (K=100)": {
        "color": "#2CA02C",
        "marker": "o",
        "linestyle": "-",
    },
    "STRICT": {"color": "#D62728", "marker": "o", "linestyle": "-"},
}

plt.rcParams.update(
    {
        "font.family": "DejaVu Sans",
        "font.size": 10.5,
        "axes.labelsize": 11.5,
        "legend.fontsize": 9.5,
        "xtick.labelsize": 9.5,
        "ytick.labelsize": 9.5,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    }
)

fig, ax = plt.subplots(figsize=FIGSIZE)

for label, values in SERIES.items():
    ax.plot(
        X,
        values,
        label=label,
        linewidth=2.0,
        markersize=6.0,
        markeredgewidth=0.8,
        **STYLE[label],
    )

ax.axhline(1.0, color="#8C8C8C", linewidth=1.2, linestyle="--", zorder=0)
ax.set_xticks(X)
ax.set_xticklabels(THREAD_LABELS)
ax.set_xlabel("Worker threads")
ax.set_ylabel("Scalability, TPS(T) / TPS(1)")
ax.set_ylim(0, 6.2)
ax.grid(axis="y", color="#D9D9D9", linewidth=0.7)
ax.set_axisbelow(True)

ax.legend(
    loc="upper left",
    bbox_to_anchor=(1.01, 1.0),
    ncol=1,
    frameon=True,
    borderaxespad=0,
    handlelength=2.4,
)

fig.subplots_adjust(left=0.14, right=0.72, bottom=0.16, top=0.97)
common_options = {"bbox_inches": "tight", "pad_inches": 0.05}
fig.savefig(
    PDF_OUTPUT,
    format="pdf",
    metadata={"Title": "Figure 6", "Creator": "Matplotlib", "CreationDate": None},
    **common_options,
)
fig.savefig(
    PNG_OUTPUT,
    format="png",
    dpi=PNG_DPI,
    metadata={"Title": "Figure 6", "Software": "Matplotlib"},
    **common_options,
)
plt.close(fig)

print(PDF_OUTPUT)
print(PNG_OUTPUT)
