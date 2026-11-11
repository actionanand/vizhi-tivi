# Vizhi TiVi

**உலகம் உங்கள் விழிகளில்!** · **The world before your eyes.** · **विश्वं तव नयनयोः पुरतः**

Stage 1 Live TV client foundation: one native Kotlin/Jetpack Compose Android project for phones and televisions, an Angular client in `web/vizhi-tivi/`, and common brand assets. Official metadata is loaded from the versioned Vizhi TiVi Data publication; development fixtures are test-only and never shown as real channels.

## Layout

```text
vizhi-tivi/
├── android/                 # Kotlin + Compose, one app module, mobile + TV flavors
├── web/vizhi-tivi/          # Standalone Angular Live TV client
├── common/branding/         # Source-of-truth brand assets, outside both apps
├── contracts/               # Versioned catalogue/backup schemas and shared fixtures
├── scripts/                 # Bootstrap, version bump, signing, checks
├── documentation/           # Setup, release process, branding, limitations
├── .github/workflows/       # PR validation + signed mobile/TV CI
├── app-config.json          # Identity, UI locales, official manifest URL
├── android-version.json     # main-android version
├── tv-version.json          # main-tv version
├── android-sdk.properties   # Consistent SDK configuration
└── AGENTS.md / agent.md     # Agent instructions
```

## Quick start

```bash
bash scripts/bootstrap.sh
python3 scripts/validate-config.py
python3 -m unittest discover -s scripts/tests -v
# Existing Angular client:
cd web/vizhi-tivi
npm ci
npm run lint
npm test -- --watch=false
npm run build:gh
```

See [`documentation/setup.md`](documentation/setup.md) and [`documentation/release-process.md`](documentation/release-process.md).

## GitHub release branches

- Push/merge/FF into `main-android` → bump `android-version.json` and build signed **mobile** APK+AAB.
- Push/merge/FF into `main-tv` → bump `tv-version.json` and build signed **TV** APK+AAB.
- `master` and feature PRs run validation without bumping versions.

The two flavors share source code but have distinct application IDs so independent version codes are safe. **No existing GitHub repository or secret is modified by this archive.**

## Scope

Browse, search, filter, favourite and import private HTTPS M3U playlists. Both native flavors share data, preferences and Media3 playback; TV uses Compose for TV buttons and content rails. Web uses native media or hls.js where supported. The default manifest is `https://actionanand.github.io/vizhi-tivi-data/manifest.json`; Settings preserves a HTTPS manifest override for testing. The current data publication is empty, so the clients show an honest empty state. A failed request retains last-known-good metadata and private imports.

See [Stage 1 architecture, behavior and limitations](documentation/stage-1.md) and [current integration validation report](documentation/integration-validation.md). Physical phone/TV playback, 1080p remote navigation and browser visual checks remain acceptance gates; builds alone do not establish device readiness.
