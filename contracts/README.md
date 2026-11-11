# Authoritative catalogue contract

The external API belongs to [actionanand/vizhi-tivi-data](https://github.com/actionanand/vizhi-tivi-data/tree/master). Its [client integration guide](https://github.com/actionanand/vizhi-tivi-data/blob/master/docs/client-integration.md), publisher and public schemas define v1. The client does not redefine that API as an embedded-stream/integer-revision format.

- `catalogue-v1.schema.json` and `streams-v1.schema.json` are exact upstream copies, pinned by the integration fixtures and a Python byte-comparison test.
- `legacy-device-catalogue-v1.schema.json` describes the older internal/development/private format. It is retained for existing private records and legacy cache migration, not fetched as the official API.
- `backup-v1.schema.json` remains a future private backup design and references the private channel shape. No backup transport or Dropbox UI is enabled.

## Production publication

The default manifest URL is `https://actionanand.github.io/vizhi-tivi-data/manifest.json`, configured once in `app-config.json`. Android receives it through BuildConfig; Angular imports that configuration. Settings can override the full HTTPS manifest URL for a mirror/test environment. Previously stored blank endpoints use the default.

The manifest declares the channel and stream catalogues, locale files (`en`, `ta`, `sa`) and both public JSON Schemas. `contentSha256` hashes the publisher's UTF-8 `JSON.stringify({catalogue,streamDoc,localeDoc,schemaDoc})` structure. It is a publication identity, not a file checksum, timestamp or monotonically increasing revision. Object insertion order in that wrapper matters; ordinary keys must not be alphabetically sorted.

Both adapters fetch the manifest, all seven payload documents, and the manifest again. They validate supported schema versions, v1 paths/counts/numbering/locales, all channel and stream fields, unique IDs/numbers/references, owner/reference consistency, provenance, HTTPS URLs and the full digest. A changed closing manifest or a mismatched digest rejects the update. A validated cached snapshot with the same digest only needs the first manifest request. Unsupported formats/status values and missing/cross-channel/orphan references fail closed; no partial channel deletion is accepted.

## Adaptation

Public `streamIds` join the separate stream catalogue. Stable IDs and published numbers are preserved exactly; surviving official IDs cannot silently change their number. ISO 639-3 `tam`, `eng`, `san` map to internal `ta`, `en`, `sa`; other channel-language codes remain intact. UI locale identifiers stay exactly `en`, `ta`, `sa` and are not changed by catalogue refresh.

`broadcaster`, nullable `region`/`logoUrl`, and `sourceUrls` map to the existing provider/region/logo/source fields. Published stream IDs, verification dates and rights-evidence URLs are retained. The publisher only exposes rights-approved streams; the adapter preserves `verified` and `experimental` status, prefers a verified candidate, and labels selected experimental candidates as trials. These are policy eligibility decisions; device codecs, geography, browser CORS and web DASH limitations remain separate runtime constraints.

Official snapshots are cached together, revalidated on restoration and replaced only after successful validation and persistence. The old integer revision field is `null` for official snapshots and remains solely for legacy/private decoding. Network, integrity, compatibility and storage failures retain last-known-good metadata. A valid empty official publication replaces older official channels with an honest empty state. Legacy development caches are ignored, and no local development fixture is loaded or bundled in production.

Private M3U records remain in their existing separate device/origin store. Official requests contain only fixed publication paths, never playlist records or private stream URLs. Reconciliation clears conflicting private display assignments in memory; official refresh does not rewrite private playlist bytes or publish them.

## Fixtures and provenance

See [official-v1 fixture provenance](fixtures/official-v1/README.md). The `empty` files are the actual gh-pages output; `synthetic` uses the pinned upstream `publicView()` publisher with explicitly synthetic example-domain inputs. `vectors.json` supplies common expected IDs, numbers, languages, verification states, preferred stream IDs and trial flags for Kotlin (both flavors), Angular and independent Python tests. These files are test-only. The older `catalogue-v1.json` and M3U fixtures also remain test-only.

Upstream schema/locale copies retain the data repository's [license](fixtures/official-v1/UPSTREAM-LICENSE). No real approved broadcast URLs or private playlists are added by these fixtures.
