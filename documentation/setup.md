# Vizhi TiVi setup

## Prerequisites

- Java 21 and Android SDK with the versions from `android-sdk.properties`.
- Gradle 9.5.0, or generate a Gradle wrapper once using `bash scripts/bootstrap.sh`.
- Python 3.10+ for validation and version tools.
- Node.js/npm only if building Angular in `web/`.

## Initialize

```bash
bash scripts/bootstrap.sh
python3 scripts/validate-config.py
# from repository root; after creating an Android wrapper:
cd android
./gradlew :app:assembleMobileDebug :app:assembleTvDebug
```

If there is no Gradle executable installed, install Gradle 9.5.0 first or let Android Studio create a wrapper. GitHub Actions uses `gradle/actions/setup-gradle` and does not need a checked-in wrapper.

## Web

`web/` is intentionally not generated. You can paste your own Angular starter, or run
`bash scripts/init-web.sh` once. The script refuses to overwrite existing Angular files.

## Branches

- `master`: integration source.
- `feature/**`: development/PR validation.
- `main-android`: **mobile** signed APK/AAB and only `android-version.json` bump.
- `main-tv`: **TV** signed APK/AAB and only `tv-version.json` bump.
- `main-github`: optional future Angular deployment branch; not configured in this starter.

Pushes, merged PRs and fast-forward updates appear as a `push` event on the destination branch. A push to `master` does **not** bump any Android or TV version.

## Fast-forward caution

Because Actions commits a version bump to a release branch, `master` may not be able to fast-forward into that branch later. Merge the version commits back into your integration branch, or use an intentional merge from `master` to each release branch. Do not force-push or reset release branches simply to maintain fast-forward-only workflows. This is an inherent consequence of branch-local committed version increments.
