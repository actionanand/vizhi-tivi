# Starter limitations

This ZIP is a **basic project skeleton**, not a finished Live TV application.

- No real IPTV player, channel parser, catalogue service, Dropbox sync, storage or TV guide yet.
- No real data repository created; URL in `app-config.json` is reserved as planned identity.
- Web app is left ungenerated for your existing/basic Angular app.
- App icons and banner are temporary branded placeholders, not production artwork.
- Mobile and TV product flavors use **different app IDs** because you requested independent release-version branches; both use the same Kotlin source and Gradle app module.
- Gradle wrapper JAR is not bundled. `scripts/bootstrap.sh` can generate a wrapper when Gradle is installed.
- Android builds were not executed in this environment; validate the Gradle dependencies and Android tooling locally or with GitHub Actions before publishing.
- GitHub Actions workflows require a repository with the named branches, available secrets and permission to push version commits.
- App signing and catalogue rights remain your responsibility.
