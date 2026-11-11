# Stage 1 cross-repository integration correction

## Result and external contract

The Kotlin mobile/TV and Angular clients now adapt the authoritative `actionanand/vizhi-tivi-data` v1 publication rather than expecting a client-defined embedded-stream catalogue with an integer revision. Both native flavors retain one app module and the same adapter implementation. Existing private M3U models/stores, UI locale identifiers and release version tracks are preserved.

Source inspected on `master`: [`662e34c976b2cfeb7fc14478dea132d74d23916a`](https://github.com/actionanand/vizhi-tivi-data/tree/662e34c976b2cfeb7fc14478dea132d74d23916a). Actual published JSON fixtures came from `gh-pages` commit [`fb70679961bae32bdb9411dcd800f6e003561e44`](https://github.com/actionanand/vizhi-tivi-data/tree/fb70679961bae32bdb9411dcd800f6e003561e44). Its empty publication digest is `64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4`.

The producer's [client integration guide](https://github.com/actionanand/vizhi-tivi-data/blob/662e34c976b2cfeb7fc14478dea132d74d23916a/docs/client-integration.md) and [`publicView()` implementation](https://github.com/actionanand/vizhi-tivi-data/blob/662e34c976b2cfeb7fc14478dea132d74d23916a/scripts/lib.mjs) establish that `contentSha256` covers the catalogue, streams, three locale documents and both schemas. It is an opaque publication-change identity, not a single-file checksum or an ordered revision.

## Implementation

- `PublishedCatalogue.kt` and `core/published-catalogue.ts` validate the manifest and seven payload documents, recompute the full publisher digest, validate references/owners/duplicates/counts/number ranges, and join channels with streams by `streamIds`.
- A second manifest read brackets the payload requests. Mixed deployments, digest failures, unsupported versions or malformed records reject the entire update. Validated cached content with the same digest requires only one manifest request. Neither client compares hashes as integers or lexicographic revisions.
- Kotlin preserves parsed JSON property order and emits the publisher's compact JSON semantics for hashing; TypeScript uses the producer's `JSON.stringify` wrapper directly. Both reproduce the actual published digest, including Tamil/Sanskrit locale documents.
- Official IDs and numbers remain exactly as published. `tam`/`eng`/`san` map to internal `ta`/`en`/`sa`; other ISO 639-3 codes remain unchanged. UI locale identifiers stay `en`, `ta`, `sa`. Names, broadcaster/provenance/logo metadata, stream IDs, verification dates and rights-evidence URLs are retained in the adapted models/raw cache.
- Stream status remains `verified` or `experimental`. Verified candidates are preferred; experimental-only playback is labelled as a trial on channel cards and in the player. Private imports retain their separate legacy `approved` policy and Imported TV identity.
- `app-config.json` supplies the default `https://actionanand.github.io/vizhi-tivi-data/manifest.json`. Android BuildConfig and Angular read that configuration. Saved HTTPS mirror overrides remain available; blank saved endpoints migrate to the default.
- Complete eight-file snapshots are cached as one atomic/versioned record, revalidated on restoration, and published to UI state only after validation and persistence succeed. Network, compatibility, integrity and quota failures retain the last-known-good catalogue. An accepted empty snapshot clears older official channels. The public model's legacy revision field is `null`.
- Private playlist bytes are not rewritten by official refresh and are never sent to the publisher. Conflicting private number displays are reconciled locally. Existing non-development legacy cache records can remain a fallback during migration; flagged development caches are ignored.
- The old public fixture copy was removed from Angular assets, and Android no longer includes `contracts/fixtures` as main assets. Test fixtures are not a production fallback. Empty official publications show a localized empty state without fake channels.
- Root public schemas are exact upstream copies. The former internal schema is explicitly named `legacy-device-catalogue-v1.schema.json`; future private backup references use that schema.

## Executed validation

| Command/check | Exact result |
|---|---|
| `python3 scripts/validate-config.py` | PASS |
| `python3 -m unittest discover -s scripts/tests -v` | PASS: 31 tests |
| `:app:testMobileDebugUnitTest` | PASS: 37 tests (19 existing + 18 integration), zero failures/errors |
| `:app:testTvDebugUnitTest` | PASS: 37 tests (same shared suites), zero failures/errors |
| `:app:lintMobileDebug` | PASS: zero errors, 23 warnings |
| `:app:lintTvDebug` | PASS: zero errors, 22 warnings |
| `:app:assembleMobileDebug` | PASS: `app-mobile-debug.apk`, 21,784,261 bytes |
| `:app:assembleTvDebug` | PASS: `app-tv-debug.apk`, 21,784,317 bytes |
| `apksigner verify` on both new APKs | PASS |
| APK asset inspection | PASS: no development/publication test fixtures |
| `npm run lint` | PASS |
| `npm test -- --watch=false` | PASS: 75 tests in 7 files |
| `npm run build:gh` | PASS: `/vizhi-tivi/` production base href |
| Release versions/workflow/isolation guard diff | Unchanged |

The Windows `python3` alias is unavailable, so the Python commands used bundled Python 3.12.14 with workspace-local temporary storage. Android used the verified local Gradle 9.5.0/JDK 21/SDK 37.0 installation and the existing SDK/version configuration. The combined Android command completed in 3m 36s (109 actionable tasks: 64 executed, 45 up-to-date):

```sh
gradle -p android :app:testMobileDebugUnitTest :app:testTvDebugUnitTest \
  :app:lintMobileDebug :app:lintTvDebug \
  :app:assembleMobileDebug :app:assembleTvDebug \
  --console=plain --max-workers=2 --no-parallel \
  '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Initial Angular test compilation caught literal-path indexing types in the new fixture tests; the publication path constants were made literal/read-only and the full suite then passed. No failed check was suppressed. Remaining native lint warnings are dependency-version suggestions, API-gated locale configuration, unused shared translation resources and the existing Compose modifier-parameter warning; no new lint baseline or disabled checks were added.

The only added runtime dependency is pinned Kotlin `kotlinx-serialization-json:1.9.0`, verified from Maven Central before use. It preserves the producer JSON structure/order for integrity checks without changing the private `org.json` decoder. No Angular dependencies or release/toolchain versions were changed.

## Integration evidence

`contracts/fixtures/official-v1/empty/` contains the actual eight published JSON files. `synthetic/` uses the pinned upstream producer with in-memory synthetic, rights-approved test records, four channels and five streams. All example stream/evidence hosts are reserved documentation domains; these are not live or actually approved broadcast feeds. Upstream software licensing is retained.

The common `vectors.json` projection is checked by Kotlin on **both flavors**, Angular and independent Python code. It verifies identical channel IDs, assigned numbers, mapped languages, joined stream IDs/statuses, preferred candidates and trial flags. Tests also cover schema compatibility, full digest coverage (including locales/schemas), cache restoration, duplicate IDs/numbers/references, missing/cross-channel/orphan ownership, invalid status/date/scheme, unchanged-digest request suppression, manifest path safety, mixed deployment rejection, private-byte preservation, storage quota failure and the valid empty catalogue. Angular DOM tests verify the empty state and trial labels.

## Remaining limitations

The official manifest, catalogue and streams Pages URLs were checked twice during this task; all returned **HTTP 404**. The `gh-pages` branch does contain the valid empty publication used in the tests. Client support and default configuration are corrected, but a real production network success cannot be claimed until the data repository's Pages site is available. No Pages settings, data branch, remote file or deployment was changed.

The public catalogue currently has zero channels/streams, so there was no actual approved broadcast to play. Domain eligibility is equivalent across clients; device codecs, geography, CORS and the existing web DASH limitation can still prevent runtime playback. No physical mobile/TV, native storage instrumentation, D-pad, real-browser visual/AXE or real-stream playback verification was performed in this correction. These remain the physical/device acceptance checks described in `stage-1.md`.

All work remains on `feature/1-bsic-setup`. `android-version.json`, `tv-version.json`, signed-release rules and version-isolation guard are unchanged. No commit, push, merge, PR or deployment was performed. The source data repository was read only.

## Changed files

The following inventory covers this integration correction only; local tools, downloaded upstream source and generated build outputs are ignored.

| File | Change |
|---|---|
| `AGENTS.md` | Record the upstream v1 API as authoritative without changing release rules. |
| `README.md` | Describe the official default and test-only development fixtures. |
| `android/app/build.gradle.kts` | Inject the manifest URL, add the ordered JSON parser, remove test fixtures from production assets. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/Domain.kt` | Publication identity, preserved stream metadata/status and verified/trial eligibility. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/LiveViewModel.kt` | Cached startup, publication refresh and private-store-safe reconciliation. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/PublishedCatalogue.kt` | Authoritative v1 decoder/adapter, digest, snapshot loader and cache envelope. |
| `android/app/src/main/java/com/actionanand/vizhitivi/core/Repository.kt` | Production endpoint default and atomic complete-publication cache. |
| `android/app/src/main/java/com/actionanand/vizhitivi/ui/LiveApp.kt` | Localized empty-publication state and trial channel labels. |
| `android/app/src/main/java/com/actionanand/vizhitivi/ui/LivePlayer.kt` | Trial label for experimental playback. |
| `android/app/src/main/res/values-sa/strings.xml` | Localized trial and empty-publication interface strings. |
| `android/app/src/main/res/values-ta/strings.xml` | Localized trial and empty-publication interface strings. |
| `android/app/src/main/res/values/strings.xml` | Localized trial and empty-publication interface strings. |
| `android/app/src/test/java/com/actionanand/vizhitivi/core/PublishedCatalogueTest.kt` | 18 shared native integration tests, executed on mobile and TV. |
| `app-config.json` | Central official manifest URL. |
| `contracts/README.md` | Explain the authoritative external protocol, adaptation, integrity and cache rules. |
| `contracts/backup-v1.schema.json` | Reference the private schema rather than the public channel shape. |
| `contracts/catalogue-v1.schema.json` | Exact upstream public channel schema. |
| `contracts/fixtures/official-v1/README.md` | Pinned fixture provenance, generation method and test-only scope. |
| `contracts/fixtures/official-v1/UPSTREAM-LICENSE` | Retain upstream software licensing. |
| `contracts/fixtures/official-v1/empty/catalog/v1/catalogue.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/catalog/v1/locales/en.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/catalog/v1/locales/sa.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/catalog/v1/locales/ta.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/catalog/v1/streams.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/manifest.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/schema/catalogue-v1.schema.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/empty/schema/streams-v1.schema.json` | Exact JSON file from the pinned gh-pages publication. |
| `contracts/fixtures/official-v1/synthetic/catalog/v1/catalogue.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/catalog/v1/locales/en.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/catalog/v1/locales/sa.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/catalog/v1/locales/ta.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/catalog/v1/streams.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/manifest.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/schema/catalogue-v1.schema.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/synthetic/schema/streams-v1.schema.json` | Test-only output from the pinned upstream publisher using synthetic inputs. |
| `contracts/fixtures/official-v1/vectors.json` | Shared raw publications, provenance and equivalent expected client projections. |
| `contracts/legacy-device-catalogue-v1.schema.json` | Explicitly retain the previous internal/private schema. |
| `contracts/streams-v1.schema.json` | Exact upstream public stream schema. |
| `documentation/README.md` | Current architecture/setup/limitations/validation documentation. |
| `documentation/integration-validation.md` | Current architecture/setup/limitations/validation documentation. |
| `documentation/limitations.md` | Current architecture/setup/limitations/validation documentation. |
| `documentation/setup.md` | Current architecture/setup/limitations/validation documentation. |
| `documentation/stage-1.md` | Current architecture/setup/limitations/validation documentation. |
| `documentation/validation.md` | Current architecture/setup/limitations/validation documentation. |
| `scripts/tests/test_official_contract.py` | Five independent upstream digest/projection/schema/configuration tests. |
| `scripts/tests/test_stage1.py` | Require development fixtures to remain outside production assets. |
| `web/vizhi-tivi/public/catalogue-v1.json` | Removed the production development fixture copy. |
| `web/vizhi-tivi/src/app/core/domain.ts` | Publication identity and preserved verified/experimental stream metadata. |
| `web/vizhi-tivi/src/app/core/published-catalogue.spec.ts` | 19 upstream structure, digest, identity, eligibility and failure tests. |
| `web/vizhi-tivi/src/app/core/published-catalogue.ts` | TypeScript v1 validation, integrity, adapter and conditional snapshot loading. |
| `web/vizhi-tivi/src/app/core/schema.spec.ts` | Identify the older schema tests as private/legacy. |
| `web/vizhi-tivi/src/app/core/store.spec.ts` | Cache/offline/integrity/mixed-deployment/quota/private-byte regressions. |
| `web/vizhi-tivi/src/app/core/store.ts` | Complete publication persistence, startup/refresh, fixture migration and private-state isolation. |
| `web/vizhi-tivi/src/app/locales/en.json` | Localized trial and empty-publication interface strings. |
| `web/vizhi-tivi/src/app/locales/sa.json` | Localized trial and empty-publication interface strings. |
| `web/vizhi-tivi/src/app/locales/ta.json` | Localized trial and empty-publication interface strings. |
| `web/vizhi-tivi/src/app/player.spec.ts` | Trial display regression. |
| `web/vizhi-tivi/src/app/player.ts` | Experimental trial display in the player. |
| `web/vizhi-tivi/src/app/shell.html` | Empty-publication and trial channel display. |
| `web/vizhi-tivi/src/app/shell.spec.ts` | Empty-publication/trial rendering and isolated startup tests. |
| `web/vizhi-tivi/src/app/shell.ts` | Select the honest empty-state message. |

Total: 61 changed/new/deleted files; all changes are unstaged. No release version file is changed.
