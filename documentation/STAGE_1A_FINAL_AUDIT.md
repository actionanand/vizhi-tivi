# Vizhi TiVi — Stage 1A final source-code audit

Audit date: **12 November 2026**. Recommendation: **NO-GO for merge in the current state; NO-GO for release.**

## Executive summary

The published v1 catalogue integration is substantially correct for the current official publication and the representative synthetic fixtures. Both native flavors share the same Kotlin implementation. The adapters reproduce the producer's complete SHA-256 calculation, join split channel/stream documents, map ISO 639-3 languages, preserve published numbers, distinguish verified/experimental streams, reject mixed snapshots and retain previously accepted state on ordinary refresh failures. The official Pages site is now available and publishes an empty catalogue. No development channels are packaged as a production fallback.

All requested executable validation passed independently: **31 Python tests, 37 Android JVM tests per flavor, both Android lint tasks, both debug APK builds, Angular lint, 75 Web tests and the production Web build**. These results confirm compilation and tested behavior, not application startup, actual broadcasts or physical TV usability.

There are **3 High, 8 Medium and 2 Low findings; no Critical finding established**. The main blockers are an Android activity-context/launcher initialization defect, playback retaining an obsolete stream after publication changes, and the existing release verifier rejecting the self-signed certificates produced by the documented signing setup. The latter is a pre-existing release-path problem, not a new feature regression. Additional problems affect private-store recovery, retained language corrections, Unicode playlist names, valid upstream URL compatibility, network deadlines, deployment gates, light-theme accessibility and branding asset synchronization.

The findings below distinguish executed reproductions from source-established behavior and unperformed device checks. No application code, tests, version files, Git refs, signing configuration or remote repository was changed. This report is the only requested source-tree addition.

## Scope, baseline and method

- Workspace root: `C:/AR_Files/code/vizhi-tivi`.
- Audited branch: `feature/1-bsic-setup`.
- Local HEAD and independently checked remote feature HEAD: `5e2fcf1cf1bde8ca61ef77e799d4442e50af396f`.
- Local `master` and merge base: `71b7fc0457bee84ae977e28c8aeef48453989e00`.
- Reviewed range: `git diff master...HEAD`: **96 files, 5,869 insertions and 176 deletions**. This includes Stage 1, official integration, release guard changes, configuration, tests, fixtures, documentation and artwork. Relevant unchanged release/support code was also reviewed; findings identify that distinction.
- Three documentation files were already modified before this audit: `contracts/README.md`, `contracts/fixtures/official-v1/README.md`, and `documentation/README.md`. Their working-tree content was read and preserved. Application/test conclusions refer to the committed feature implementation.
- Data repository `master` was independently checked at `662e34c976b2cfeb7fc14478dea132d74d23916a`. The pinned publication provenance is `fb70679961bae32bdb9411dcd800f6e003561e44` on `gh-pages`.
- Evidence: full change inventory and relevant source inspection; fresh executable checks; inspection of generated test/lint reports and APKs; public branch/Actions/API reads; in-memory probes of actual TypeScript functions/classes; resolved AndroidX bytecode inspection; upstream publisher replay; and a temporary synthetic signing probe. No external dependency was added.
- File references below are exact repository-relative paths under the workspace root, followed by one-based source lines. Binary assets have dimensions/hashes instead of line numbers.

The existing validation reports were treated as claims to check, not as evidence of success. The latest [Android validation run](https://github.com/actionanand/vizhi-tivi/actions/runs/43070649783) and [Angular validation run](https://github.com/actionanand/vizhi-tivi/actions/runs/43070649762) independently report success for the audited HEAD. Local results below were rerun separately.

### Verdict definitions

**PASS** means the stated, bounded behavior is supported by source and/or an executed check. **FAIL** identifies a concrete defect. **NOT VERIFIED** means the required runtime/environmental evidence was not obtained. A source-level PASS is not a physical-device certification. High findings block the recommendation; Medium findings require correction or explicit acceptance with regression coverage before claiming Stage 1 acceptance.

## Area-by-area verdicts

| Area | Verdict | Evidence and limits |
|---|---|---|
| Shared Kotlin architecture | PASS | One `android/app` module and shared `core/`/`ui/`; `BuildConfig.FLAVOR` chooses layout. No duplicated mobile/TV project. |
| Flavor IDs, versions and manifests | PASS | `android/app/build.gradle.kts:47–63`; flavor-specific launchers in `src/mobile/AndroidManifest.xml:5` and `src/tv/AndroidManifest.xml:7`; APK identity verification below. |
| Android startup and localization context | FAIL | H01: activity-result owner is lost before the unconditional import launcher is composed. Device exception was not captured. |
| Native player lifetime, Back and disposal | FAIL | Release/listener cleanup and Back are present in `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt:46–58`; H02 prevents correct stream replacement for the same channel ID. Actual lifecycle/playback is NOT VERIFIED. |
| Native HLS/DASH/progressive wiring | PASS | Explicit HLS/DASH MIME types and Media3 modules, `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt:38–43`, Gradle dependencies. Actual codecs, tracks and broadcast playback are NOT VERIFIED. |
| TV D-pad/focus/Back | NOT VERIFIED | TV buttons, rails, focus requester and Back handlers exist. `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt:130–145` requests focus on the favourite button for the selected card, not necessarily the initiating Play control; off-screen lazy-item restoration and rail boundaries need remote testing. |
| Preferences, favourites and recent history | PASS | DataStore/AtomicFile and Web origin storage implement persistence; Web tests exercise service restoration. Recent entries record the Play action, not successful decoded playback. Native persistence instrumentation is NOT VERIFIED. |
| Private M3U import and persistence | FAIL | Ordinary import/reimport tests pass, but M01/M02/M03 expose recovery, correction and parser defects. |
| Loading/error states | FAIL | Progress and generic retained-data errors exist; M01 can leave persisted private data inaccessible, and M05 can keep native operations waiting without a total deadline. |
| Angular routing/navigation | PASS | Lazy root route in `src/app/app.routes.ts:2–4`; tab/filter/navigation DOM tests pass. Sections use local signals, not separate URLs/history entries. Deep links and browser Back between sections are not implemented. |
| Angular responsive layout | PASS | Grid, wrapping filters and 760px breakpoint in `web/vizhi-tivi/src/app/shell.scss`; source/build verified. Actual narrow-screen layout and text scaling are NOT VERIFIED. |
| Angular official loading/cache restoration | PASS | Actual live loader accepted all eight files; cache/offline/corrupt/quota tests pass. Active-player invalidation still fails H02. |
| Angular HLS/native playback | NOT VERIFIED | Native and dynamic hls.js paths exist. Actual HLS/MSE/network behavior was not exercised by current media mocks. DASH deliberately reports unsupported. |
| Angular resource cleanup | PASS | Effect cleanup removes visibility listener, destroys hls.js and clears native source (`web/vizhi-tivi/src/app/player.ts:182–194`); progressive cleanup test passes. Real HLS teardown/races remain NOT VERIFIED. |
| Dark/light/system themes | FAIL | Theme selection/CSS exist; light/system-light operation-error text fails contrast (M07). Visual behavior on browsers/TV is NOT VERIFIED. |
| Keyboard navigation/accessibility | FAIL | Native controls, Web labels/status/alert roles and focus-return test are present; M07 is a calculable contrast defect. No real-browser accessibility audit or screen-reader check was run. |
| Published manifest/split JSON contract | FAIL | Current publication and synthetic fixtures pass; M04 demonstrates a valid upstream-produced HTTPS URL rejected by both adapters. |
| Full SHA-256 and opaque publication identity | PASS | Producer wrapper includes catalogue, streamDoc, three locales and both schemas. Real live digest and independent producer replays match. Hashes are compared for equality, not ordered revisions. |
| Mixed-snapshot rejection | PASS | Manifest bracketing plus complete digest verification; mixed deployment tests on both adapters. No claim of server-side transactional deployment is made. |
| Channel/stream ownership, joins and duplicates | PASS | Both adapters validate counts, duplicate identities/numbers/references, owners and orphan streams before publication. |
| ISO 639-3 and UI locale separation | PASS | `tam→ta`, `eng→en`, `san→sa`; `hin` remains `hin`; UI identifiers stay `en/ta/sa`. Shared projection tests pass on mobile, TV and Web. |
| Verified/experimental eligibility and trials | PASS | Verified candidates preferred; experimental-only choice has trial labels. This is policy eligibility, not a guarantee of working browser playback. |
| Last-known-good retention | FAIL | Refresh/integrity/compatibility/quota failure tests pass; M01 shows a native startup path where private-store failure prevents restoring a valid official cache. |
| Publication refresh/empty catalogue | PASS | Current empty publication validates, replaces older nonempty metadata in tests, and has a localized honest empty state. No production fixture fallback found in output. |
| Number ranges, stability and vacancy reuse | PASS | Official numbers are preserved; surviving number changes rejected; private allocator tests cover ranges, ordering, vacancy reuse, collisions, official reservations and exhaustion. M02 affects retained manual classification on reimport. |
| Official/imported number isolation | PASS | Default imports are unnumbered; opt-in allocation reserves official/other private numbers; official refresh clears conflicting private display assignments to pending. Global malformed private-store records and long transition histories lack tests. |
| Privacy/no private uploads | PASS | No implemented upload/telemetry transport; private bytes are excluded from publication requests. Backup transport remains an interface. No explicit application logging of imported URLs found. |
| HTTPS/redirect validation | NOT VERIFIED | Native import fetch checks each hop and disables cleartext. Web validates input/final URL and omits credentials/referrer, but uses browser automatic redirects; intermediate-hop policy is browser-dependent. No live redirect/TLS fault injection was performed. |
| Timeouts, JSON bounds and persistence limits | FAIL | Network input limits exist, but native total duration/cancellation is not bounded (M05); accepted import and restore validation differ (M01). Cache reads also allocate before validation. |
| Local persistence security | PASS | App-private native files, backup disabled/excluded; Web origin-local storage. Neither is encrypted against device compromise or same-origin script access; this is already documented. |
| Signing credential hygiene | PASS | No tracked signing-key file candidates or common private-key/GitHub/AWS token signatures found. Secrets are referenced through environment/Actions secrets; missing-secret guard fails before signed naming/upload. This was not an exhaustive historical secret audit. |
| English/Tamil/Sanskrit strings/fallback | PASS | All three platforms' dictionaries match: 47 keys and identical values per locale. Native resources/Web dictionary supply English fallback. L01 identifies remaining control/track-label gaps. Translation quality/fonts need human/device review. |
| SDK/Gradle/flavor build compatibility | PASS | Fresh compile, both lint tasks, unit suites and debug APK generation succeeded with the configured SDK/toolchain. |
| Independent release version safety | PASS | Target-only bump, `push.before` guard, exact push SHA checkout, normal non-force final push and release-only triggers. Version files unchanged in the feature diff and this audit. |
| Signed APK/AAB workflow | FAIL | H03: strict AAB verification lacks the expected self-signed signer trust anchor. Full secret-backed signed release was NOT run. |
| Feature/master deployment isolation | PASS | Feature/master validation does not invoke version bump/release. Web publication requires `main-github`; feature manual dispatch cannot satisfy publish conditions. |
| GitHub Pages publication quality gate | FAIL | M06: publication workflow builds/lints but does not depend on the test workflow. No remote deployment was attempted. |
| README/documentation/artwork accuracy | FAIL | Physical limitations are candidly stated. M08/L02 identify asset synchronization and obsolete release-checkout wording; the historical data-site 404 is now resolved. |
| Existing tests as acceptance evidence | FAIL | Useful deterministic tests pass, but they miss the defects below and do not establish native startup, native storage or real media/device behavior. |

## Findings

### Critical

No Critical severity finding was established. This is not a certification that an exhaustive security review or physical-device validation has passed.

### H01 — High: localized Android context removes the activity-result registry owner at startup

**Introduced in Stage 1; affects mobile and TV.**

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/MainActivity.kt:24–27`; `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt:43`.

`createConfigurationContext(config)` replaces `LocalContext` for the entire app. The returned framework configuration context is not the `ComponentActivity` or a wrapper leading back to it. `LiveApp` unconditionally calls `rememberLauncherForActivityResult(OpenDocument())`, including on Home and TV, before a user opens import.

The actual resolved `androidx.activity:activity-compose:1.13.0` runtime JAR was inspected with JDK `javap`. `LocalActivityResultRegistryOwner.getCurrent()` uses an explicitly supplied composition owner or walks `LocalContext` through `ContextWrapper`. Its default composition value is null. The resolved launcher function throws `IllegalStateException` with **“No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner”** when lookup fails. The resolved `ComponentActivity.setContent` passes the content directly and sets lifecycle/ViewModel/saved-state tree owners; it does not explicitly provide this registry composition local. The app itself supplies no replacement registry owner.

The framework context behavior is also visible in the primary [AOSP ContextImpl source](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/app/ContextImpl.java): `createConfigurationContext` constructs a `ContextImpl`.

**Impact:** source and resolved dependency behavior establish a launch-blocking owner lookup on both flavors, rather than merely a picker failure after clicking Import. A physical launch/crash trace was not obtained; this is a source/bytecode-established finding, not a claimed device reproduction.

**Reproduction:** install either generated debug APK and cold-launch Home with default settings. Observe the registry-owner lookup during first composition. No catalogue or imported playlist is needed.

**Recommended correction:** preserve an activity-backed context while localizing resources, or explicitly provide the original `ActivityResultRegistryOwner` across the localized subtree. Check other activity-dependent context consumers as well. Add an actual activity-launch/import-launcher smoke test for both flavors and all three UI locales; pure JVM domain tests cannot detect this.

### H02 — High: accepted catalogue changes do not reliably invalidate the active playback source

**Introduced by the Stage 1/player and integration interaction.**

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt:33–43,66`; `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt:36–48`; `web/vizhi-tivi/src/app/shell.ts:20,88–94`; `web/vizhi-tivi/src/app/shell.html:36–37`; `web/vizhi-tivi/src/app/core/store.ts:168–181`.

- Android re-resolves the selected channel ID from current state, but `remember(channel.id)` retains the existing ExoPlayer and its original media item when the same ID receives a new URL, format or preferred candidate. A new trial/status label can describe a different candidate from the one still loaded. Complete channel removal does leave the native player branch and dispose it; the native defect concerns surviving IDs whose playable stream changes.
- Web stores the selected `Channel` object independently of the store. Catalogue replacement/removal never replaces or clears that object, so the player input remains the old snapshot. This also applies when the replacement publication is empty.

**Executed evidence:** an in-memory probe called the actual transpiled `Shell.prototype.play` with `test-tamil` from the actual synthetic publication, then replaced `LiveStore.catalogue` with an accepted-shape empty catalogue. Result: `officialCount: 0`, while `selectedAfterOfficialRemoval: "test-tamil"`. No media network request was made in this probe. The native same-ID retention follows directly from the `remember` key and media-item construction; actual playback was not exercised.

**User path:** startup exposes cached channels while publication loading continues. Play a cached channel, then let a valid newer snapshot remove it or replace its stream. This path does not require opening Settings while the player is active.

**Impact:** a withdrawn/replaced stream can continue to be selected after a valid new publication has been accepted. Stream status labels and actual native media selection can diverge. Digest/cache correctness alone does not enforce publication playback policy.

**Recommended correction:** derive selection from a stable channel ID plus the latest catalogue; clear unavailable selections. Rebind/recreate native playback using a key that includes the effective candidate identity/URL/format, with disposal of the old player. Add cached-startup refresh tests for removal, empty snapshot, same-ID URL/format change, and verified-to-experimental candidate changes on all clients.

### H03 — High: signed-release AAB verification rejects the documented self-signed signing setup

**Pre-existing in the baseline; still relevant to the requested release-safety audit.**

**Locations:** `scripts/ci-build.sh:24`; `scripts/generate-keystore.sh:13–15`; `.github/workflows/android-release.yml:91–99`; `documentation/release-process.md:27–34,43`.

The documented generator creates a self-signed Android signing certificate. CI invokes `jarsigner -verify -strict "$AAB"` without supplying that certificate/keystore as a trust anchor. A clean JDK trust store does not trust the generated app certificate. With `set -e`, a severe signer warning stops the workflow before committing the version or collecting artifacts.

**Executed reproduction:** using only the existing JDK 21, generated a temporary synthetic RSA self-signed key, signed a temporary JAR, and ran the same strict verification form. Key generation and signing returned 0; strict verification returned **4**, reporting an invalid certificate chain and self-signed signer. Strict verification supplied with the same test keystore and expected alias returned **0**. Temporary material was removed; no real project signing key or credentials were used. The JAR signing verification mechanism is the one used by the AAB command. [Oracle's jarsigner documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jarsigner.html) documents the strict warning exit behavior.

**Impact:** ordinary debug CI can be green while releases using the documented self-signed key fail. A custom runner/JDK with an explicitly trusted signer could behave differently; the real Actions secrets and a complete signed release were not tested.

**Recommended correction:** anchor verification to the expected signing certificate/keystore and alias, retaining strict signature checks and signer identity checks. Keep missing-secret/unsigned-artifact rejection. Add a disposable self-signed signing/verification regression and rejection cases for unsigned, tampered and wrong-signer bundles. Do not solve this by accepting every nonzero verifier result.

## Medium findings

### M01 — Accepted private imports can fail their own restore contract; native startup then skips the official cache

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt:30–36,87–91`; `android/app/src/main/java/com/actionanand/vizhitivi/core/CatalogueCodec.kt:24–25`; `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt:95–104`; `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt:27–37`; `web/vizhi-tivi/src/app/core/domain.ts:99–103,226`; `web/vizhi-tivi/src/app/core/store.ts:61–92,121–124`.

Both parsers accept an unknown one-character language such as `tvg-language="x"` and save it as `["x"]`. Both private restore decoders require each language to contain at least two characters. Thus an import accepted by the app can make its next persisted restore fail. Native startup loads private playlists before the official cache and outside its isolated cache-restore catch: one private failure aborts the entire startup action before `repository.initial()` or refresh. Web discards all restored private state and sets `writable=false`, with no in-app recovery/export path; later successful public refresh can clear the generic error while writes remain disabled.

**Executed Web reproduction:** import `#EXTM3U`, then `#EXTINF:-1 tvg-id="stable" tvg-language="x",News`, then `https://example.com/live.m3u8`. Actual `LiveStore.importText` saved one playlist with language `["x"]`. Constructing a second actual store restored zero playlists, set an error and preserved the raw disk record. The Kotlin acceptance/rejection and startup ordering were verified in source; an additional JShell runtime probe could not start under local registry/socket restrictions and is not counted as native runtime evidence.

Private save/restore size limits also differ: the M3U input may be under 5,000,000 characters, but its expanded serialized playlist can exceed the private decoder's 5,000,000-character limit. Native `readFully()` allocates the entire private file before validation, with no aggregate playlist/file bound.

**Correction/tests:** validate or normalize metadata with the same contract before saving; round-trip accepted imports through restoration. Isolate private-store errors from public-cache restoration and preserve unaffected private records. Provide a deliberate local recovery/export path without uploading secrets. Test malformed metadata, one invalid playlist among valid ones, unsupported store versions, oversized serialized records, and valid public cache plus invalid private store/offline startup.

### M02 — Manual language corrections are silently lost on reimport

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt:63–70,75–77`; `web/vizhi-tivi/src/app/core/store.ts:183–200,212–219`.

Language correction writes the channel's current language, but reimport rebuilds the channel solely from playlist metadata. Only previous numbers are merged; no correction override survives. **Executed Web probe:** import a stable tvg-id without language, correct to `ta`, reimport the identical input/identity: the language changes from `["ta"]` back to `["und"]`. Kotlin has the same merge behavior.

**Impact:** a user's explicit correction is undone and subsequent opt-in numbering may classify the channel in the other-language range. A changed source-language classification can also conflict with a retained number and reject reimport instead of providing a deliberate migration.

**Correction/tests:** persist explicit language overrides keyed by stable imported channel ID, merge them during reimport, and define how users clear overrides. Test correction/reimport/restart with numbering enabled and disabled, including `ta`/`en`/`sa` transitions. Do not infer language from channel spelling.

### M03 — Web M3U delimiter indexing corrupts names containing non-BMP metadata characters

**Locations:** `web/vizhi-tivi/src/app/core/domain.ts:210–215`; native comparison: `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt:73–76`.

Web spreads the metadata string into Unicode code points to find the comma, then uses that index with UTF-16 `slice()`. An emoji before the delimiter occupies one array entry but two UTF-16 units.

**Executed actual-parser reproduction:** `#EXTINF:-1 tvg-id="emoji" tvg-name="😀" tvg-language="tam",Tamil News` followed by a valid HTTPS stream produces the Web name **`,Tamil News`**, not `Tamil News`. Kotlin's character index and substring both use UTF-16, so this specific indexing defect is absent there. The full native edge-case runtime probe was not completed.

**Correction/tests:** scan and slice in the same indexing system. Add shared vectors with supplementary characters in multiple attributes, combining characters, quoted commas and channel names. Existing Tamil/Sanskrit fixtures do not cover surrogate-pair offsets.

### M04 — Both adapters reject an HTTPS URL that the authoritative producer accepts and publishes

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt:24–26`; `android/app/src/main/java/com/actionanand/vizhitivi/core/PublishedCatalogue.kt:35`; `web/vizhi-tivi/src/app/core/published-catalogue.ts:60–64`. Upstream: `scripts/lib.mjs`, `checkedHttps()` and `publicView()` at the audited data commit.

The publisher validates URLs through the URL parser, which accepts an uppercase scheme, and preserves the original URL string in publication output. Kotlin compares `URI.scheme` to lowercase `https` exactly; the Web publication helper requires a lowercase `^https://` prefix.

**Executed cross-repository reproduction:** replaced one synthetic approved source stream URL with `HTTPS://example.com/synthetic/eng-trial`, passed it through the unchanged, hash-verified upstream `publicView()`, then passed that producer-generated snapshot to the actual Web decoder. Producer accepted and emitted that exact URL; Web rejected it with `Expected HTTPS URL`. Native rejects the same scheme through the exact comparison in its shared helper.

**Impact:** a valid new v1 publication can be rejected wholesale even though the old cached catalogue is retained. The current empty publication is unaffected.

**Correction/tests:** agree normalization with the producer, or accept case-insensitive HTTPS schemes while keeping authority/credential/fragment checks. Add producer-to-client URL edge vectors, rather than relying solely on independently invented lowercase samples. Preserve stable IDs and original channel names.

### M05 — Native read timeouts do not bound total operation duration or promptly cancel blocking reads

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt:69–93,119–128`; `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt:40–44,55–60`.

The connection has 8-second connect/read timeouts, two attempts, redirect iteration limits and a byte bound. There is no total request/body deadline. A server sending small chunks before each read timeout can keep the body loop active for an unbounded practical duration. Blocking `HttpURLConnection` work inside `Dispatchers.IO` is not made promptly interruptible merely by coroutine cancellation. All ViewModel actions share one mutex, so preferences/favourites/history/import actions wait behind this fetch.

**Evidence/reproduction scope:** source-established control flow; no external slow-drip server was created and no timing result is claimed. A controlled trusted-TLS endpoint emitting a byte less than every eight seconds demonstrates the missing whole-operation budget.

**Correction/tests:** add a total deadline and cancellation that closes the active connection/input, an aggregate publication budget, and keep local preference operations responsive during remote work. Test slow-drip bodies, stalled headers/redirect chains, cancellation on ViewModel destruction, oversize bodies and queued local actions. Distinguish Media3's minimum-retry configuration from a demonstrated hard media-network attempt limit.

### M06 — Web publishing is not gated by the Web tests

**Pre-existing workflow gap. Locations:** `.github/workflows/vizhi-tivi-web.yml:37–62`; `.github/workflows/web-validation.yml:18–31`.

The `main-github` publication workflow installs, lints and builds, then publishes. The separate validation workflow runs tests but is not a dependency of publication. A failing unit test that leaves lint/build green can therefore coexist with a published deployment. Branch protection on PRs does not itself create an execution dependency for direct pushes/manual reruns on the publishing branch.

**Reproduction:** source inspection establishes the missing gate; no failing commit or deployment was created. In an isolated test repository, introduce a failing assertion with a compiling production app and observe the two independent workflows.

**Correction/tests:** require successful tests for the same SHA within the publishing job or a shared/reusable dependency before publication. Keep existing feature/PR publish guards and version isolation. Test trigger/ref/needs behavior; do not change either remote as part of this audit.

### M07 — Web operation errors fail contrast in light mode

**Locations:** `web/vizhi-tivi/src/app/shell.scss:18–25,128–130,297–305`; `web/vizhi-tivi/src/app/shell.html:33–34`.

The error text stays `#ffb7b3` while light and system-light backgrounds are `#f4f7f8`. Calculated from the actual CSS using the standard sRGB relative-luminance formula, contrast is **1.539:1**. This is below both ordinary-text and large-text thresholds in [WCAG 2.2 contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). The CSS has no light-mode error override.

**Reproduction:** choose Light, trigger an invalid URL/import or refresh failure, and inspect the operation-error paragraph. The color calculation was executed; a browser screenshot/AXE run was not performed.

**Correction/tests:** define theme-specific error colors with at least 4.5:1 ordinary-text contrast. Check dark/light/system states and add a browser accessibility regression for visible errors, focus indicators and controls.

### M08 — New common/Web icon is incompatible with the documented Android branding synchronization

**Locations:** binary `common/branding/icon/app-icon.png`; binary `web/vizhi-tivi/public/app-icon.png`; binary `android/app/src/main/res/drawable-nodpi/app_icon.png`; `scripts/sync-branding.py:8,19–23`; `documentation/branding.md:9`.

The new common and Web PNGs are **1254×1254**, 467,508 bytes, SHA-256 `7539112e8e5928f68dd2e6dedc736776a9ebdde071447660dbf1ba9c00885b46`. The unchanged native copy is **512×512**, 5,958 bytes, SHA-256 `54eadf8a9d954d8b1f37d2b4021f59a952d126361e90c7bf1b82e7a4d4a69d87`. The documented sync script requires the common icon to be exactly 512×512 and exits before copying when it is not. Configuration validation only checks asset presence and does not catch this mismatch.

**Executed evidence:** inspected the common image, read PNG dimensions and compared file hashes. The mutating sync script was not run; its first preflight would reject the measured dimensions before any copy.

**Impact:** native and Web clients ship different icon assets; following the documented source-of-truth synchronization instruction fails. Placeholder status does not fix the broken maintenance command.

**Correction/tests:** establish a canonical source/output sizing policy, produce the approved native/Web derivatives, and validate dimensions plus synchronization without modifying assets during validation. Keep exact Vizhi TiVi branding and placeholder limitations.

## Low findings

### L01 — Remaining language-control and track-label inconsistencies

**Locations:** `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt:144–145`; `web/vizhi-tivi/src/app/shell.html:210–220`; `web/vizhi-tivi/src/app/player.ts:65,164–165`.

The native manual channel-language control offers `ta`, `en`, `und`, but not `sa`; Web offers Sanskrit. An unknown imported Sanskrit channel cannot be manually corrected to Sanskrit through the native UI. Web also supplies literal `Audio`/`CC` fallback labels outside the otherwise matching localized dictionaries. Published channel-language mapping itself is correct, and these issues do not change UI locale identifiers.

**Correction/tests:** provide equivalent supported correction choices, localized accessible labels and translated generic track fallbacks. Test all three UI locales with missing broadcaster track labels and unknown imported-language metadata. Preserve broadcaster-provided names.

### L02 — Release documentation still describes a checkout behavior the guard deliberately changed

**Locations:** `documentation/release-process.md:51`; `.github/workflows/android-release.yml:33–37`; `documentation/setup.md:98–99`. Related historical availability statement: `documentation/integration-validation.md:66` and `documentation/limitations.md:7`.

The release-process document says the workflow checks out the latest branch head. The actual workflow correctly checks out `github.sha`, with a normal final push that can fail if the branch moved. Setup documents this correctly. The conflicting sentence can mislead operators handling queued runs.

The old integration report's 404 result is historical evidence, not evidence of a current failure. All live documents now return 200. Its old outage should not be carried forward as an outstanding deployment blocker. This audit does not claim that the old observation was fabricated.

**Correction/tests:** align the release wording with exact-SHA semantics and update the current limitations/availability narrative with a dated successful check while preserving historical reports. Keep no-auto-release/no-device-readiness limitations.

## Independent cross-repository verification

### Actual current publication

The actual TypeScript `loadPublication()` was run through a temporary in-memory transpilation harness against the production endpoint. It made **nine requests**: initial manifest, seven payload documents, final manifest. Every response was **HTTP 200** with `Access-Control-Allow-Origin: *`. All eight unique file bodies matched the checked-in `official-v1/empty/` fixtures byte-for-byte.

- [Production manifest](https://actionanand.github.io/vizhi-tivi-data/manifest.json)
- [Production catalogue](https://actionanand.github.io/vizhi-tivi-data/catalog/v1/catalogue.json)
- [Production streams](https://actionanand.github.io/vizhi-tivi-data/catalog/v1/streams.json)
- Manifest `channelCount=0`, `streamCount=0`.
- Accepted digest: `64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4`.

This proves current network/structure/digest interoperability through the actual Web adapter. It does not substitute for Android device networking or browser media playback. There are no official approved broadcasts available in this publication to play.

### Producer replay and adapter parity

The upstream [`scripts/lib.mjs`](https://github.com/actionanand/vizhi-tivi-data/blob/662e34c976b2cfeb7fc14478dea132d74d23916a/scripts/lib.mjs) used for replay was checked against the remote file's Git blob SHA: `75c371fa0af02584c9c001d2ad0dbb75532a1d56`. Its unchanged `publicView()`/validation functions were run with in-memory empty inputs and reconstructed synthetic approved source records; no source repository file was written.

| Fixture | Upstream-produced digest | Result |
|---|---|---|
| Empty | `64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4` | Matches fixture; exact parsed catalogue/stream output. |
| Synthetic | `c103f9ac64c904bdadc70bee5dc8312e2213c04c236bec6c595e71e18d565c15` | Matches fixture; exact parsed catalogue/stream output. |

The common expected projection, checked by both native flavor suites, Web and Python, is:

| Channel ID | Number | Internal languages | Preferred stream | Policy eligibility |
|---|---:|---|---|---|
| `test-tamil` | 7 | `ta`, `en` | `tam-z-verified` | Verified; earlier experimental candidate does not win. |
| `test-english` | 251 | `en` | `eng-trial` | Experimental; trial label required. |
| `test-sanskrit` | 252 | `sa` | `san-verified` | Verified. |
| `test-hindi` | 253 | `hin` | `hin-verified` | Verified; unknown-to-UI ISO code retained. |

The English synthetic candidate is DASH. All clients agree that it is permitted by catalogue policy, but the Web player deliberately cannot play DASH. Consequently **metadata eligibility parity passes; universal actual playback parity does not**. The synthetic example hosts/rights-approved flags are test constructs, not working or officially approved broadcast feeds.

The publisher also owns its source numbering ledger and revocations. Clients consume complete published snapshots rather than that private source ledger. They preserve published values, reject changed surviving assignments and reserve official numbers during local allocation. They cannot independently reconstruct a producer's historical retirement ledger from a single published snapshot. This is an explicit trust boundary, not evidence that a retired number was actually misused.

## Executed validation results

All commands used existing installed tools/dependencies. No `npm ci`, dependency install, SDK install, wrapper generation, signing-key import, release build, commit, push or deployment was performed. Test-generated temporary Git repositories/commits are confined to the existing Python tests' disposable fixtures; no project commit was made.

| Check | Exact audit result |
|---|---|
| Python config | Exit 0; `Vizhi TiVi configuration, versions, localization and artwork paths: OK`. |
| Python unittest discovery | Exit 0; **31 tests**, 0 failures, `OK`. |
| Android mobile JVM tests | Exit 0; DomainTest **19**, PublishedCatalogueTest **18**: **37**, 0 failures/errors. |
| Android TV JVM tests | Exit 0; same **37** tests, 0 failures/errors. These are shared suites, not independent device coverage. |
| Android mobile lint | Exit 0; **0 errors, 23 warnings** in fresh XML report. |
| Android TV lint | Exit 0; **0 errors, 22 warnings** in fresh XML report. |
| Android debug builds | Exit 0; combined command **BUILD SUCCESSFUL in 9m 22s; 109 actionable tasks: 109 executed** using `--rerun-tasks`. |
| Mobile debug APK | `app-mobile-debug.apk`, **17,727,013 bytes**; package `com.actionanand.vizhitivi.app.debug`; version `1` / `1.0.0-debug`; target SDK 36. |
| TV debug APK | `app-tv-debug.apk`, **17,727,069 bytes**; package `com.actionanand.vizhitivi.tv.debug`; version `1` / `1.0.0-debug`; target SDK 36. |
| APK signature verification | Both `apksigner verify --verbose` calls succeeded; v2 signature verified, one signer. These are debug signatures, not release-signing proof. |
| Packaged fixture inspection | Neither APK contained the searched catalogue/playlist/vector/official-v1 test fixture paths. Web output had no fixture-like files or `Synthetic test-tamil`/`demo.tamil` bundle text. |
| Angular lint | Exit 0. |
| Angular tests | Exit 0; **7 files, 75 tests passed**, Vitest 5.0.5. |
| Angular production build | Exit 0; `build:gh` output has `<base href="/vizhi-tivi/">`; initial bundle 224.46 kB, lazy HLS chunk 590.30 kB. |
| Live publication loader | All nine request responses 200; accepted empty digest; eight unique bodies exactly match fixtures. |
| Upstream producer replay | Empty and synthetic output/digests match as above. |
| Locale parity | `en`, `ta`, `sa`: **47 native keys and 47 Web keys each; zero differing values**. |
| Current tracked credential scan | No tracked key-file extension candidates and no matches for the examined private-key/GitHub/AWS-token patterns. |
| Adversarial probes | Reproduced Web stale selection, private restore mismatch, correction loss, emoji name corruption, valid upstream uppercase-scheme rejection and strict self-signed-verifier failure. These are additional probes, not added test cases. |
| Extra native adversarial JShell probe | **NOT VERIFIED**: default engine failed under socket restrictions; local engine failed because Windows Java preferences backing storage was unavailable. No native edge-case runtime success is claimed from these attempts. |
| `git diff --check` | Exit 0 before and after report creation. The new report was also checked separately for whitespace errors because an ordinary working-tree diff excludes untracked files. |
| Version isolation | Both version files unchanged against the baseline and the feature diff; guard tests pass. |

Native lint warnings comprise dependency-version suggestions, API-gated `localeConfig`, unused shared resources and the Compose optional-modifier ordering warning. Native libraries `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` could not be stripped and were packaged as-is; the build succeeded. No lint baseline or disabled check was introduced by this audit. Version suggestions are not proof that upgrading is required or safe.

### Commands and environment

The Windows `python3` alias is unavailable. The actual interpreter was Python **3.12.14** at:

```text
C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1110-973-0_26.1110.973.0_x64__3k8sg7r9htsxt/dependencies/python/python.exe
```

It executed `scripts/validate-config.py` and `-m unittest discover -s scripts/tests -v`, with workspace-local temporary storage.

Android used `.tools/gradle-9.5.0/bin/gradle.bat`, `.tools/jdk/jdk-21.0.13+9`, `.tools/sdk`, and `.tools/gradle-cache`, with the equivalent arguments:

```text
-p android :app:testMobileDebugUnitTest :app:testTvDebugUnitTest
:app:lintMobileDebug :app:lintTvDebug
:app:assembleMobileDebug :app:assembleTvDebug
--rerun-tasks --console=plain --max-workers=2 --no-parallel
-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
-Pkotlin.compiler.execution.strategy=in-process
```

The JVM argument pair was passed as one argument. Project configuration remains AGP 9.3.1, Kotlin/Compose compiler 2.4.20, Java bytecode target 17, compile SDK 37.0, Build Tools 36.0.0, minimum SDK 26 and target SDK 36.

Web used Node **v24.22.1** and the existing npm CLI, from `web/vizhi-tivi`:

```text
node ../../.tools/package/bin/npm-cli.js run lint
node ../../.tools/package/bin/npm-cli.js test -- --watch=false
node ../../.tools/package/bin/npm-cli.js run build:gh
```

Ignored local logs: `.tools/audit-config.log`, `.tools/audit-python.log`, `.tools/audit-android.log`, `.tools/audit-web-lint.log`, `.tools/audit-web-tests.log`, `.tools/audit-web-build.log`, `.tools/audit-diff-check.log`. Generated JVM XML reports are under `android/app/build/test-results/`; lint XML under `android/app/build/reports/`. These are local execution evidence, not committed report dependencies.

## Critical examination of test coverage

The existing tests are useful, not merely empty success checks: parser/allocator projections, actual published digests, recomputed-digest negative mutations, source ownership, cache retention/quota, trial/empty UI and release guards all exercise real behavior. However, their scope is materially narrower than feature acceptance:

1. **No native activity/UI/persistence instrumentation:** the 19+18 native tests exercise domain, decoder and publication loading with supplied functions. They never compose MainActivity, resolve the document launcher, exercise DataStore/AtomicFile, instantiate the real ViewModel network path, or drive Media3/TV focus. Running the same suites under two flavors verifies compilation/parity, not remote usability or two independent player implementations.
2. **Web media methods are mocked:** `web/vizhi-tivi/src/app/player.spec.ts:28–34` replaces play/pause/load. Its lifecycle test is meaningful for source clearing, but cannot establish codecs, segment loading, CORS, media decoding or real audio/subtitle selection. It does not execute a real HLS network lifecycle. The input-change test uses another channel ID, and Shell tests do not update a publication while playing.
3. **Source-string checks are limited:** parts of `scripts/tests/test_stage1.py` assert workflow snippets/paths. They cannot establish Actions trigger semantics, dependency ordering, secret-backed signing or a working launcher. Existing configuration validation checks artwork presence, not native/common image equality or correct source dimensions.
4. **Offline fixture self-consistency is not upstream drift detection:** `test_official_contract.py` checks stored copies against stored vectors and independently reconstructs their digest. It does not execute or fetch the producer during CI. The independent producer replay/live-file comparison in this audit supplies additional evidence for the pinned/current contract, but is not yet a maintained cross-repository regression test.
5. **Broad exception assertions need targeted reason checks:** negative tests often assert that any exception is thrown. Recomputed digests improve them, but assertions should distinguish intended schema/reference failures from an incidental parser/integrity failure when practical.

### Missing regression and negative cases

| Priority | Required case |
|---|---|
| Blocker | Cold-launch each native flavor with `en/ta/sa`; establish activity-result owner and exercise picker availability/cancellation. |
| Blocker | Play from cache while a new publication arrives: same-ID URL/format/candidate change, channel removal, empty publication and trial-status change; assert old resources/source are released. |
| Blocker | Disposable self-signed release verification plus wrong-signer, unsigned and tampered bundle rejection; retain absent-secret rejection. |
| High | Accepted M3U → save → restart equivalence, including one-character/invalid language, large expanded JSON, corrupt/unsupported store, one bad playlist among valid ones, and public cache restored independently while offline. |
| High | Manual language override persistence through reimport/restart and opt-in numbering, including explicit correction reset. |
| High | Producer-generated URL compatibility vectors; canonical/legal scheme handling and consistent unsafe URL rejection. |
| Medium | Unicode supplementary characters before M3U delimiters; BOM/CRLF/lone-CR behavior; quote/comma/whitespace vectors on both parsers. |
| Medium | HTTPS downgrade/cross-origin redirect behavior, too many redirects, stalled headers/bodies, slow-drip bytes, network cancellation, byte/record/aggregate limits and local-action responsiveness. |
| Medium | Multiple private playlists, official numbers newly occupying private slots, repeated retirement/reappearance transitions, malformed restored global collisions, and surviving IDs after rename/order/source URL changes. |
| Medium | Actual hls.js initialization/fatal errors/dynamic-import failure, visibility pause, audio/subtitle changes and teardown during delayed initialization; native lifecycle/player resource counts. |
| Medium | Publishing blocked on a test failure for the same SHA; exact-SHA queued release races; missing-secret and artifact-name checks against executable scripts rather than only source snippets. |
| Medium | Light/dark/system contrast, keyboard-only error recovery, screen-reader names, skip link and focus restoration when the originating card is filtered/removed/off-screen. |
| Low | Canonical branding output dimensions/hashes; locale coverage of generic track labels and native Sanskrit correction choice. |

No new test files or implementation corrections were made during the audit.

## Privacy, security and compatibility boundaries

Native backup/transfer exclusions and Web origin isolation are appropriate for the documented Stage 1 local-only design. Imported streams, playlist records and query tokens are not serialized into the public cache or sent to the data repository. URL fetching and playback necessarily contact the chosen provider; this is distinct from an application upload/telemetry path. Browser/OS network diagnostics were not audited as a guarantee that a URL can never appear in local diagnostics.

Native import fetching manually validates each redirect and has finite hop/attempt/byte limits, but M05 prevents claiming a finite total request duration. Web `fetchText` uses default automatic redirect following and validates the final response URL only after the redirect. That is not application-level validation of each intermediate hop. Production HTTPS browsers enforce their own mixed-content/CORS rules; no actual insecure-hop request or secret leakage was demonstrated. Test this explicitly, especially for localhost/development origins, or reject redirects when intermediate HTTPS cannot be verified. CORS failure must remain an honest local error; no proxy or access-control workaround should be added.

Both adapters parse untrusted JSON into explicit typed checks rather than evaluating payload code. Record counts, document lengths and aggregate decoded publication lengths are bounded. They do not execute full downloaded JSON Schemas; they check supported schema-version markers and implement the current field validation manually. A changed incompatible publication must continue to fail closed and retain LKG. The current upstream schema permits empty `streamIds`; an additional probe confirmed the Web decoder accepts it consistently with that schema, so this was **not** reported as a defect.

No hard parsing-depth budget or pre-allocation bound on native cache `readFully()` was established. Very deeply nested/large cached data, aggregate private-store growth and out-of-memory recovery remain negative-test gaps. SHA-256 establishes publication consistency with the fetched manifest, not an independent publisher signature or authorization source; HTTPS and the configured official endpoint remain the trust boundary. Mirrors are deliberately user-controlled testing overrides.

## Documentation review

`README.md`, `documentation/stage-1.md`, `documentation/validation.md`, `documentation/integration-validation.md`, `documentation/limitations.md`, `documentation/setup.md`, `documentation/release-process.md`, branding/contract/fixture documentation and `web/README.md` were checked against source.

Accurate qualifications include the MVP/skeleton foundation scope, no shipped broadcasts, no VOD/DRM bypass/background service/Dropbox implementation, Web DASH/CORS/codecs/autoplay limitations, private unencrypted local storage boundary, absent wrapper JAR, temporary artwork and outstanding physical acceptance. These qualifications should remain.

Claims requiring correction/qualification are startup usability until H01 is fixed, current-media alignment with refreshed publications (H02), successful documented self-signed release verification (H03), unconditional retained prior data after private-store restore failure (M01), and the broken branding sync instruction (M08). Release-process checkout wording conflicts with the correct guard/setup documentation (L02). Previously reported test counts were independently reproduced; previously reported APK sizes were not reused as current evidence. The old 404 observation is superseded by this audit's successful live checks.

## Remaining physical-device and browser acceptance

All of the following remain **NOT VERIFIED** and must not be inferred from green CI:

- Cold-launch mobile/TV, locale changes, rotation/process recreation, picker absence/cancellation, private reimport/restart and offline cache restoration. First resolve H01 before treating any other native UI path as usable.
- API 26+ phone plus representative current Android device: permitted HLS/DASH/progressive streams, extensionless URLs, codecs, audio focus, becoming-noisy, track/subtitle menus, buffering/errors/retry behavior, screen-on, background/foreground and repeated open/close.
- Physical Google TV/Android TV at 1080p: initial focus, rapid D-pad actions, all navigation/filter/rail edges, scroll-to-focused-card, return from player, dialogs/URL keyboard, Back hierarchy, long Tamil/Sanskrit strings, track menus and memory/player-instance counts. Touch-emulated clicks do not establish this.
- Real Chromium/Firefox/Safari as applicable: native HLS versus MSE/hls.js, CORS, redirects, autoplay denial, fullscreen, background pause, real HLS teardown and explicit unsupported DASH behavior. Current synthetic URLs are not usable broadcast fixtures.
- Browser keyboard/screen reader/accessibility audit and narrow phone/desktop layouts; contrast, text scaling, Tamil/Devanagari glyphs, light/dark/system themes and focus when selected content is removed or filtered.
- Native backup exclusion/OEM device-transfer behavior and real storage-failure recovery; browser private mode/quota/storage-denial recovery. No user playlist or private URL should be copied into shared evidence.
- Full signed-release workflow on a deliberately authorized release branch with real secrets, exact signer verification and final-push race behavior, after H03 is corrected. No release branch or remote action was exercised by this audit.

## Merge/release recommendation and preservation record

**NO-GO for the current merge.** Resolve H01 and H02 with runtime-oriented regression coverage, correct or explicitly address the pre-existing H03 release blocker, and repair private-store recovery/contract acceptance defects before describing Stage 1 as accepted. Re-run the relevant complete checks after corrections. Review/accept each remaining Medium item explicitly; physical acceptance remains a separate release gate even after source defects are fixed.

Release version JSON files were not changed by the feature diff or this audit. At audit start, each had SHA-256 `c34f7e4a895dab63d4723694bdbeaa6cacb4187db26fa002e2834aaf777449ea`; both contain independent platform metadata despite presently matching bytes. The release-isolation rules remain intact. The exact file hashes and all tracked-file preservation were checked again at audit completion.

No application/test/source correction, commit, push, merge, branch change, release, remote write, Pages settings change or deployment was performed. Generated build/test outputs, ignored logs and disposable validation material are local validation artifacts. The only audit deliverable is `documentation/STAGE_1A_FINAL_AUDIT.md`; the three pre-existing documentation modifications remain the user's work.
