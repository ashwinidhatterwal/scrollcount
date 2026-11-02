# ScrollCount 1.2.27

Native Android short-video scroll counter (`in.scrollcount.probe`, `versionCode = 45`, `minSdk = 26`, `targetSdk = 36`) with a 24 FPS scroll-reactive animated avatar, local SQLite history, and optional end-to-end encrypted Friends sharing via Cloudflare Workers + D1.

## Build & Verify

```bash
python3 tools/configure_release.py --check
node tools/render_public_pages.mjs --preview
chmod +x gradlew
./gradlew :app:assembleDebug :app:lintDebug
```
