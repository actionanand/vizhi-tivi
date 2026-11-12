# Automated versions, signing and release pipeline

Reference design based on LocalTell's root `android-version.json`, `android-sdk.properties`, signing secrets and Android workflow, with two independent build targets.

## Version files

| Git ref | Flavor | Version source | Artifact name | Application ID |
|---|---|---|---|---|
| `main-android` | `mobile` | `android-version.json` | `vizhi-tivi-mobile-vX.Y.Z-bN.apk` | `com.actionanand.vizhitivi.app` |
| `main-tv` | `tv` | `tv-version.json` | `vizhi-tivi-tv-vX.Y.Z-bN.apk` | `com.actionanand.vizhitivi.tv` |

Both `versionCode` and the patch component of `versionName` increase by 1 **per successful branch build**. `--minor` and `--major` can be used locally. Version files are committed to the appropriate release branch only after the signed APK and AAB have passed verification.

Distinct app IDs are chosen because two independently incremented version tracks cannot safely publish as one Play Store package. A single codebase and app module are retained. If you require one Play Store package instead, first redesign this into one globally monotonically increasing version counter.

## Required Actions secrets

Set in `Settings → Secrets and variables → Actions`:

- `KEYSTORE_BASE64`: Base64 content of one keystore file; no newlines preferred.
- `KEYSTORE_PASSWORD`: password used when generating keystore.
- `KEY_ALIAS`: e.g. `vizhitivi`.
- `KEY_PASSWORD`: optional if identical to the keystore password (PKCS12 default).

The workflow **fails** if required credentials are absent; it will never mislabel an unsigned artifact as signed.

To generate on a trusted computer:

```bash
bash scripts/generate-keystore.sh
base64 -w0 release-keystore.jks > keystore.b64
# Use the whole output of keystore.b64 as KEYSTORE_BASE64. Never commit the file.
```

Securely back up the keystore; losing it can prevent updates for your existing signing identity. For Google Play App Signing, preserve the upload key appropriately.

## CI sequence

1. Push/merge/FF to one release branch.
2. GitHub Actions validates version/config and builds/tests the matching **debug** flavor.
3. Bump the matching branch's version file in the working tree.
4. Decode keystore outside the repository and sign matching **APK + AAB** with Gradle.
5. Verify APK signature via `apksigner`, AAB via `jarsigner`, and collect artifacts.
6. Commit the version-file update `[skip ci]` and push it to that release branch.
7. Upload signed build artifacts with 14-day Actions retention; signing material is deleted.

No GitHub Release or Play Store upload is automatically created by this starter. This is intentionally only an app build/version pipeline, independent from `vizhi-tivi-data` catalogue releases. Existing GitHub email notifications can report workflow completion based on your account settings.

## Concurrency and permissions

Actions runs serialize per branch; the workflow checks out the exact push SHA. A normal final push fails if the branch has moved. Automatic commits require `contents: write` and branch protection/ruleset permission for `github-actions[bot]`. If branch protection blocks the push, adapt your policy to permit this bot or have the workflow create a PR instead. Do not disable essential protections solely for this.

## Manual local version change

```bash
python3 scripts/bump-version.py --platform mobile --patch
python3 scripts/bump-version.py --platform tv --minor
```

Local development should not bump version numbers every time you build. CI only bumps on the release branches.
