# Shared application/data contracts

`catalogue-v1.schema.json` defines full official catalogue snapshots. `schemaVersion` is exactly 1, `revision` is a positive monotonically increasing integer. IDs are immutable, case-sensitive ASCII identifiers; names are display metadata. Official published numbers are authoritative: Tamil (`ta` in languages) 1–250, others 251+, or explicit `null` for pending assignment. Clients reject duplicate IDs, collisions, range violations, changed surviving assignments, older revisions and incompatible schemas. A rejected snapshot never partially replaces the last-known-good cache.

Streams require HTTPS, a declared format (`hls`, `dash`, `progressive`), review status and explicit permission. Only `approved` + `permitted: true` candidates are playable. Imported records use a separate private namespace and are never official approvals. Optional metadata includes description, logo and EPG ID. Names remain unchanged when the interface language changes.

`fixtures/catalogue-v1.json` is a development-only metadata example, not an official station list. Its public Angular copy must match byte-for-byte (Python regression test). Android reads this same source as assets. `playlist.m3u` and `playlist-vectors.json` are synthetic parser tests with reserved `.invalid` URLs; no working streams or private playlists are included.

Runtime decoders in Kotlin and TypeScript enforce the structural contract and cross-record invariants. AJV tests validate the machine-readable JSON Schema. Shared fixtures test decoding and SHA-256 import identities in both languages.

`backup-v1.schema.json` describes a future portable **private** backup payload. Its records may contain secret stream URLs. No export UI, remote transport or Dropbox OAuth integration is enabled. `PrivateBackupTransport` in each client establishes that future boundary.
