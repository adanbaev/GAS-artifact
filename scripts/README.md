# Scripts

- `analyze_v1_3.py` validates the four raw datasets and recomputes Tables 3-6
  plus K-sensitivity.
- `grafik3_revised.py` through `grafik6_revised.py` regenerate the approved
  performance plots from the table medians.
- `graphical_abstract_final.py` regenerates the final graphical abstract.

Install dependencies from the repository root:

```bash
python -m pip install -r requirements.txt
```

Run statistical analysis from the repository root as shown in the main
`README.md`.  The original plotting scripts write their outputs beside the
script; compare those files with the corresponding files under `figures/`.
