# Report

The LaTeX source for the project report lives here.

## Status

Waiting for the official school template. **Do not** build out a full LaTeX
structure yet — it will be replaced wholesale when the template arrives.

## Planned layout (once the template is in)

```
report/
├── main.tex          # document root, \input{}s the sections
├── sections/         # one .tex per chapter (intro, design, implementation, …)
├── figures/          # exported diagrams / screenshots referenced by \includegraphics
└── references.bib    # BibTeX bibliography
```

## Notes for whoever plugs in the template

- Screenshots / architecture diagrams can be exported from the app and Figma
  into `figures/`.
- Keep the dev plan (`updated_study_group_finder_dev_plan-1.md` at repo root)
  as the reference for section 3 (data model) and section 4 (security rules).
- Known limitations to mention in the report are already flagged in the dev
  plan (no `collectionGroup` queries, no stored `completed` status, host-only
  material upload is UI-enforced only).
