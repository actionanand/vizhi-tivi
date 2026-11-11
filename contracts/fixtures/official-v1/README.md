# Official API v1 integration fixtures

Source repository: https://github.com/actionanand/vizhi-tivi-data

- `master` source commit: `662e34c976b2cfeb7fc14478dea132d74d23916a`
- Actual `gh-pages` publication commit: `fb70679961bae32bdb9411dcd800f6e003561e44`
- Empty publication digest: `64fcd16817abc546d7d244c47cbe1b3afb82d9105b47f6ab117370b500feedf4`

`empty/` is a byte-for-byte copy of the eight published JSON documents at the publication commit. The source commit's `publicView(loadAll(...))` reproduces that digest.

`synthetic/` was produced by the same pinned upstream `scripts/lib.mjs` `publicView()` function, with in-memory synthetic channels/numbering and rights-approved test records. No source/data branch was edited. The four synthetic channel IDs, assigned numbers and languages are fixed; five stream records exercise verified preference, experimental trials, extensionless URLs, HLS, DASH and progressive metadata. All evidence/stream URLs use reserved documentation example domains. They are not working or officially approved broadcasts.

`vectors.json` stores those raw documents and independently specified expected client projections. Kotlin mobile/TV and Angular compare IDs, numbers, mapped channel languages, joined stream IDs, statuses, selected candidates and trial flags to the same expectations. Python independently checks the publisher digest and fixture copies. Negative tests mutate payloads and recompute valid test digests where needed to exercise validation beyond checksum rejection.

Production Android assets and Angular public output do not include these fixtures. Runtime clients load only the configured official manifest or a user-selected HTTPS mirror. Imported private playlists are never part of a publication.

See `UPSTREAM-LICENSE` for the upstream schema/locale license notice. Broadcasting rights are not granted by that software license.
