# Vizhi TiVi

**உலகம் உங்கள் விழிகளில்!** · **The world before your eyes.** · **विश्वं तव नयनयोः पुरतः**

Monorepo starter: one native Kotlin/Jetpack Compose Android project for phones and televisions, a reserved `web/` folder for Angular, and common brand assets.

## Layout

```text
vizhi-tivi/
├── android/                 # Kotlin + Compose, one app module, mobile + TV flavors
├── web/                     # Your own Angular starter goes here
├── common/branding/         # Source-of-truth brand assets, outside both apps
├── contracts/               # Shared JSON contracts (future)
├── scripts/                 # Bootstrap, version bump, signing, checks
├── documentation/           # Setup, release process, branding, limitations
├── .github/workflows/       # PR validation + signed mobile/TV CI
├── app-config.json          # Identity, locales, planned data-repo URL
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
# Generate the web app only if needed:
bash scripts/init-web.sh
```

See [`documentation/setup.md`](documentation/setup.md) and [`documentation/release-process.md`](documentation/release-process.md).

## GitHub release branches

- Push/merge/FF into `main-android` → bump `android-version.json` and build signed **mobile** APK+AAB.
- Push/merge/FF into `main-tv` → bump `tv-version.json` and build signed **TV** APK+AAB.
- `master` and feature PRs run validation without bumping versions.

The two flavors share source code but have distinct application IDs so independent version codes are safe. **No existing GitHub repository or secret is modified by this archive.**

## Scope

This archive is a skeleton, not a working IPTV player. See [`documentation/limitations.md`](documentation/limitations.md).
