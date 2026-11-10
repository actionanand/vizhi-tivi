# AI agent instructions — Vizhi TiVi

1. Preserve the exact branding: `Vizhi TiVi`, `vizhi-tivi`, and taglines in `documentation/branding.md`.
2. Kotlin Android mobile + Google TV/Android TV share one `android/app` source base; no duplicated mobile/TV projects.
3. Android mobile and TV are separate Gradle **product flavors** with their own app IDs and version files. Avoid altering their independent versioning contract without explicit approval.
4. Web belongs inside `web/`, while reusable brand source files belong under `common/branding/`.
5. Interface languages: `en`, `ta`, `sa`; provide localized UI strings, fall back to English, preserve channel names.
6. Tamil numbering is 1–250, all other languages start at 251; use persistent channel IDs and stable assignments, recycle only genuinely vacated numbers. Imported IPTV stays private and appears under Imported TV.
7. Do not add movie/series/VOD sections. Live TV only; no DRM bypass, unauthorized stream publication, or playlist secrets committed to Git.
8. Official catalogue comes from a separate planned `vizhi-tivi-data` repo. Never hardcode private imported IPTV URLs or user credentials.
9. Never commit signing keys/tokens. Validate that missing secrets fail signing rather than publishing unsigned artifacts with signed names.
10. On `main-android`, bump only `android-version.json`. On `main-tv`, bump only `tv-version.json`. Do not bump on feature or master pushes.
11. Add/adjust deterministic tests for new scripts and run `python3 -m unittest discover -s scripts/tests -v` and `python3 scripts/validate-config.py`.
12. Treat this repository as an initial skeleton. Do not describe placeholder screens/artwork as feature-complete. Report tests honestly.
