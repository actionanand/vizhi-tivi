# Stage 1 limitations

The repository started as a skeleton. Stage 1 adds a usable browsing/import/player foundation; production and device acceptance remain separate.

- Native Media3 and web native/HLS playback, M3U import, local storage and catalogue refresh are implemented. No working broadcasts are bundled, and actual playback needs a permitted compatible stream and device/browser checks.
- No Dropbox synchronization, EPG guide, DRM handling, recording, downloads, VOD or background audio playback.
- The data repository exists and its published API is authoritative. At integration verification time its Pages URL returned 404, although `gh-pages` contained a valid empty publication. The client retains cached data on that failure; enabling Pages/deploying the data API is outside this feature task.
- Existing Angular 22 client is in `web/vizhi-tivi/`. Web DASH reports unsupported; browser CORS/codecs/autoplay restrictions apply. No offline shell service worker is included.
- App icons and banner are temporary branded placeholders, not production artwork.
- Mobile and TV product flavors use **different app IDs** because you requested independent release-version branches; both use the same Kotlin source and Gradle app module.
- Gradle wrapper JAR is not bundled. `scripts/bootstrap.sh` can generate a wrapper when Gradle is installed.
- See [validation.md](validation.md) for exact local build/test results and remaining visual/physical-device checks. Do not infer Google Play TV quality compliance from a successful APK build.
- GitHub Actions workflows require a repository with the named branches, available secrets and permission to push version commits.
- App signing and catalogue rights remain your responsibility.
