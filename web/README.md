# Vizhi TiVi web client

The existing standalone Angular client is in `vizhi-tivi/`. Run `npm ci`, `npm run lint`, `npm test -- --watch=false`, and `npm run build:gh` there. The production base href is `/vizhi-tivi/`. See `../documentation/stage-1.md` for the catalogue contract, private imports and playback limitations.

Private playlist changes and recovery use the browser's Web Locks API to coordinate same-origin tabs. This requires a secure context and a browser that provides Web Locks. If storage or locking is unavailable, private writes and recovery fail safely; export any available data and use a supported browser. The lock coordinates copies of this version of Vizhi TiVi, not older clients or unrelated software that writes the same storage keys. Private data remains in browser storage on this device; exports may contain private playlist URLs.
