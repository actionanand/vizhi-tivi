# Stage 1 implementation

## Scope and startup

The native mobile and TV product flavors and Angular client now have channel browsing, search, language/category/country filters, favourites, recent history, private M3U import, settings and foreground playback paths. The initial catalogue is an explicitly labelled, two-record metadata fixture. It supplies no broadcasts. Configure a reviewed HTTPS JSON endpoint in Settings and explicitly refresh, or import a playlist the user is permitted to watch. No production endpoint for the planned `vizhi-tivi-data` repository is assumed.

This is an MVP foundation with outstanding device acceptance checks, not a production release. There are no movies, series, VOD, recording, downloads or background playback services.

## Shared Android source and UI

One `android/app` module retains `mobile` (`com.actionanand.vizhitivi.app`) and `tv` (`com.actionanand.vizhitivi.tv`) flavors, independent version files and existing launcher placeholders. Debug IDs retain the `.debug` suffix. `BuildConfig.FLAVOR` selects device layout, so each installed flavor always opens its intended UI. Only the TV-flavor manifest advertises Leanback hardware and its banner; launcher categories remain flavor-specific.

`core/Domain.kt` contains shared models, immutable identifiers, allocation, playlist parsing and the future backup transport interface. `CatalogueCodec.kt` validates official snapshots. `Repository.kt` handles bounded network reads, catalogue caching, document reads, structured private records and DataStore preferences. `LiveViewModel.kt` serializes user operations, exposes StateFlow and retains previous data on failure. Dependency injection is a direct repository constructor; no DI framework or duplicate phone/TV data implementation is needed.

`ui/LiveApp.kt` uses Material 3 for mobile and Compose for TV buttons with their focus states for TV. TV uses larger cards, horizontal category rails and generous margins. Browsing scroll state is saved across the player, and returning requests focus on the selected channel card. Remote-selectable controls, text fields, filters and confirmation dialogs require no gestures. Mobile uses the document picker; TV offers URL entry and the same optional document picker, whose availability depends on device providers. No storage permission is requested.

The Media3 `LivePlayer` owns exactly one foreground player in the composition. Selecting a playable channel is required before any network playback. Back closes the player; disposal detaches the view/listeners and releases ExoPlayer. Backgrounding pauses without auto-resuming on return. Screen-on follows `isPlaying`, including pause, buffering and failure transitions. Media3 controls supply play/pause, seeking/live controls where applicable, audio/settings and subtitle selection when tracks exist. The full content area is the player while browsing is hidden; system bars are not forcibly hidden. Stream switching closes the previous composition/player before starting another.

## Persistence and migrations

Small preferences use DataStore: locale, appearance, favourites, recent/last-watched IDs, endpoint and the local-numbering opt-in. Recent IDs are capped at 30, most recent first. Startup restores metadata/history, without automatically starting a stream.

Catalogue and structured playlist records use separate `AtomicFile` JSON stores under app-private `filesDir`. This is justified for Stage 1's bounded snapshot/import operations: there are no cross-table queries, EPG timelines or large relational datasets. A validated snapshot is atomically replaced, and playlist replacements are written before publishing UI state. A relational Room migration should precede large guide/history workloads. Private records carry `schemaVersion: 1`; unknown versions fail rather than being silently migrated or overwritten during normal load. There is no prior structured user schema in this skeleton to migrate. OS-level backup remains disabled; Android 12+ extraction rules explicitly exclude private records from cloud backup/device transfer.

Web stores versioned device JSON and catalogue JSON in origin-local `localStorage`, with separate keys. Writes update reactive state only after storage succeeds. Quota/unavailable-storage failures are visible. Invalid private store versions preserve the original bytes and disable further saves for that session. This small-store choice has browser quota limits; do not use it for large EPG datasets. Browser storage can be cleared by the user/browser and is not a cloud backup.

## Catalogue refresh and numbering

See `contracts/README.md`. Refresh uses an 8-second connection/read timeout (web AbortSignal), at most two attempts with a short backoff, HTTPS and a 5 MB response cap. Android follows at most four validated HTTPS redirect hops. Web uses browser fetch with credentials omitted and no referrer. Server CORS rules apply. On failure the current catalogue and cache are retained, with a visible error. Cached metadata can be browsed offline; the web shell itself must already be loaded/cached by the browser (no offline service worker is claimed).

Full snapshots are intentional: a delta or incomplete record set must not be interpreted as an authoritative removal. Invalid records reject the whole update, rather than silently deleting a channel and recycling its number. Revision rollback, duplicate IDs, collisions and published number changes for surviving IDs are rejected. Official `null` numbers remain pending; clients do not invent official assignments.

The pure allocator preserves surviving assignments, visits new stable IDs in deterministic ASCII order, fills the lowest available gaps and leaves Tamil channels unnumbered after 250 slots. Removed IDs genuinely vacate their slots. Tests cover order/name changes, ranges, collisions, gap reuse, exhaustion and official reservations.

Private imports are unnumbered by default. The Settings opt-in assigns local numbers at import/reimport using the same allocator, reserving official and other private numbers. Newly published official numbers take priority; a conflicting local assignment becomes pending rather than changing the official catalogue. Language corrections clear that channel's old local number; reimport can allocate again. Changing numbering policy applies on the next import, not a silent bulk rewrite.

## M3U import and privacy

M3U/M3U8 channel lists require `#EXTM3U` and `#EXTINF`. The parser supports UTF-8/BOM, CRLF, names containing commas, quoted tvg-id/name/logo, group-title, tvg-language/language, and type/mime-type hints. Missing names fall back to tvg-name. HLS media segment manifests are not channel catalogues and will show an import error. Unsupported URLs and unnamed entries are ignored; an empty result rejects the import and retains the prior playlist.

IDs are `import.<SHA-256 playlist identity>.<SHA-256 tvg-id or stream URL>`. Duplicate tvg-id or stream URL entries within a playlist are collapsed. A stable tvg-id preserves identity across spelling and stream URL changes; without a tvg-id, a changed URL is a new identity. The first duplicate wins. Android playlist identity is the document URI or exact playlist URL. Web file import uses the editable playlist name as its reimport identity, while URL import uses the exact URL. Use the same identity to replace a playlist; a distinct identity creates a separate private playlist. Removal asks for confirmation and persists the removal. Playlist counts and imported labels stay visible.

Tamil candidates come only from explicit language metadata (`ta`, `tam`, `Tamil`, `தமிழ்`), including comma/semicolon lists. There is no name-based guess. Missing language is `und`; channel controls allow manual correction. Metadata groups/country/language strings are broadcaster data, not interface translations.

All imports remain on the device/origin; there is no upload, telemetry or proxy. App-private files and browser-origin storage are not encrypted against a compromised device or same-origin script. Playlist credentials may appear in query strings and must be treated as private. Embedded URL username/password, HTTP, file, data and script schemes are rejected. TLS verification is never disabled; Android cleartext remains disabled. A private import's playback flag means user-supplied content, never official catalogue review/approval. Respect broadcaster rights and device/browser limitations.

## Playback and web architecture

Native Media3 supports HLS, DASH and supported progressive containers/codecs; declared catalogue format and import MIME hints work without an extension. Codec support remains device-dependent. Automatic media load retries are bounded, and the UI supplies explicit Retry after terminal errors. No fallback to unrelated channels is attempted. The first eligible candidate is selected; automatic candidate health ranking/failover is deferred.

Angular keeps pure models/decoders/numbering/parser in `core/domain.ts`, persistence/network/localization in `core/store.ts`, lazy-loaded navigation/browsing in `shell.*`, and player ownership in `player.ts`. Signals derive filtered lists; TypeScript and templates use strict checking. Native video handles progressive and native HLS. A dynamically loaded, pinned hls.js handles HLS with Media Source Extensions. DASH currently reports a clear limitation rather than claiming support; use the native clients for DASH. Player disposal clears source, pauses video and destroys hls.js. Autoplay rejection leaves native controls available; it does not reconnect endlessly. Audio/subtitle selection is exposed for hls.js tracks; native browser controls vary. CORS, TLS, unsupported codecs and browser autoplay restrictions remain browser-enforced. No access-control workarounds or open proxy exist.

## Localization and branding

Exactly `en`, `ta`, `sa`. Native `values`, `values-ta`, `values-sa` and web locale JSON files cover navigation, common settings, import, errors and playback actions. Settings selection persists; Android resources and the web dictionary fall back to English. Channel names are preserved. Channel language filtering is independent of interface language. OS/browser fonts provide Tamil/Devanagari glyphs; rendering needs physical-device checks. Existing artwork is reused from `common/branding/`, with its placeholder quality limitations preserved. Theme supports light/dark/system.

## Future integrations and acceptance gates

The versioned private backup schema and `PrivateBackupTransport` interfaces establish a future Dropbox boundary. No OAuth credentials, Dropbox UI, server storage or pretend synchronization are included. Backup transport, export/import adapters and migrations from later schema versions need a separate integration stage.

Before release, check real permitted HLS/DASH/progressive streams on API 26+ phones and real Google/Android TVs. Exercise codec/audio/subtitle support, interruption/background/foreground, screen-on, network loss/retry limits, rapid switching and repeated player open/close. At 1080p, test rapid D-pad movement, every rail boundary, scrolling, focus restoration, Back and player track menus. At narrow phone/web widths, test long Tamil/Sanskrit labels, touch targets, fonts, contrast and text scaling. Perform an actual browser AXE audit and CORS/playback checks; unit tests do not establish visual or accessibility compliance. See `validation.md` for executed checks.
