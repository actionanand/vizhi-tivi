# Broadcaster channel discovery — 2026-11-13

This is an **identity-only research inventory**, not an authorized, verified playable stream catalogue. No third-party URLs or tokens are committed. Do not add non-playable candidates to the production catalogue; place only broadcaster-authorized, validated in-app playable streams in the official data repository.

## Source backup audit

The user-provided private `.vztv` backup has **7,260** channel records and **7,191** stream candidates, of which **7,118** records carry the unknown language code `und`. This code is not a visible language name. The Web and Android apps now display real language names when known (e.g., Tamil, English, Hindi, Malayalam, Telugu, Kannada and Sinhala) and a localized "Unknown language" label when metadata is genuinely missing. Do **not** fill unknown languages by guessing from a channel's name.

## Research expansion

Compared with the uploaded backup, a conservatively name-matched list of 105 broadcaster/operator documented non-news channel identities yielded **15 already listed** and **90 missing channel identities**, as of November 13, 2026:

- **Sri Lankan Tamil** — 4 missing: Vasantham TV, Nethra TV, Star Tamil, Athavan TV. Shakthi TV already occurs as a candidate, with rights and technical playback still unverified by us. Athavan is a UK-based Tamil digital broadcaster, **not** classified as Sri Lankan terrestrial television.
- **Sun Network** — 35 missing across Tamil, Telugu, Kannada, Malayalam, Bengali, Marathi and Hindi. This includes Sun TV, Sun Music, Adithya TV, Chutti TV, Sun Life, Gemini, Udaya and Surya families.
- **Zee Entertainment** — 24 missing, including Zee Tamil (SD), Zee Thirai, Zee Cinemalu, Zee Keralam, Zee Picchar, Zee Sarthak, Zee Punjabi and others.
- **JioStar (Star/Vijay/Asianet/Maa)** — 27 missing, including Star Vijay, Vijay Super, Asianet, Star Maa, Star Suvarna and Star Jalsha channel variants.

The matching is **identity-based**, not a proof that available stream candidates genuinely show the named channel. HD and SD names are distinct. Cross-border channels and similarly named foreign stations should not be conflated.

## Primary sources

- Sri Lanka TRCSL 2026 operator/channel listings: https://www.trc.gov.lk/content/files/tariff/tariff2026/PEO%20packges%20for%204G%20LTE%20Broadband%20Packges.pdf
- Sri Lankan Tamil television provider and broadcaster: https://www.vasantham.lk/
- Shakthi/Vasantham/Nethra TV operator lineup: https://dialog.lk/notices/dtv/silver?language=ta
- Athavan TV's own website: https://athavantv.com/
- Sun Network television channels: https://www.sun.in/television.aspx
- Zee Entertainment's 2026 channel schedule: https://assets-prod.zee.com/wp-content/uploads/2026/03/ZEEL_RIO_DAS-HITS-DTH_NTO-4_V-4.pdf
- JioStar portfolio: https://www.jiostar.com/portfolio/

## Verification rules

1. Review identity, language, country and network against an official broadcaster or authoritative operator.
2. Discover streams only from licensed/authorized sources, including authorized embedded playback.
3. Test actual in-app playback on Web, Android mobile and TV; do not treat HTTP 200 as proof of video playback.
4. Never copy DRM-protected, subscription-only, tokenized or third-party playlist URLs into public data.
5. Exclude TV news channels, as requested.
6. Keep research-only metadata separate from published `vizhi-tivi-data`; never use unknown/unreviewed metadata to imply a playable or permitted channel.

The full missing-identity file is available separately as a **metadata-only private VZTV collection**, not committed to the repository and not merged with the user's original backup.
