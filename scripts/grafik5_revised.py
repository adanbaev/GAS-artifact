"""Generate revised manuscript Figure 5 as PDF and high-resolution PNG.

The plotted medians are the approved concurrent benchmark values reported in
Table 5. Thread counts are displayed as equally spaced experimental categories.
"""

import os
import tempfile
from pathlib import Path

os.environ.setdefault(
    "MPLCONFIGDIR", str(Path(tempfile.gettempdir()) / "gas_artifact_matplotlib")
)

import matplotlib.pyplot as plt


OUTPUT_DIR = Path(__file__).resolve().parent
PDF_OUTPUT = OUTPUT_DIR / "Figure_5.pdf"
PNG_OUTPUT = OUTPUT_DIR / "Figure_5.png"
FIGSIZE = (7.2, 4.8)
PNG_DPI = 1000

THREADS = [1, 2, 4, 8, 16, 32]
X = list(range(len(THREADS)))
THREAD_LABELS = [str(value) for value in THREADS]

# Approved median aggregate TPS values from Table 5.
SERIES = {
    "STANDARD": [480.0, 917.7, 1257.5, 1338.0, 1266.9, 1253.5],
    "FINGERPRINT": [162.9, 297.9, 470.4, 671.0, 685.8, 934.2],
    "CHECKPOINT (K=100)": [272.2, 476.6, 686.6, 644.5, 521.2, 596.5],
    "STRICT": [145.5, 272.5, 303.1, 309.1, 297.7, 265.9],
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

ax.set_xticks(X)
ax.set_xticklabels(THREAD_LABELS)
ax.set_xlabel("Worker threads")
ax.set_ylabel("Median aggregate throughput (TPS)")
ax.set_ylim(0, 1450)
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
    metadata={"Title": "Figure 5", "Creator": "Matplotlib", "CreationDate": None},
    **common_options,
)
fig.savefig(
    PNG_OUTPUT,
    format="png",
    dpi=PNG_DPI,
    metadata={"Title": "Figure 5", "Software": "Matplotlib"},
    **common_options,
)
plt.close(fig)

print(PDF_OUTPUT)
print(PNG_OUTPUT)
