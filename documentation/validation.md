# Initial Stage 1 validation report — 11 November 2026

This records the initial implementation. See [the integration correction report](integration-validation.md) for the current authoritative catalogue adapter, endpoints, test counts and changed files.

## Worktree and release safety

Branch: `feature/1-bsic-setup`. No commit, push, merge, PR, deployment or release was performed. `android-version.json`, `tv-version.json`, `app-config.json`, application IDs and the two release tracks are unchanged. The existing isolation/rollback guard and push-only signed-release policy remain intact. Root Angular deployment from `main-github` to `gh-pages` is unchanged.

## Executed checks

| Check | Result |
|---|---|
| `python3 scripts/validate-config.py` | PASS, using bundled Python 3.12.14 because Windows' python3 alias is not executable |
| `python3 -m unittest discover -s scripts/tests -v` | PASS: 26 tests |
| `npm ci` | PASS: fresh final lockfile install, 560 packages; Husky reports no `.git` inside the nested web project |
| `npm run lint` | PASS |
| `npm test -- --watch=false` | PASS: 45 tests, 6 files |
| `npm run build:gh` | PASS: production output in `web/vizhi-tivi/dist/vizhi-tivi/browser`, `/vizhi-tivi/` base href |
| `:app:testMobileDebugUnitTest` | PASS: 19 tests, zero failures/errors |
| `:app:testTvDebugUnitTest` | PASS: 19 tests, zero failures/errors |
| `:app:lintMobileDebug` | PASS: zero errors, 22 warnings |
| `:app:lintTvDebug` | PASS: zero errors, 21 warnings |
| `:app:assembleMobileDebug` | PASS: `app-mobile-debug.apk`, 17,710,614 bytes |
| `:app:assembleTvDebug` | PASS: `app-tv-debug.apk`, 17,710,670 bytes |
| `apksigner verify` on both debug APKs | PASS |
| Signed-build script with signing variables removed | PASS: mobile and TV each reject missing `SIGNING_KEYSTORE_PATH` with exit 1 before Gradle/release output |
| `git diff --check` | PASS |

The final combined Android run completed successfully in 48 seconds (109 actionable tasks: 45 executed, 64 up-to-date). Reproduction with the verified Gradle 9.5.0 installation and JDK 21:

```sh
gradle -p android :app:testMobileDebugUnitTest :app:testTvDebugUnitTest \
  :app:lintMobileDebug :app:lintTvDebug \
  :app:assembleMobileDebug :app:assembleTvDebug \
  --console=plain --max-workers=2 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Packaged APK badging confirms `com.actionanand.vizhitivi.app.debug` with the phone launcher and `com.actionanand.vizhitivi.tv.debug` with the Leanback launcher. Both retain versionCode 1 / versionName `1.0.0-debug`, compile API 37 and target API 36. APKs are under `android/app/build/outputs/apk/{mobile,tv}/debug/`. Remaining lint warnings concern available newer stable dependency versions, shared localized strings unused on Android, API-gated locale configuration and Compose modifier parameter ordering. They were not suppressed. The debug builds also report that two dependency-native libraries could not have debug symbols stripped and were packaged intact; this did not prevent signing/assembly.

Python temporary directories were redirected into ignored `.tools/tmp` after the sandbox's default Windows temporary directory caused permission errors. The first web test run also hit a sandbox temporary-file error; workspace-local temporary storage resolved it. An isolated subsequent test retry encountered a worker-start timeout during heavy concurrent compilation. The full suite then passed; Vitest is now capped at two workers. Local Gradle was restarted with two workers and bounded heap to reduce contention. These failures were not hidden or treated as passing tests.

Web tests cover schema compatibility and structural validation, malformed records, stable ID/number reconciliation, Tamil exhaustion/gap reuse, non-Tamil numbering, collisions, M3U parsing/deduplication, MIME hints, multi-language detection, shared identity hashes, reimport, favourite/locale/recent persistence, quota errors, last-known-good cache preservation, official-number priority, unsupported playback, autoplay rejection, player switching/cleanup, navigation/filter behavior, and keyboard focus returning to the recreated channel button after player close. Native JVM tests use the same catalogue/M3U fixtures for domain and decoder behavior. No native instrumentation or physical player/focus lifecycle test is claimed.

## Android CI investigation

All eight failed Actions runs available at audit time were reviewed. Seven Android validation runs failed at **Install exact SDK packages**, before compilation:

| Run | Failure |
|---|---|
| [43021850641](https://github.com/actionanand/vizhi-tivi/actions/runs/43021850641) | `Failed to find package 'platforms;android-37'` |
| [43017637926](https://github.com/actionanand/vizhi-tivi/actions/runs/43017637926) | Same package mismatch |
| [43015961193](https://github.com/actionanand/vizhi-tivi/actions/runs/43015961193) | Same package mismatch |
| [43015096103](https://github.com/actionanand/vizhi-tivi/actions/runs/43015096103) | Same package mismatch |
| [43015013784](https://github.com/actionanand/vizhi-tivi/actions/runs/43015013784) | Same package mismatch |
| [42989327305](https://github.com/actionanand/vizhi-tivi/actions/runs/42989327305) | Same package mismatch |
| [42921117963](https://github.com/actionanand/vizhi-tivi/actions/runs/42921117963) | Same package mismatch |

The live Google SDK repository advertises `android-37.0`, `37.1`, `37.2`, and API 36 packages, with no package named `android-37`. The fix retains compile API 37, target API 36, min API 26 and Build Tools 36.0.0, while pinning `COMPILE_SDK_PACKAGE=37.0` and `COMPILE_SDK_MINOR=0`. Both Android workflows install that exact package and log `sdkmanager --list`; Gradle uses the minor-aware `release(37) { minorApiLevel = 0 }` DSL. Local installation of `platforms;android-37.0` succeeded.

[AGP 9.3 compatibility documentation](https://developer.android.com/build/releases/agp-9-3-0-release-notes) confirms Gradle 9.5.0 and Build Tools 36.0.0. The retained AGP 9.3.1, explicit Kotlin/Compose compiler 2.4.20 line, Gradle 9.5.0 and JDK 21 were used locally; JVM bytecode remains Java 17. No redundant legacy Kotlin Android plugin was introduced. The [built-in Kotlin migration documentation](https://developer.android.com/build/migrate-to-built-in-kotlin) informed this review.

The first local Gradle configuration also exposed the skeleton's `resValue` declaration with AGP 9's `resValues` feature disabled. `buildFeatures.resValues = true` fixes that configuration failure while preserving `app_name`. `buildConfig` is enabled to select flavor-specific UI. No SDK/Gradle failure was suppressed and no validation task was removed.

The first full mobile lint run found `MissingLeanbackLauncher`: the shared manifest advertised TV hardware while the mobile flavor correctly used the phone launcher. The fix moves Leanback hardware and the TV banner into `src/tv/AndroidManifest.xml`, keeping one module/source base. TV lint examines flavor manifests independently, so launcher categories, exported activity attributes and optional touchscreen support are explicit in those manifests rather than relying on placeholder/manifest merging for detection. Lint also identified bundle language splitting, which would undermine the in-app locale selector; all three language resources are now bundled together. Explicit Android 12+ extraction rules exclude private records from cloud backup/device transfer, complementing the existing backup-disabled flags. No lint baseline or disabled checks were added.

The eighth failure, [43002891328](https://github.com/actionanand/vizhi-tivi/actions/runs/43002891328), built Angular successfully but its deployment check annotations said `main-github` was not permitted by the `github-pages` environment protection rules. Later Pages runs were successful. The current branch-to-`gh-pages` deployment workflow and repository protection settings were left unchanged. No remote CI rerun was triggered, so this report does not claim the feature branch's remote CI is green.

The old `web-validation.yml` silently skipped the actual nested Angular project. It now runs its install, lint, tests and Pages-compatible build, including on feature pushes.

## Dependencies

Added native dependencies (published POMs verified before use):

- Lifecycle ViewModel Compose / runtime Compose 2.10.0.
- DataStore Preferences 1.2.0.
- Compose for TV Material 1.0.1.
- Media3 ExoPlayer, HLS, DASH, UI 1.9.3.
- Coil Compose / OkHttp networking 3.3.0 for channel logos with existing artwork fallback.
- `org.json:json:20240303` only for JVM decoder tests; Android uses the platform JSON implementation.

Web adds pinned `hls.js` 1.7.4 and dev-only AJV 8.20.0. AJV was moved to the compatible patched release after the audit identified an older-version advisory. Existing Angular 22 and Android toolchain/UI dependencies were preserved. Final `npm audit`: 10 moderate findings in the existing ESLint/TypeScript-ESLint/minimatch development tooling graph; zero high/critical findings. Suggested automatic fixes require changing the lint-toolchain major versions, so no forced unrelated downgrade/migration was performed. The audit is reported separately from passing lint/tests/build.

## Remaining acceptance checks and deliberate boundaries

`adb devices` found no connected device/emulator. Actual permitted HLS/DASH/progressive playback, native DataStore restart/instrumentation checks, codec/audio/subtitle behavior, background/audio-focus transitions, 1080p TV D-pad/focus and narrow-phone visual checks remain unverified. Browser preview was attempted, but the in-app webview initially failed to attach; computer use then stopped because it could not confidently determine the Windows browser URL. After resuming, an in-app browser retry could not reach the local preview server (`ERR_CONNECTION_TIMED_OUT`), including after restarting that server. No visual layout, screenshot, real-browser AXE or real-stream playback success is claimed. The responsive CSS and TV layout are implemented, but require those checks before release.

The shipped catalogue has metadata only. No production catalogue endpoint or approved broadcasts have been invented. No private playlist, credential or signing material was added. The MVP supports foreground playback; web DASH, candidate health/failover, EPG, large relational storage, background audio, immersive-system-bar controls, Dropbox transport/OAuth and backup UI/adapters are deferred as described in `stage-1.md`. Existing artwork remains provisional. Stage 1 is not declared production-ready solely from build results.

## File-by-file change inventory

The inventory below describes the final working-tree changes; generated caches, downloaded local tools and build outputs are ignored.

| File | Change |
|---|---|
| `.github/workflows/android-release.yml` | Exact API 37.0 SDK package installation and SDK diagnostics; release isolation and signing preserved. |
| `.github/workflows/android-validation.yml` | Exact SDK package installation and diagnostics for both debug flavors. |
| `.github/workflows/web-validation.yml` | Validate the real nested Angular app on PRs and feature pushes. |
| `.gitignore` | Exclude local tools and Kotlin compiler caches. |
| `README.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `android-sdk.properties` | Separate compile API, minor API and SDK package identity. |
| `android/app/build.gradle.kts` | Minor-aware SDK DSL, resValues/BuildConfig, shared fixtures and native dependencies. |
| `android/app/src/main/AndroidManifest.xml` | Common application/activity configuration and explicit private backup rules. |
| `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt` | Lifecycle-aware ViewModel state, persisted locale context and flavor-specific layout selection. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt` | Strict versioned catalogue decoder and structured channel encoder. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt` | Shared channel/stream models, stable allocator, HTTPS validation, M3U parser and future private transport boundary. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt` | Serialized background operations and StateFlow for catalogue, imports, settings and history. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt` | Bounded HTTPS reads, atomic catalogue/private stores, DataStore preferences and document reads. |
| `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt` | Mobile browsing and TV rails/remote controls, import/settings dialogs, filters and focus restoration. |
| `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt` | Single foreground Media3 owner, audio focus, controls, errors/retry and lifecycle cleanup. |
| `android/app/src/main/res/values-sa/strings.xml` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `android/app/src/main/res/values-ta/strings.xml` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `android/app/src/main/res/values/strings.xml` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `android/app/src/main/res/xml/data_extraction_rules.xml` | Exclude private device records from Android 12+ backup and transfer. |
| `android/app/src/mobile/AndroidManifest.xml` | Explicit phone launcher for the mobile flavor. |
| `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt` | 19 deterministic domain/decoder/parser tests executed for each flavor. |
| `android/app/src/tv/AndroidManifest.xml` | TV launcher, Leanback hardware, optional touch input and banner. |
| `contracts/README.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `contracts/backup-v1.schema.json` | Future portable private backup contract; no enabled transport. |
| `contracts/catalogue-v1.schema.json` | Machine-readable official catalogue v1 contract. |
| `contracts/fixtures/catalogue-v1.json` | Explicit metadata-only development catalogue. |
| `contracts/fixtures/playlist-vectors.json` | Common parser expectations and SHA-256 identities. |
| `contracts/fixtures/playlist.m3u` | Synthetic M3U input with reserved .invalid URLs. |
| `documentation/README.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `documentation/branding.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `documentation/limitations.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `documentation/setup.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `documentation/stage-1.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `documentation/validation.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `scripts/tests/test_stage1.py` | SDK/workflow/localization/shared-fixture regressions. |
| `web/README.md` | Updated setup, architecture, contract, scope or validation documentation. |
| `web/vizhi-tivi/angular.json` | Bounded Vitest runner configuration; production build layout preserved. |
| `web/vizhi-tivi/package-lock.json` | Locked final web dependency graph. |
| `web/vizhi-tivi/package.json` | Pinned hls.js runtime and AJV test dependency. |
| `web/vizhi-tivi/public/app-icon.png` | Copy of the common source-of-truth icon for the web header/fallback. |
| `web/vizhi-tivi/public/catalogue-v1.json` | Byte-identical Angular copy of the shared development fixture. |
| `web/vizhi-tivi/src/app/app.routes.ts` | Lazy-load the Live TV shell. |
| `web/vizhi-tivi/src/app/app.spec.ts` | Replace stale starter assertion with root outlet verification. |
| `web/vizhi-tivi/src/app/app.ts` | Small router-outlet application root. |
| `web/vizhi-tivi/src/app/core/domain.spec.ts` | 20 shared-domain/numbering/parser parity tests. |
| `web/vizhi-tivi/src/app/core/domain.ts` | TypeScript catalogue decoder, stable numbering, M3U parser and private transport interface. |
| `web/vizhi-tivi/src/app/core/schema.spec.ts` | Six JSON Schema acceptance/rejection tests. |
| `web/vizhi-tivi/src/app/core/store.spec.ts` | Ten persistence, reimport, quota and last-known-good-cache tests. |
| `web/vizhi-tivi/src/app/core/store.ts` | Private local state, cache/refresh/import transactions and localized text. |
| `web/vizhi-tivi/src/app/locales/en.json` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `web/vizhi-tivi/src/app/locales/sa.json` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `web/vizhi-tivi/src/app/locales/ta.json` | Localized interface resources for English, Tamil or Sanskrit, preserving exact taglines. |
| `web/vizhi-tivi/src/app/player.spec.ts` | Four player lifecycle/switching/autoplay/unsupported-format tests. |
| `web/vizhi-tivi/src/app/player.ts` | Native/HLS playback, track controls, errors, autoplay handling and teardown. |
| `web/vizhi-tivi/src/app/shell.html` | Accessible Live TV, favourites, private imports, settings and channel cards. |
| `web/vizhi-tivi/src/app/shell.scss` | Responsive layouts, restrained brand palette, focus indicators and theme modes. |
| `web/vizhi-tivi/src/app/shell.spec.ts` | Three navigation/filter/playback-eligibility and focus-restoration checks. |
| `web/vizhi-tivi/src/app/shell.ts` | Signal-derived browsing, navigation, import UI handlers and player selection. |
| `web/vizhi-tivi/src/index.html` | Exact application title and common brand icon as favicon. |
| `web/vizhi-tivi/src/styles.scss` | Minimal global viewport and form-control defaults. |
| `web/vizhi-tivi/tsconfig.json` | Strict TypeScript/templates and JSON resource imports. |
| `web/vizhi-tivi/vitest-base.config.mts` | Cap test workers to two for reliable local/CI resource use. |

Working-tree inventory: 62 changed/new files. All changes are unstaged; no release version file is in this inventory.
