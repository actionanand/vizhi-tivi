## Final Stage 1A source acceptance — 2026-11-13

**Stage 1A Source Acceptance:** GO for independent source review and continued development. The focused review rechecked the 13-finding matrix, representative implementation and regression evidence, including the two-flavor emulator results below. No application source changed after those checks. The evidence applies to the uncommitted working tree based on Git HEAD `5e2fcf1cf1bde8ca61ef77e799d4442e50af396f`, not to that commit alone.

**Source blockers remaining:** None identified in this focused review. The prior offline Gradle connected-task dependency was unavailable, but both test APKs executed successfully through `adb` on the emulator as detailed below; this is not a production workflow verification.

**Production Release:** NO-GO.

**Release verification pending:** Physical Android mobile and Google TV/Android TV, D-pad and Back, permitted real HLS/DASH/progressive playback, audio and subtitles, physical power-loss recovery, and actual production signing and release workflow. Accessibility and publisher workflow acceptance also remain to be checked. Emulator tests used synthetic streams and a generic Android image with TV display sizing.

**Next development stage:** TV catalogue data integration, with rights review before publication.

# Stage 1A — Android platform verification continuation

**Source acceptance: ready for independent source re-audit. Stage 1A acceptance and production release: NO-GO pending the remaining acceptance gates.** The source, JVM and emulator evidence below support reviewing the corrections; they do not prove unexecuted physical-hardware, media, or release-signing behavior. This section supersedes the earlier verdict and validation snapshot; subsequent sections preserve the prior investigation as history.

## Android platform coverage

Added `android/app/src/androidTest/java/com/actionanand/vizhitivi/PlatformInstrumentation.kt` and `PlatformChecks.kt`, wired through `android/app/build.gradle.kts`, using a small framework instrumentation runner without an additional test library. The synthetic fixture assets are bundled into each test APK. The four executable checks exercise actual `AtomicFile` successful writes, interrupted `.new` recovery and injected commit failures, recovery snapshot inventory, concurrent repository writes, loopback network deadlines and cancellation, and real activity startup with a synthetic official cache and corrupt private data. The startup scenario checks recovery/export, preserved IDs, numbers, language override/source and favourites, lifecycle recreation, three locale startups, and simulated accepted publication replacement/removal/empty and Back cleanup. It uses reflection to simulate accepted catalogue state; it does not exercise the full network publication path or decode media. Local synthetic URLs and app-process log scanning are used; no real IPTV credentials are required. Startup refuses to overwrite existing app data, so use a disposable installation. Failure output suppresses exception messages that could contain private URLs.

**Android API 35 emulator: PASS, four checks per flavor**, run using `adb shell am instrument -w` on a fresh isolated emulator. `atomicFileAndSnapshots`, `repositoryConcurrency`, `loopbackDeadlineAndCancellation` and `startupRecoveryAndLocales` all returned status 0 for mobile and TV. The TV flavor used a 1920x1080 virtual display on a generic Google APIs x86_64 image, not Android TV hardware or a TV-specific image. The official image matched Google's published size and SHA-1. The connected Gradle task was attempted but failed before execution because the offline cache lacked `com.android.tools.utp:android-test-plugin-host-additional-test-output:32.3.1`; `adb` executed the existing APKs without adding that dependency. Initial runs passed three checks and timed out finding Play using `findAccessibilityNodeInfosByText`; a synthetic-screen inspection showed Play visible. Changing the test to traverse accessibility nodes directly fixed that test lookup, after which both full four-check runs passed.

Actual framework results on this emulator: `AtomicFile` reopened the original bytes after an incomplete `.new`; failure injected before finalization retained the previous valid store without a rollback write, while failure injected after finalization left the complete new store and the prior recovery snapshot. Real snapshot inventory reconciliation and repository concurrency passed. Real activity startup restored the synthetic official cache offline alongside partially corrupt private data, recovered/exported valid records locally, and preserved the tested favourite, ID, number, override and source language. The test's app-process log scan found no synthetic private URL in the exercised path. A controlled HTTPS handshake stall was cancelled with the catalogue retained; the direct loopback HTTP adapter deadline and cancellation disconnected its socket. Synthetic state replacement/removal/empty, Back and recreation assertions passed. These checks do not prove power-loss durability, arbitrary network stacks, all possible log paths, real media decoding or physical-device behavior.

An isolated, manually dispatched `.github/workflows/android-platform-instrumentation.yml` provisions an API 35 emulator and runs both flavor connected test tasks. The workflow is not a release or deployment trigger and has not been dispatched here. The manual workflow has not been dispatched. Emulator execution verifies the exercised Android framework paths; injected boundaries do not reproduce every filesystem failure, abrupt process death, or power loss. Physical-device checks remain outstanding.

## Bounded network review

`NetworkFetcher.kt` bounds admitted request and disconnect work to eight leases; a lease stays reserved while a worker or its disconnect remains unresolved. Rejected cleanup and disconnect failures fail closed rather than allowing unbounded worker growth. `PublicationTransaction.kt` and the publication coordinator stage the candidate, serialize competing refreshes, and gate the commit against cancellation; a previous cache is retained. The 22 JVM network tests per flavor and five publication-transaction tests per flavor exercise controlled saturation, timeouts, cancellation, failure and commit races. The Android emulator instrumentation passed controlled loopback sockets and real ViewModel cancellation. An OS call which ignores interruption or throws may retain a lease until process restart; bounded loss of capacity is the explicit residual platform limitation, not proof of socket closure.

## Original audit findings: current evidence

| Finding | Source/JVM status | Runtime and acceptance gaps |
|---|---|---|
| H01 — startup owner | Implemented; Android JVM/build checks pass. | Synthetic cold startup, recreation and three locale starts passed on both flavor emulator builds; picker launch/cancellation and physical mobile/TV remain to be verified. |
| H02 — stale playback | Implemented; Android and Web tests pass. | Both emulator flavors passed synthetic state replacement/removal/empty and Back detach; no real media decoding, copyright-revocation playback, remote or physical device has been proven. |
| H03 — AAB signature | Implemented; disposable expected/wrong signer, unsigned and tampered artifact tests pass. | No real production-signed AAB or release workflow was run. |
| M01 — private recovery | Implemented; injected transaction, snapshot and repository tests pass. | Both emulator flavors passed actual `AtomicFile`, cache isolation, DataStore favourite, concurrent operations and recovery checks; power-loss and physical storage remain unverified. |
| M02 — language persistence | Implemented; override/reset and recovery metadata tests pass. | Native end-to-end reset, picker and recovery with TV remote require device validation. |
| M03 — Unicode parser | Implemented and Web regression tests pass. | Further browser interoperability remains manual acceptance. |
| M04 — HTTPS compatibility | Implemented and adapter tests pass. | Real publisher TLS, redirects and device network conditions remain unverified. |
| M05 — cancellation and bounded work | Implemented; JVM saturation, cancellation and publication race tests pass. | Both emulator flavors passed loopback deadline/cancellation and ViewModel cancellation; other platform socket behavior remains unverified and uninterruptible OS calls fail closed at bounded capacity. |
| M06 — Web publishing gate | Implemented; workflow source and tests reviewed. | Actual failing-test Actions run for a publishing SHA was not dispatched. |
| M07 — light-theme contrast | Implemented; Web checks pass. | Human and assistive-technology contrast review remains. |
| M08 — synchronized branding | Implemented; deterministic generation and asset tests pass. | Physical device artwork review remains. |
| L01 — localized controls | Implemented; localized resources and Web tests pass. | Translation and screen-reader review remains. |
| L02 — release documentation | Implemented; documentation and release-guard tests pass. | Actual authorized release remains separate. |

## Final integrated checks

- Python configuration validation: **PASS**; full Python suite: **38 passed**, including disposable signing negative cases.
- Android mobile and TV: **83 JVM tests per flavor passed**, both lint tasks passed (0 errors; 20 mobile and 19 TV warnings), both debug APKs and both instrumentation APKs built. Final combined offline Gradle run: **PASS**, 173 tasks, 6 executed and 167 up-to-date, 1m 1s.
- Android API 35 emulator: **4 instrumentation checks passed per flavor** via `adb`; Gradle connected task blocked by an uncached offline test-platform artifact as described above.
- Angular lint, **108 tests in 8 files**, and production GitHub Pages build: **PASS**.
- Real headless Chromium concurrency script: **PASS** using same-origin iframe contexts; this is not separate browser processes, a media test, or an Android test.
- `git -c core.safecrlf=false diff --check`: **PASS**. Versions and pre-existing modified documentation were preserved.
- Debug APK metadata and signatures verified with `aapt`/`apksigner`: mobile `com.actionanand.vizhitivi.app.debug`, TV `com.actionanand.vizhitivi.tv.debug`, version code 1, version name `1.0.0-debug`, target SDK 36; one Android Debug signer, v2 verified for each. This is not production signature verification.

## Remaining manual acceptance

Make the Gradle connected tasks runnable with their test-platform artifacts and run the manual CI workflow; verify results independently. On physical mobile and TV devices verify cold startup, activity results, D-pad focus and Back, recovery/export including process interruption and quota/storage failures, language reset, connection cancellation, and permitted real HLS/DASH stream replacement/removal/revocation. Independently review storage durability and bounded network failure behavior. Test actual browser processes, quota denial, assistive technology and permitted HLS playback. In an authorized release environment validate a real signed AAB/APK and exercise the Pages gate. Production release remains NO-GO until applicable acceptance and signing gates pass; source re-audit can proceed independently.

## Complete current changed-file inventory

This includes earlier uncommitted user work and historical reports, not just changes introduced in this continuation:

- `.github/workflows/vizhi-tivi-web.yml`
- `android/app/build.gradle.kts`
- `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt`
- `android/app/src/main/res/drawable-nodpi/app_icon.png`
- `android/app/src/main/res/values-sa/strings.xml`
- `android/app/src/main/res/values-ta/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt`
- `contracts/README.md`
- `contracts/fixtures/official-v1/README.md`
- `documentation/README.md`
- `documentation/branding.md`
- `documentation/release-process.md`
- `scripts/ci-build.sh`
- `scripts/sync-branding.py`
- `scripts/tests/test_stage1.py`
- `web/README.md`
- `web/vizhi-tivi/public/app-icon.png`
- `web/vizhi-tivi/public/favicon.ico`
- `web/vizhi-tivi/src/app/core/domain.spec.ts`
- `web/vizhi-tivi/src/app/core/domain.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.spec.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.ts`
- `web/vizhi-tivi/src/app/core/store.spec.ts`
- `web/vizhi-tivi/src/app/core/store.ts`
- `web/vizhi-tivi/src/app/locales/en.json`
- `web/vizhi-tivi/src/app/locales/sa.json`
- `web/vizhi-tivi/src/app/locales/ta.json`
- `web/vizhi-tivi/src/app/player.spec.ts`
- `web/vizhi-tivi/src/app/player.ts`
- `web/vizhi-tivi/src/app/shell.html`
- `web/vizhi-tivi/src/app/shell.scss`
- `web/vizhi-tivi/src/app/shell.spec.ts`
- `web/vizhi-tivi/src/app/shell.ts`
- `web/vizhi-tivi/vitest-base.config.mts`
- `.github/workflows/android-platform-instrumentation.yml`
- `android/app/src/androidTest/java/com/actionanand/vizhitivi/PlatformChecks.kt`
- `android/app/src/androidTest/java/com/actionanand/vizhitivi/PlatformInstrumentation.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/NetworkFetcher.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivatePlaylistCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivateRecoveryTransaction.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PublicationTransaction.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/RecoverySnapshots.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/NetworkFetcherTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/PrivateRecoveryTransactionTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/PublicationTransactionTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/RecoverySnapshotsTest.kt`
- `documentation/STAGE_1A_FINAL_AUDIT.md`
- `documentation/STAGE_1A_REMEDIATION_REPORT.md`
- `scripts/test-browser-concurrency.py`
- `scripts/tests/test_branding_sync.py`
- `scripts/tests/test_verify_aab.py`
- `scripts/verify-aab.sh`
- `web/vizhi-tivi/src/app/core/private-lock.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.spec.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.ts`
- `web/vizhi-tivi/src/app/core/test-locks.ts`

No commit, push, merge, tag, deployment, version bump, production signing credential change or data-repository modification was made.


---

# Vizhi TiVi Stage 1A — M01/M05 closure continuation

**Source acceptance: NO-GO; production release: NO-GO.** The two commit paths were corrected and the listed automated suites passed, but required native integration evidence is still missing. In particular, the new tests use a real temporary filesystem through injected writers rather than Android's actual `AtomicFile`/`Os` implementation, and they do not execute native startup with a corrupted private store and a valid offline official cache. A failed or permanently blocked platform disconnect cannot be proven to release a socket: the implementation retains its admission slot and fails closed at a bounded limit. These are not claimed as physical-device results. An independent re-audit should review the implementation and remaining acceptance gaps before merge; release additionally requires physical devices, permitted real streams and production signing.

## M01 — private recovery transaction

`Repository.kt` validates the serialized candidate with `PrivatePlaylistCodec`, captures each distinct active/legacy/pending private-file candidate in the existing recovery snapshots, and only then calls `commitPrivateRecovery` in `PrivateRecoveryTransaction.kt`. The writer stages bytes in `AtomicFile`'s `.new`, flushes and syncs the file, rereads and compares the complete candidate, then calls `finishWrite` and syncs the directory. Before finalization, failures abort the staged write and leave the old active bytes in place. After finalization begins, an exception is treated as an ambiguous commit: there is **no second rollback write**; the captured prior snapshot remains, and `LiveViewModel.restoreSnapshot` marks private data recovery-required so stale in-memory playlists cannot be written over a possibly committed result. On restart the base/legacy backup is read first and an interrupted `.new` is not treated as a valid active store. Favourites remain in the separate DataStore; IDs, numbers, language overrides and source metadata remain in the candidate records.

`PrivateRecoveryTransactionTest` uses temporary disk files and an injected writer to cover failure before start, partial write, sync failure, corrupt staging, before/after rename, post-commit read failure, snapshot-capacity refusal, repeated attempts and valid playlist metadata after reopening. `RecoverySnapshotsTest` covers snapshot creation/finalization interruptions, directory-sync and delete failures, retention and partial snapshot inventory. These tests do not run Android's actual `AtomicFile`, directory `fsync`, DataStore or process-kill behavior. Native official-cache availability after private recovery failure is established by the independent catches in startup source, but the requested native executable integration test was not run. The prior snapshot remains available after an ambiguous commit; a failure after rename can leave the new complete store active and requires explicit recovery.

## M05 — publication commit and bounded cleanup

`PublicationCoordinator` serializes refreshes so each reads its baseline after the preceding refresh; an older request cannot finish a later publication after a newer request through that coordinator. `NetworkCommitGate` rejects cancellation before the commit boundary and runs the short final commit in a non-cancellable section once accepted. Catalogue validation, copying the previous validated cache to one bounded `publication-v1.previous.json` file, writing and syncing the candidate, and rereading the staged bytes occur before that section. `PublicationTransaction.kt` uses a same-directory atomic rename as the commit boundary; the in-memory catalogue follows whether rename occurred, including a directory-sync failure after rename. A cancelled preparation can leave one bounded `.candidate` file, ignored on startup and replaced by a later preparation. The prior cache file remains available after a post-rename failure. It is deliberately **not automatically played** when the active cache is missing or corrupt, because an older publication might contain a subsequently revoked stream.

`NetworkFetcher` has at most eight admission leases shared by request and cleanup work. A lease remains held until its worker and scheduled disconnect finish; an uninterruptible or failed disconnect retains the slot, so repeated failures eventually reject new requests instead of creating unbounded threads or tasks. If the closer executor rejects work, the worker retains ownership and attempts disconnect in its own `finally`; timer-scheduler rejection releases admission before a request starts. Platform DNS, reads or disconnect calls may ignore interruption or throw. The code cannot guarantee that such an OS-level socket is closed; bounded capacity can remain unavailable until process restart. `NetworkFetcherTest` covers cancellation before/during/after the gate, timeout/cancellation races, overlapping serialized refreshes, saturation, rejected cleanup, a hung closer, disconnect failure and scheduler rejection with controlled fakes. `PublicationTransactionTest` tests staged, renamed and retained previous files with injected failures. Real ViewModel lifecycle/Android filesystem and socket behavior remain unverified.

## Original finding matrix

| Finding | Current status | Evidence and remaining work |
|---|---|---|
| H01 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Activity-result owner is provided; cold startup, picker cancellation and recreation remain device checks. |
| H02 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Effective stream keys native playback and Web re-resolves selection; real player replacement/revocation and disposal remain device/media checks. |
| H03 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Disposable verifier tests cover expected/wrong signer, unsigned and tampered artifacts; a production-signed AAB/release workflow was not run. |
| M01 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Staged recovery and snapshot fault tests pass; actual Android `AtomicFile`, startup cache isolation, DataStore and power-loss cases remain unverified. |
| M02 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Override/source metadata, reset and Web reimport/restart tests remain; native end-to-end reset/recovery requires a device. |
| M03 | IMPLEMENTED AND TESTED | Unicode-safe Web parser regression tests pass. |
| M04 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Case-insensitive HTTPS tests pass; actual native publisher redirect/TLS behavior remains unverified. |
| M05 | OPEN — VERIFICATION INCOMPLETE | Staging, commit-gate and bounded cleanup tests pass. Native lifecycle/filesystem/socket integration and guaranteed closure of platform calls that fail or ignore interruption are not established; bounded fail-closed capacity is the explicit limitation. |
| M06 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Pages workflow tests before publication; real Actions failure/trigger behavior was not exercised. |
| M07 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Theme error colors were corrected; real browser accessibility and contrast review remains. |
| M08 | IMPLEMENTED AND TESTED | Deterministic canonical branding generation and dimension/integrity tests pass; device artwork review remains. |
| L01 | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Native Sanskrit correction and localized generic Web track labels are present; accessibility/translation review remains. |
| L02 | IMPLEMENTED AND TESTED | Release documentation and exact-SHA guard wording were corrected. |

## Final validation

All checks below ran after the final application/test source change; earlier runs and the earlier JUnit initialization failure are not presented as final results.

- Bundled Python 3.12 `-m unittest discover -s scripts/tests -v`: **38 passed**; `scripts/validate-config.py`: **PASS**. Disposable JDK/Git Bash signing-negative checks are included.
- Android offline combined `:app:testMobileDebugUnitTest :app:testTvDebugUnitTest :app:lintMobileDebug :app:lintTvDebug :app:assembleMobileDebug :app:assembleTvDebug`: **PASS**, 109 tasks, 28 executed and 81 up-to-date, 1m 55s. Each flavor had **83 JVM tests**: 24 domain, 18 publication adapter, 22 network, 9 recovery snapshots, 5 private transaction and 5 publication transaction; zero failures/errors. Lint: 0 errors, 20 mobile warnings and 19 TV warnings. The focused test run also passed after correcting an initial JUnit test return type.
- Angular `npm run lint`, `npm test -- --watch=false`, `npm run build:gh`: **PASS**, **108 tests in 8 files**.
- `scripts/test-browser-concurrency.py`: **PASS** in real headless Chromium, using two same-origin iframe contexts and a disposable profile; it covers competing writes, conflict/retry, lock-owner closure and a new context restoring state. It is not a separate-browser-process or media test.
- `git -c core.safecrlf=false diff --check`: **PASS** for tracked changes; untracked report and new Kotlin files are separately checked for whitespace.
- `adb devices`: no devices attached; no emulator is installed in the configured SDK. No physical Android/TV, instrumentation, real permitted stream or production signing test was performed.

### Debug APK verification

`aapt dump badging` and `apksigner verify --verbose --print-certs` verified both final debug APKs: `com.actionanand.vizhitivi.app.debug` and `com.actionanand.vizhitivi.tv.debug`, each version code `1`, version name `1.0.0-debug`, target SDK 36, one Android Debug signer and v2 signature verified. Sizes were 18,537,903 and 18,537,959 bytes respectively. Debug signer SHA-256: `f4886b98a6fbcd50589fea6c0d2f50a427a2eb10943aea15716228983bfaf8ff`. These are not production-signed artifacts.

## Changed-file inventory

The following is the complete current working-tree inventory, including preserved prior user changes and the historical audit report; it is not a claim that every item was first changed in this continuation:

- `.github/workflows/vizhi-tivi-web.yml`
- `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt`
- `android/app/src/main/res/drawable-nodpi/app_icon.png`
- `android/app/src/main/res/values-sa/strings.xml`
- `android/app/src/main/res/values-ta/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt`
- `contracts/README.md`
- `contracts/fixtures/official-v1/README.md`
- `documentation/README.md`
- `documentation/branding.md`
- `documentation/release-process.md`
- `scripts/ci-build.sh`
- `scripts/sync-branding.py`
- `scripts/tests/test_stage1.py`
- `web/README.md`
- `web/vizhi-tivi/public/app-icon.png`
- `web/vizhi-tivi/public/favicon.ico`
- `web/vizhi-tivi/src/app/core/domain.spec.ts`
- `web/vizhi-tivi/src/app/core/domain.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.spec.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.ts`
- `web/vizhi-tivi/src/app/core/store.spec.ts`
- `web/vizhi-tivi/src/app/core/store.ts`
- `web/vizhi-tivi/src/app/locales/en.json`
- `web/vizhi-tivi/src/app/locales/sa.json`
- `web/vizhi-tivi/src/app/locales/ta.json`
- `web/vizhi-tivi/src/app/player.spec.ts`
- `web/vizhi-tivi/src/app/player.ts`
- `web/vizhi-tivi/src/app/shell.html`
- `web/vizhi-tivi/src/app/shell.scss`
- `web/vizhi-tivi/src/app/shell.spec.ts`
- `web/vizhi-tivi/src/app/shell.ts`
- `web/vizhi-tivi/vitest-base.config.mts`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/NetworkFetcher.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivatePlaylistCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivateRecoveryTransaction.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PublicationTransaction.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/RecoverySnapshots.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/NetworkFetcherTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/PrivateRecoveryTransactionTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/PublicationTransactionTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/RecoverySnapshotsTest.kt`
- `documentation/STAGE_1A_FINAL_AUDIT.md`
- `documentation/STAGE_1A_REMEDIATION_REPORT.md`
- `scripts/test-browser-concurrency.py`
- `scripts/tests/test_branding_sync.py`
- `scripts/tests/test_verify_aab.py`
- `scripts/verify-aab.sh`
- `web/vizhi-tivi/src/app/core/private-lock.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.spec.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.ts`
- `web/vizhi-tivi/src/app/core/test-locks.ts`

## Remaining acceptance work

Run native instrumentation for private recovery failures while offline official cache is valid, actual `AtomicFile` and directory-sync interruptions, DataStore favourites, ViewModel lifecycle cancellation/refresh races and blocked sockets. On physical mobile and TV devices verify startup/locales, picker, D-pad/Back, recovery/export and real permitted stream replacement/removal/revocation. Test actual browser processes, storage denial/quota, accessibility and legal HLS playback. In an authorized release environment verify a real AAB/APK against the intended production certificate and test the Pages gate. Review the fail-closed network capacity behavior and storage durability assumptions in the independent source audit.

Branch remains `feature/1-bsic-setup`; both version JSON files remain SHA-256 `c34f7e4a895dab63d4723694bdbeaa6cacb4187db26fa002e2834aaf777449ea`. The three pre-existing modified documentation files retain their initial hashes. No commit, push, merge, tag, deployment, production signing-key change or data-repository write was made.

---

## Earlier remediation report (historical source revisions)

# Vizhi TiVi Stage 1A — final implementation continuation

**Source-level remediation: NO-GO. Production release: NO-GO.** M01 and M05 have substantial new safeguards and passing automated tests, but the requested failure guarantees are not fully established. In particular, the native active-store rollback can itself fail after a recovery write, and the lifecycle-cancellation/commit race has not been exercised through the actual ViewModel and Android storage stack. The real Chromium check covers two same-origin iframe contexts, not separate browser processes or actual media playback. Historical reports below describe earlier source revisions and their results must not be used as current evidence.

## Finding-by-finding status

| Finding | Status | Source correction and automated evidence | Remaining verification or work |
|---|---|---|---|
| H01 (High) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | `MainActivity.kt` supplies the activity-result registry owner through the localized composition; both flavors compile. | Cold launch, locale switching, document-picker cancellation and process recreation have not been run on Android. |
| H02 (High) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | `LivePlayer.kt` keys player, buffering and error state to channel and effective stream; Web resolves selected channels against current catalogue. Existing Web tests cover replacement/removal. | Native player disposal, real stream replacement, format changes and copyright revocation require device/media testing. |
| H03 (High) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | `scripts/verify-aab.sh` verifies against the configured keystore and alias and rejects an unsigned result. Three Python tests execute it using disposable keys and check the expected signer, wrong/missing signer, tampering, unsigned artifact and missing password. | These use synthetic JARs exercising the AAB signature mechanism; a real AAB and production-secret release workflow were not run. |
| M01 (Medium) | OPEN — IMPLEMENTATION INCOMPLETE | Android snapshots use synced pending files, directory synchronization, validated inventory, and preserve interrupted private-store candidates before replacement. Web uses a shared exclusive Web Lock, stale-state checks, localStorage storage events and fail-closed unsupported-storage handling. Nine native snapshot tests, Web multi-context simulations and a real Chromium check passed. | `Repository.kt:174` attempts to restore the prior active bytes after verification fails, but catches a second rollback failure: a failed recovery can leave the active store changed even though saved snapshots remain. Actual AtomicFile replacement failure, process death, DataStore favourites and power loss lack runtime tests. An older Web tab or unrelated same-origin writer that does not honor the lock remains outside the lock protocol. |
| M02 (Medium) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Native/Web preserve source language and manual overrides through reimport and provide reset; Web tests include reset, reimport and recovery. Snapshot tests preserve native override/source metadata and channel assignments. | Native restart and UI reset/recovery, including legacy metadata migration, need device instrumentation; legacy overrides without source metadata require reimport. |
| M03 (Medium) | IMPLEMENTED AND TESTED | Web M3U scanning and slicing use consistent string indices; Unicode/emoji and quoted metadata tests pass. | Additional shared-parser vectors remain useful. |
| M04 (Medium) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Native and Web accept case-insensitive HTTPS; URL and publication tests pass. | Full native publisher-derived redirects and production TLS behavior need runtime testing. |
| M05 (Medium) | OPEN — IMPLEMENTATION INCOMPLETE | `NetworkFetcher.kt` bounds each request across redirects/retries/body and disconnects on cancellation; `NetworkOutcome` distinguishes timeout, cancellation and failure. `LiveViewModel.kt` has separate network/local actions, neutral cancellation UI, and `NetworkCommitGate` for user cancellation. Nine network JVM tests include slow drip, stalled reads, redirects, limits, cancellation and commit races. | ViewModel destruction can cancel a coroutine independently of `NetworkCommitGate`; actual cancellation racing the synchronous cache write/reconciliation is not covered. The bounded asynchronous disconnect executor can reject cleanup when saturated, leaving socket timeouts as the fallback. Live ViewModel local-action responsiveness, LKG retention and real device sockets remain unverified. |
| M06 (Medium) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | `.github/workflows/vizhi-tivi-web.yml` runs tests before Pages publication; Python checks workflow ordering. | A deliberately failing Actions publish for the same SHA was not executed. |
| M07 (Medium) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Web error colors have theme-specific contrast; prior calculated light/dark ratios are 7.571:1 and 10.735:1. | Real browser screen-reader, keyboard, system-theme and contrast audits remain. |
| M08 (Medium) | IMPLEMENTED AND TESTED | `scripts/sync-branding.py` derives Android/Web 512-pixel icons and favicon from the approved common source, and synchronizes the TV banner; three Python regressions check dimensions, integrity and outdated/missing assets. | Actual launcher and TV artwork display need device review. |
| L01 (Low) | IMPLEMENTED — RUNTIME VERIFICATION PENDING | Native Sanskrit classification and localized Web generic track labels are present; locale parity and Web track-label tests pass. | Translation and accessibility need human/device review. |
| L02 (Low) | IMPLEMENTED AND TESTED | `documentation/release-process.md` describes exact push-SHA checkout and version isolation; configuration/release guard tests pass. | No release was executed. |

## M01 recovery design and constraints

Android `RecoverySnapshots.kt` inventories separate SHA-256-checked envelopes and any legacy backup. Capture creates a unique `.pending` file, syncs its bytes, renames it to its final name and syncs the directory before active data can be replaced. Interrupted pending files are listed as invalid, are exportable, count toward the five-entry limit and cannot be restored. No snapshots are automatically removed; explicit deletion is refused unless another recoverable snapshot remains. Invalid restore-status markers do not count as completed recovery. `Repository.kt` reads private base/legacy backup/pending files without `AtomicFile.openRead()` side effects, refuses ordinary writes while interrupted sidecars exist, and captures every distinct candidate before a recovery write. Capacity, invalid UTF-8, oversized data and I/O failures fail closed before replacement when snapshot capture cannot complete. Recovery merges existing playlist identities and retains existing per-channel metadata; collisions are rejected rather than silently renumbered.

The native tests inject filesystem boundary failures at pending creation, writing, finalization, directory sync and deletion, then reopen the inventory. They exercise metadata and number preservation in snapshot payloads. JVM tests substitute directory sync and do not execute Android's real `Os.fsync`, DataStore or `AtomicFile` replacement; the rollback limitation above remains a source-level issue. Exports are local user-initiated document-provider actions; a provider selected by the user may itself synchronize data.

Web `private-lock.ts` uses the browser Web Locks API for all cooperating private-store and snapshot mutations. Each mutation checks that its observed active state or selected inventory item is still current under the same exclusive lock; stale work is rejected and the view reloads. Individual localStorage entry writes are atomic, but this is not a multi-key IndexedDB transaction: a prior-state snapshot is written before an active-store replacement, and a crash can leave an additional snapshot. Storage events update other live views; quota failure preserves earlier entries. Browsers lacking Web Locks, secure context support or usable storage fail closed with a localized conflict message. The lock only coordinates updated cooperating clients, not old tabs or unrelated same-origin scripts; this restriction is documented in `web/README.md`.

The permanent `scripts/test-browser-concurrency.py` serves the actual production build locally and opens two same-origin Chromium iframe contexts in a disposable profile. It checked competing private setting writes, a conflict and retry, release after a lock-owning context closes, and a newly opened context reading the resulting state. Simulated multi-context tests additionally cover stale restore/delete selection, simultaneous snapshot creators, retention, unavailable locks/storage and quota failure. These tests do not prove multi-process/browser-vendor behavior, actual playback, or arbitrary storage-device failure.

## M05 network behavior and constraints

`NetworkFetcher.kt` applies a 15-second total budget to each request across redirects, retries, headers and body, bounds response size, and requests asynchronous disconnect on timeout/cancellation. `NetworkOutcome` classifies timeout, cancellation and other failures. Mobile/TV UI provides localized `en`, `ta`, and `sa` timeout and neutral cancellation messages and a cancel action. Local playlist operations and preferences no longer wait for an in-flight network fetch. The user-cancellation gate serializes an accepted cancellation against publication/import commit; cancellation after a commit has begun is not reported as accepted. JVM fakes exercise the gate and network behavior; no actual Android socket, ViewModel lifecycle cancellation, or device responsiveness test was run. The lifecycle and saturated-cleanup limitations in the matrix remain open.

## Final validation

All commands below ran after the last application or test modification, using existing tools and dependencies; no dependency was installed.

- Python: bundled Python 3.12 `-m unittest discover -s scripts/tests -v`: **38 passed**; `scripts/validate-config.py`: **PASS**. The disposable signature tests are included.
- Android: `gradle.bat -p android :app:testMobileDebugUnitTest :app:testTvDebugUnitTest :app:lintMobileDebug :app:lintTvDebug :app:assembleMobileDebug :app:assembleTvDebug --offline --console=plain --max-workers=2 --no-parallel` with JDK 21 and in-process Kotlin: **PASS**. Each flavor has **60 JVM tests**: 24 domain, 18 publication, 9 network and 9 snapshot; zero failures/errors. The substantive build ran 52 tasks and reused 57 in 11m 27s; the post-harness final repeat passed with 2 executed and 107 up-to-date in 31s. Lint reports 0 errors, 20 mobile warnings and 19 TV warnings.
- Angular, in `web/vizhi-tivi`: `npm run lint`, `npm test -- --watch=false`, `npm run build:gh`: **PASS**, **108 tests in 8 files**. Production initial bundle 224.46 kB; lazy HLS chunk 590.30 kB.
- `scripts/test-browser-concurrency.py`: **PASS** against the final build in real headless Chromium with two same-origin iframe contexts and a disposable profile. An earlier harness falsely matched its own script source; the check was corrected to inspect the rendered result, then rerun successfully using real time.
- `git -c core.safecrlf=false diff --check`: **PASS** for tracked changes; the untracked browser test and report are separately checked for trailing whitespace.
- `adb devices`: empty device list. Android instrumentation, physical mobile/TV checks, real permitted stream playback and production signing were **not run**.

### Debug APK identity and signature verification

`aapt dump badging` and `apksigner verify --verbose --print-certs` on the final debug builds returned:

| Flavor | Package | Version code/name | Size | Signature |
|---|---|---|---:|---|
| Mobile | `com.actionanand.vizhitivi.app.debug` | `1` / `1.0.0-debug` | 18,537,903 bytes | Android Debug, one signer, v2 verified |
| TV | `com.actionanand.vizhitivi.tv.debug` | `1` / `1.0.0-debug` | 18,537,959 bytes | Android Debug, one signer, v2 verified |

Both target SDK 36; the debug signing certificate SHA-256 is `f4886b98a6fbcd50589fea6c0d2f50a427a2eb10943aea15716228983bfaf8ff`. These are debug artifacts and do not prove production AAB/APK signing.

## Working-tree changed-file inventory

The list includes preserved pre-existing user modifications and untracked audit artifacts; it does not imply every item was first changed in this continuation:

- `.github/workflows/vizhi-tivi-web.yml`
- `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt`
- `android/app/src/main/res/drawable-nodpi/app_icon.png`
- `android/app/src/main/res/values-sa/strings.xml`
- `android/app/src/main/res/values-ta/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt`
- `contracts/README.md`
- `contracts/fixtures/official-v1/README.md`
- `documentation/README.md`
- `documentation/branding.md`
- `documentation/release-process.md`
- `scripts/ci-build.sh`
- `scripts/sync-branding.py`
- `scripts/tests/test_stage1.py`
- `web/README.md`
- `web/vizhi-tivi/public/app-icon.png`
- `web/vizhi-tivi/public/favicon.ico`
- `web/vizhi-tivi/src/app/core/domain.spec.ts`
- `web/vizhi-tivi/src/app/core/domain.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.spec.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.ts`
- `web/vizhi-tivi/src/app/core/store.spec.ts`
- `web/vizhi-tivi/src/app/core/store.ts`
- `web/vizhi-tivi/src/app/locales/en.json`
- `web/vizhi-tivi/src/app/locales/sa.json`
- `web/vizhi-tivi/src/app/locales/ta.json`
- `web/vizhi-tivi/src/app/player.spec.ts`
- `web/vizhi-tivi/src/app/player.ts`
- `web/vizhi-tivi/src/app/shell.html`
- `web/vizhi-tivi/src/app/shell.scss`
- `web/vizhi-tivi/src/app/shell.spec.ts`
- `web/vizhi-tivi/src/app/shell.ts`
- `web/vizhi-tivi/vitest-base.config.mts`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/NetworkFetcher.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivatePlaylistCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/RecoverySnapshots.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/NetworkFetcherTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/RecoverySnapshotsTest.kt`
- `documentation/STAGE_1A_FINAL_AUDIT.md`
- `documentation/STAGE_1A_REMEDIATION_REPORT.md`
- `scripts/test-browser-concurrency.py`
- `scripts/tests/test_branding_sync.py`
- `scripts/tests/test_verify_aab.py`
- `scripts/verify-aab.sh`
- `web/vizhi-tivi/src/app/core/private-lock.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.spec.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.ts`
- `web/vizhi-tivi/src/app/core/test-locks.ts`

## Remaining acceptance checklist

Complete the M01 active-store failure/rollback transaction and executable Android replacement-interruption tests; complete M05 lifecycle/commit and resource-saturation handling and ViewModel-level regressions; rerun affected validation and seek independent source re-audit. On physical mobile and TV devices verify cold launch in all locales, picker cancellation/recreation, D-pad focus and Back, corrupted private-store recovery and provider export, slow-drip cancellation with responsive local actions, and stream replacement/removal/revocation with HLS/DASH/player disposal. On real browsers test cross-process tabs, browser-specific Web Locks/storage restrictions, quota and crash recovery, keyboard/screen-reader and real permitted HLS playback. In an authorized release environment verify a real AAB/APK against the intended production certificate and exercise the Pages gate without unintended publication.

Branch remained `feature/1-bsic-setup`. Both release version JSON files remain SHA-256 `c34f7e4a895dab63d4723694bdbeaa6cacb4187db26fa002e2834aaf777449ea`. The three pre-existing modified documentation files retain their initial hashes. No commit, push, merge, tag, deployment, release, signing-secret change or data-repository write was made.

---

## Earlier remediation report (historical source revisions)

# Vizhi TiVi Stage 1A remediation report — current continuation

**NO-GO.** The source and test work below is incomplete. In particular, native recovery has not yet passed the full requested regression and runtime matrix. The historical report is retained below and its earlier status and validation claims describe earlier source revisions, not the current revision.

## Current finding matrix

| Finding | Implementation status and root cause/correction | Automated evidence | Runtime verification and remaining work |
|---|---|---|---|
| H01 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: activity-result ownership was lost in the localized composition; `MainActivity.kt` supplies the owner. | Prior JVM/build checks; current full Android run recorded below. | Cold startup, picker cancellation and recreation on both flavors and all locales need instrumentation/device execution. |
| H02 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: stale playback could survive an updated catalogue; `LivePlayer.kt` keys playback to the stream and Web resolves the selected channel from current state. | Existing Web replacement/removal tests. | Device lifecycle/disposal, format changes, copyright revocation and real permitted streams remain unverified. |
| H03 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: verification did not establish the expected AAB signer. `scripts/verify-aab.sh` runs strict `jarsigner` verification against the configured alias and rejects an unsigned result; `ci-build.sh` invokes it. | Three disposable signing tests exercise the actual verifier: correct signer, wrong/missing signer, unsigned, tampered and missing credentials. | No production-key or complete secret-backed release/AAB workflow executed. |
| M01 | OPEN — IMPLEMENTATION INCOMPLETE: a single recovery backup could not safely accommodate repeated corruption. Android `RecoverySnapshots.kt` and Web `recovery-snapshots.ts` now maintain separately identified snapshots; their repositories and recovery views provide selection, export, restore and confirmed deletion. | Web snapshot tests cover three snapshots, capacity, corrupted metadata, last-copy protection, legacy and quota failure; a UI test reports export failure without changing private state; existing Web store tests cover offline official cache, corruption and recovery. Android snapshot JVM tests added; see run result below. | Native UI, storage interruption/rollback and export failure are not established on-device; actual browser quota and download behavior, concurrent browser tabs and all requested multi-incident recovery cases remain unverified. Android pending snapshot handling can block subsequent captures without an in-app way to resolve the pending file. Web does not yet provide a verified transaction across tabs. These are implementation gaps, not device-only gaps. |
| M02 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: detected language was not preserved separately from the manual correction. Native and Web persist both and expose reset; existing source metadata is not invented for legacy records. | Prior native codec and Web reimport/restart/reset tests; Web recovery test covers override and detected language. | Native end-to-end recovery and reset across restart on device remain unverified; legacy overrides require reimport. |
| M03 | IMPLEMENTED AND TESTED: Web parsing previously mixed UTF-8 byte offsets and JavaScript string offsets. `domain.ts` uses Unicode-safe string indexing. | Existing emoji, quoted comma and Tamil tests pass in Web suite. | Additional shared native/browser Unicode vectors are useful. |
| M04 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: inconsistent HTTPS scheme case handling was corrected in Kotlin and Web. | Existing native and Web URL/publication tests. | Verify full publisher-derived native publication and redirect behavior. |
| M05 | OPEN — IMPLEMENTATION INCOMPLETE: socket read timeouts alone did not bound slow-drip requests, and local actions waited behind network operations. `NetworkFetcher.kt` adds a 15-second total deadline, bounded workers, redirects, retries and response size; cancellation requests disconnect; preferences/favourites use independent local actions. | Six focused JVM tests were reported passing during implementation; current full Android run recorded below. | The UI still reports a generic failure rather than a distinct timeout/cancellation; real network cancellation and local responsiveness need runtime tests. |
| M06 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: Pages publication previously lacked a test gate for the checked-out SHA. Workflow and Python ordering regression updated. | Python configuration and workflow-order tests pass. | Deliberately failing a real Actions publication for the same SHA was not performed. |
| M07 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: theme error text lacked suitable contrast; Web theme styling now uses contrast-appropriate colors. | Prior calculated light and dark contrast ratios 7.571:1 and 10.735:1. | Browser accessibility, keyboard, screen-reader and system-light checks remain. |
| M08 | IMPLEMENTED AND TESTED: the sync script rejected the approved 1254-pixel icon. `scripts/sync-branding.py` now deterministically generates 512-pixel Android/Web images and a 32-pixel embedded PNG favicon from the canonical source, and synchronizes the canonical TV banner; `documentation/branding.md` records the source. | Three Python tests check checked-in output, dimensions, missing/outdated output and invalid source. | Visual launcher sizing and TV display still require device review. |
| L01 | IMPLEMENTED — RUNTIME VERIFICATION PENDING: native Sanskrit and localized Web player track labels were missing. Native controls and `player.ts` now use localized labels; English, Tamil and Sanskrit resources remain in parity. | Web player test covers Tamil labels; locale parity test passes. | Screen-reader/TV use remains unverified. |
| L02 | IMPLEMENTED AND TESTED: release documentation misstated checkout semantics; `documentation/release-process.md` describes the exact push SHA. | Existing configuration checks pass. | No real release executed. |

## Recovery snapshot behavior and limitations

Android stores app-private individual SHA-256-checked JSON envelopes, with the original legacy backup listed separately. Web stores separate localStorage entries with SHA-256, and also lists the legacy key. Both target five entries, include the legacy backup in the capacity, never automatically delete snapshots, and refuse capture at capacity. Corrupt original bytes are captured before replacement; candidate data is decoded before persistence. Explicit deletion retains at least one other recoverable snapshot. Android uses `AtomicFile` for active data and a synced pending file followed by rename for snapshots. Web uses individual atomic localStorage writes and compares the active value before replacing it; this is not an IndexedDB transaction. Both preserve prior data on normal snapshot creation failure. Android selected restore retains active playlist records and rejects identity or number conflicts; Web merges existing records and rejects conflicts. Browser quota, a failed verification after a write, concurrent tabs, interrupted Android pending snapshot cleanup and device/provider behavior require further work and tests. No private URLs are intentionally logged or sent to the publisher; user-selected export providers may themselves synchronize data.

## Validation in this continuation

- Python: bundled Python `-m unittest discover -s scripts/tests -v`: **38 passed**, including three disposable `verify-aab.sh` signing tests; `scripts/validate-config.py`: **PASS**.
- Web: `npm test -- --watch=false`: **100 passed, 8 files**; `npm run lint`: **PASS**; `npm run build:gh`: **PASS**. These are automated Vitest tests, not a real-browser acceptance run.
- Android: mobile and TV JVM unit tests each passed 51 tests (24 domain, 18 publication, 6 network, 3 snapshot); both lint tasks and both debug APK builds passed in the combined offline run (109 tasks, 48 executed, 61 up-to-date; 17m 27s). The snapshot test fixture was strengthened after lint, and both JVM suites were rerun afterwards; no application source changed after the build.
- `git -c core.safecrlf=false diff --check`: **PASS** for tracked changes before this report update.
- `adb devices` returned an empty device list; no Android physical device or emulator was available. No instrumentation, real stream, real-browser accessibility test, production signing secret or release workflow was exercised. No dependency installation, version increment, commit, push, merge, tag, deployment or data-repository change was performed.

## Working-tree changed-file inventory

This inventory includes pre-existing user changes and new/untracked files; it is not a claim that every file was modified in this continuation:

- `.github/workflows/vizhi-tivi-web.yml`
- `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt`
- `android/app/src/main/res/drawable-nodpi/app_icon.png`
- `android/app/src/main/res/values-sa/strings.xml`
- `android/app/src/main/res/values-ta/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt`
- `contracts/README.md`
- `contracts/fixtures/official-v1/README.md`
- `documentation/README.md`
- `documentation/branding.md`
- `documentation/release-process.md`
- `scripts/ci-build.sh`
- `scripts/sync-branding.py`
- `scripts/tests/test_stage1.py`
- `web/vizhi-tivi/public/app-icon.png`
- `web/vizhi-tivi/public/favicon.ico`
- `web/vizhi-tivi/src/app/core/domain.spec.ts`
- `web/vizhi-tivi/src/app/core/domain.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.spec.ts`
- `web/vizhi-tivi/src/app/core/published-catalogue.ts`
- `web/vizhi-tivi/src/app/core/store.spec.ts`
- `web/vizhi-tivi/src/app/core/store.ts`
- `web/vizhi-tivi/src/app/locales/en.json`
- `web/vizhi-tivi/src/app/locales/sa.json`
- `web/vizhi-tivi/src/app/locales/ta.json`
- `web/vizhi-tivi/src/app/player.spec.ts`
- `web/vizhi-tivi/src/app/player.ts`
- `web/vizhi-tivi/src/app/shell.html`
- `web/vizhi-tivi/src/app/shell.scss`
- `web/vizhi-tivi/src/app/shell.spec.ts`
- `web/vizhi-tivi/src/app/shell.ts`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/NetworkFetcher.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/PrivatePlaylistCodec.kt`
- `android/app/src/main/java/com/actionanand/vizhitivi/core/RecoverySnapshots.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/NetworkFetcherTest.kt`
- `android/app/src/test/java/com/actionanand/vizhitivi/core/RecoverySnapshotsTest.kt`
- `documentation/STAGE_1A_FINAL_AUDIT.md`
- `documentation/STAGE_1A_REMEDIATION_REPORT.md`
- `scripts/tests/test_branding_sync.py`
- `scripts/tests/test_verify_aab.py`
- `scripts/verify-aab.sh`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.spec.ts`
- `web/vizhi-tivi/src/app/core/recovery-snapshots.ts`

## Required acceptance follow-up

Complete the open M01 and M05 items and their negative regressions; re-run the full final-source suite; independently re-audit. On physical mobile and TV devices check cold start, locale switching, picker cancellation/recreation, remote focus and Back, corrupt-store recovery/export/interruption, slow network and responsive favourites, stream replacement/removal/revocation, HLS/DASH playback and disposal. In a real browser check storage quota, concurrent tabs, download failures, offline recovery, keyboard/screen-reader, themes and permitted HLS playback. In an authorized release environment verify a real AAB/APK against the intended certificate and test the Pages gate without publishing.

---

## Historical report (prior source revisions)

# Vizhi TiVi Stage 1A remediation report

**NO-GO for acceptance re-audit.** Private-playlist recovery, correction persistence, network cancellation, asset synchronization, and requested device/signing regression coverage remain incomplete. No external blocker was established for these implementation tasks; they were not completed in this working session. The prior audit remains historical evidence. Both documents were read before this continuation.

## Finding-by-finding verification

The prefixes retain the original severity: H = High, M = Medium, L = Low. The original root causes and exact locations are retained in `STAGE_1A_FINAL_AUDIT.md`; each row below identifies the correction or remaining root cause and evidence.

| Finding | Status | Changes, evidence, and outstanding work |
|---|---|---|
| H01 | FIXED BUT UNVERIFIED | `MainActivity.kt` provides the activity result registry owner across the localized composition. Both flavors compile and pass JVM tests. Cold-launch/picker instrumentation and device verification remain outstanding. |
| H02 | FIXED BUT UNVERIFIED | Native `LivePlayer.kt` keys the player by effective stream. Web `shell.ts` resolves selection by ID against current state and clears unavailable selection; a DOM test covers replacement and empty catalogue. Native lifecycle/disposal and real playback remain unverified. |
| H03 | FIXED BUT UNVERIFIED | `scripts/ci-build.sh` runs strict verification with the configured keystore and alias, then requires `jar verified.` to reject unsigned archives. Disposable JDK verification in this continuation accepted the expected signer and rejected wrong/missing aliases, tampering and unsigned archives. Automated tests of the actual release script and a real signed AAB remain outstanding. |
| M01 | OPEN | Both clients isolate malformed playlist records, keep valid records accessible, protect the original store from writes after corruption, and offer local exports of readable records and the original store. A confirmed repair action first saves the original locally, then writes readable records; Android verifies the backup digest before writing and preserves an interrupted pending store for recovery. Each playlist is validated before saving, including a 5,000,000-character serialized catalogue limit; private stores are bounded at 20,000,000 UTF-8 bytes. Web tests cover corrupt and empty records, offline cached official publication, oversized stores, successful repair, backup-write failure and an existing different backup; native JVM tests cover invalid/colliding records and unsupported versions. **Blocker:** a second incident with a different existing recovery backup is safely refused rather than offering backup management; native backup/repair, interrupted-write handling, offline startup and export need instrumentation/device verification. |
| M02 | FIXED BUT UNVERIFIED | Both private models persist the latest detected source languages separately from explicit overrides. Localized reset actions restore source languages and clear incompatible numbers, while retaining IDs. Web tests cover override, reimport, restart, reset and reimport for Tamil, English, Sanskrit and unknown metadata, including an override created before source metadata was stored. Native JVM tests cover source-language serialization and reset/number policy for all four; end-to-end Android persistence/UI/device behavior is unverified. Existing overridden records without saved source metadata must be reimported before reset is available; no source language is guessed. |
| M03 | FIXED AND VERIFIED | Web scans and slices UTF-16 consistently. A test covers emoji, quoted comma and Tamil channel name. Broader shared native/Web Unicode and line-ending vectors remain useful. |
| M04 | FIXED BUT UNVERIFIED | Kotlin and Web accept uppercase HTTPS; native helper and Web publication tests pass. Native full-publication and producer-derived negative vectors remain outstanding. |
| M05 | OPEN | Native network operations still lack a total deadline and effective cancellation, and the action mutex can delay local operations. Implement and test slow drip/cancellation/redirect handling. |
| M06 | FIXED BUT UNVERIFIED | Web publish job runs tests before publication. A Python regression checks ordering and the branch guard. Actual Actions failure/trigger behavior was not exercised. |
| M07 | FIXED BUT UNVERIFIED | Theme-specific error colors give calculated light contrast 7.571:1 and dark contrast 10.735:1. Automated contrast/keyboard checks and a browser accessibility audit remain open. |
| M08 | OPEN | Icon source remains 1254×1254 while the existing sync script expects 512×512. Reproducible approved-source resizing and synchronization tests remain needed. |
| L01 | OPEN | Native channel language controls include Sanskrit. Generic Web audio/subtitle fallback localization remains open. |
| L02 | FIXED AND VERIFIED | Release documentation now describes exact push-SHA checkout. The historical audit was preserved; current availability wording still needs review. |

## Validation

| Check | Result |
|---|---|
| Python configuration | PASS, exit 0. |
| Python unittest discovery | PASS, 32 tests including publication gate ordering, rerun in this continuation. |
| Android mobile and TV unit tests | PASS, 42 per flavor: 24 domain and 18 publication tests; zero failures/errors. |
| Android mobile and TV lint | PASS; 0 errors, 23 mobile and 22 TV warnings. |
| Android mobile and TV debug builds | PASS. Final combined Gradle run after interrupted-write handling: 34 seconds, 109 tasks, 32 executed and 77 up-to-date. |
| Angular lint | PASS. |
| Angular tests | PASS, 90 tests in 7 files in Batch A. Tests include offline official-cache restoration with a corrupt private record, protection of original bytes including an empty damaged store, repair/backup failures, source-language reset for Tamil/English/Sanskrit/unknown, prior-override migration, reservation of official numbers after reset, duplicate private identities/source-language rejection and oversized private catalogue/store rejection. |
| Angular production build | PASS, `build:gh`. |
| `git diff --check` | PASS for tracked working-tree changes. |
| Synthetic JDK signing probe | This continuation's disposable check accepted the expected signer and rejected wrong and missing aliases, a tampered archive and an unsigned archive using the same strict verifier and output predicate. The complete release script was not executed. |

In a preceding continuation a sandboxed Gradle daemon connection failed; the retry succeeded. During Batch A, a stale Android lint run was stopped after an unusually long source traversal; a fresh complete offline run against the final source passed. Bundled Python 3.12.14 was used because the Windows `python3` alias was unavailable. No dependencies were installed.

Android used `.tools/gradle-9.5.0/bin/gradle.bat -p android` with `:app:testMobileDebugUnitTest :app:testTvDebugUnitTest :app:lintMobileDebug :app:lintTvDebug :app:assembleMobileDebug :app:assembleTvDebug`, JDK 21, the existing SDK/cache, `--offline`, `--max-workers=2`, and `--no-parallel`. Web used the existing npm CLI for `run lint`, `test -- --watch=false`, and `run build:gh`. Logs are in ignored `.tools/batch-a-*.log` and `.tools/remediation-*.log` files.

Existing tests cover numbering ranges, stable IDs, vacancy reuse, official/private reservations, mixed publication rejection and cache retention. Batch A adds Web restart/reimport/reset tests, offline cache recovery with a corrupt private record, and native codec/isolation/reset tests. They do not establish full native startup/recovery UI behavior, cancellation, native lifecycle or actual browser media. No user IPTV data was used or sent to the official data repository.

## Changed files

- `.github/workflows/vizhi-tivi-web.yml` — test gate.
- `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt` — activity result owner.
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt` — HTTPS and language handling.
- `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt` and `PrivatePlaylistCodec.kt` — private language metadata serialization/restoration and record isolation.
- `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt` — private-store failure isolation and write protection.
- `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt` — private record validation, recovery loading and local export.
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt` — Sanskrit language option.
- `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt` — effective stream key.
- `android/app/src/test/java/com/actionanand/vizhitivi/core/DomainTest.kt` — URL and language tests.
- `scripts/ci-build.sh` — strict expected-signer verification and unsigned guard.
- `scripts/tests/test_stage1.py` — publish test-order check.
- `documentation/release-process.md` — exact-SHA wording.
- `web/vizhi-tivi/src/app/core/domain.ts` and `domain.spec.ts` — UTF-16 parsing and language tests.
- `web/vizhi-tivi/src/app/core/store.ts` and `store.spec.ts` — reimport/restoration of explicit language overrides and regression coverage.
- `web/vizhi-tivi/src/app/core/published-catalogue.ts` and `published-catalogue.spec.ts` — HTTPS handling/tests.
- `web/vizhi-tivi/src/app/shell.ts`, `shell.html`, `shell.spec.ts` and `shell.scss` — current-channel selection, recovery exports, reset action, test and error contrast.
- `android/app/src/main/res/values*/strings.xml` and `web/vizhi-tivi/src/app/locales/{en,ta,sa}.json` — localized reset and export labels.
- `documentation/STAGE_1A_REMEDIATION_REPORT.md` — this report.

The pre-existing audit report and three pre-existing documentation modifications were preserved. Both version files retain SHA-256 `c34f7e4a895dab63d4723694bdbeaa6cacb4187db26fa002e2834aaf777449ea`. No release credentials, commits, pushes, merges, tags or deployments were made.

## Remaining verification

### Batch A2 continuation — M01 remains OPEN

Added a Web regression in `web/vizhi-tivi/src/app/core/store.spec.ts` that restores a readable playlist from a damaged store and checks that its channel ID, Tamil override, detected English language, favourite, and subsequent reset survive. This exercises the existing single-backup recovery path; it does not establish multi-snapshot recovery.

The requested bounded snapshot inventory, snapshot metadata/integrity/recovery status, selection/export/delete interface, retention policy and transactional selected-snapshot recovery were **not implemented** in this continuation. The existing implementation retains at most one recovery backup and refuses a different second backup. No external restriction was established; the remaining blocker is the unimplemented cross-platform snapshot lifecycle and its failure-path tests. M01 therefore remains OPEN and Stage 1A remains NO-GO. No M05 work was performed.

Batch A2 validation: Python configuration PASS and 32 tests PASS; Android mobile/TV unit tests, lint and debug builds PASS in an incremental offline run (109 tasks: 2 executed, 107 up-to-date); Angular lint PASS, 91 tests PASS, and production build PASS. Android physical-device recovery and real-browser storage behavior were not verified.

No physical device, Android instrumentation, real browser accessibility audit, real stream, production signing credential or secret-backed release workflow was used. Web DASH remains intentionally unsupported. Passing builds do not establish startup or real player lifecycle correctness. Complete the open work and missing negative/runtime tests before requesting an independent acceptance re-audit.

Batch A exports are deliberately local actions. On Android, the user chooses a document provider; a provider chosen by the user may itself synchronize files. Web creates a local browser download. Exports can contain private stream URLs and must be handled as private data. The app does not import the exported JSON. Its separate confirmed repair action first preserves the original in a local recovery backup, then replaces the active store with readable records. Backup failure leaves the active original untouched; a different pre-existing backup is never overwritten, and instead blocks another repair. Exporting alone does not repair the store. Native backup/repair and provider behavior remain unverified on a device.

Manual acceptance checklist: cold-start both flavors in all three locales and open/cancel the picker; exercise TV D-pad and Back; verify official revocation during real playback and resource release; test permitted HLS/DASH/progressive streams and browser restrictions; verify corrupt-store recovery and private export without exposing playlist URLs; test slow-drip cancellation and responsive favorites; perform browser keyboard/screen-reader and theme contrast checks; verify a correctly signed AAB in an authorized release workflow after adding automated signer-negative tests.
