# Shared Vizhi TiVi branding

Keep the source-of-truth artwork here, outside `android/` and `web/`.

- `icon/`: launcher/app icons, SVG masters, transparent PNGs.
- `tv-banner/`: Google TV / Android TV store and launcher banner assets (TV launcher banner: 320 × 180 or platform-required dimensions).
- `feature-graphic/`: Google Play feature graphic (1024 × 500).
- `wordmark/`: wordmarks/tagline compositions in English, Tamil and Sanskrit.

Current PNGs are **temporary build placeholders**, not finished artwork.
Run `python3 scripts/sync-branding.py` after replacing `common/branding/icon/app-icon.png`
and `common/branding/tv-banner/tv-banner.png` to copy them to the Android resources.
Do not add font files, signing keys or licensed television imagery without permission.
