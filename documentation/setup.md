# Vizhi TiVi setup

## Prerequisites

- Java 21 and Android SDK with the exact package from `android-sdk.properties` (`platforms;android-37.0`, Build Tools 36.0.0, platform-tools). API 37's minor version must be present in both SDK package and Gradle DSL.
- Gradle 9.5.0, or generate a Gradle wrapper once using `bash scripts/bootstrap.sh`.
- Python 3.10+ for validation and version tools.
- Node.js/npm only if building Angular in `web/`.

## Initialize

```bash
bash scripts/bootstrap.sh
python3 scripts/validate-config.py
# from repository root; after creating an Android wrapper:
cd android
./gradlew :app:lintMobileDebug :app:lintTvDebug :app:testMobileDebugUnitTest :app:testTvDebugUnitTest :app:assembleMobileDebug :app:assembleTvDebug
```

If there is no Gradle executable installed, install Gradle 9.5.0 first or let Android Studio create a wrapper. GitHub Actions uses `gradle/actions/setup-gradle` and does not need a checked-in wrapper.

## Web

The existing Angular 22 project is `web/vizhi-tivi/`; do not reinitialize it. Use Node matching its `.nvmrc`, then run `npm ci`, `npm run lint`, `npm test -- --watch=false`, `npm run build:gh`. `npm start` runs the development server. Production assets resolve relative to `/vizhi-tivi/`. Private playlist URL fetches require provider CORS permission; local file import needs no proxy.

Install APKs from `android/app/build/outputs/apk/mobile/debug/` and `android/app/build/outputs/apk/tv/debug/` using Android Studio or `adb install -r <apk>`. Launch the appropriate app from the phone or TV launcher. Debug apps have `.debug` appended to their application IDs. Mobile and TV can be installed together. For a CLI without a wrapper, substitute `gradle -p android` from repository root for `./gradlew` from `android/`.

Clients use the official Vizhi TiVi Data manifest by default; Settings accepts a full HTTPS manifest URL for a test mirror. Imported TV accepts your permitted private M3U. The current official publication is empty; local development fixtures are test-only. See [Stage 1 behavior](stage-1.md) and [executed validation](validation.md).

## Branches

- `master`: integration source.
- `feature/**`: development/PR validation.
- `main-android`: **mobile** signed APK/AAB and only `android-version.json` bump.
- `main-tv`: **TV** signed APK/AAB and only `tv-version.json` bump.
- `main-github`: existing compiled Angular deployment to `gh-pages`, preserving `/vizhi-tivi/`.

Pushes, merged PRs and fast-forward updates appear as a `push` event on the destination branch. A push to `master` does **not** bump any Android or TV version.

## Fast-forward caution

Because Actions commits a version bump to a release branch, `master` may not be able to fast-forward into that branch later. Merge the version commits back into your integration branch, or use an intentional merge from `master` to each release branch. Do not force-push or reset release branches simply to maintain fast-forward-only workflows. This is an inherent consequence of branch-local committed version increments.

## Enforced release version isolation

- Only remote pushes to `main-android` and `main-tv` start the signed release workflow.
- Manual workflow dispatch has been removed. `master`, `feature/**`, and
  `main-github` do not trigger automatic version bumps.
- Pushes include merged PRs and fast-forward updates once published remotely.
- On `main-android`, CI may increment only `android-version.json`.
- On `main-tv`, CI may increment only `tv-version.json`.
- Before building, CI compares the incoming commit with GitHub's `push.before`
  commit. The target's `versionCode` must still equal its previous release
  value; the other platform's complete version metadata must not change.
- Target `versionName` must not decrease; a forward major/minor update
  is possible without manually incrementing `versionCode`.
- If merging overwrites either version file, preserve the destination
  release branch metadata and retry the merge. A regression fails CI before
  signing or committing any new version. Do not force-push release branches.
- The workflow builds the exact incoming push SHA. Its final version commit
  uses a normal non-force push and fails safely if the branch moved.
