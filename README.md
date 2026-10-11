Public support/privacy contact is configured as **ashwinidhatterwal@gmail.com** in this package. App/backend metadata and the static privacy page have been regenerated. Backend deployment is still required. Contact configuration commands below apply if you change the address later.

# ScrollCount 1.2.4-rc1

Native Android Reels/Shorts counter, local history and optional encrypted Friends sharing. Android 8.0+, application ID `in.scrollcount.probe`.

## Get started

First configure your real public contact: `python3 tools/configure_release.py --email YOUR_EMAIL`. The operator defaults to Ashwini Dhatterwal. Signed releases and backend deployment require this field; it is configured in this package.

Upload the contents of this folder to your GitHub repository root, including `.github`. Follow [GITHUB-TERMUX-SETUP.md](GITHUB-TERMUX-SETUP.md) to create your own key and configure signed APK/AAB builds. Follow [PLAY-PUBLISHING.md](PLAY-PUBLISHING.md) before Play submission.

The live badge now shows one combined daily **Scrolls** total across both feeds. Android API-28 UI calls have guards for Android 8. Public privacy/deletion pages are included in the backend at `/privacy` and `/delete`, with a dedicated saved deletion key usable after uninstalling. Backup and device-transfer rules exclude history and credentials.

The refined midnight-blue, ivory and turquoise Scroll Counter icon is integrated into launcher and onboarding resources. Master and 512px store artwork are in `branding/`. Counting and sharing logic are preserved. The onboarding privacy summary now correctly describes optional sharing.

Counting requires separate Accessibility disclosure/agreement and Android settings enablement. Counting is offline; Friends sharing is optional. Daily totals include confirmed items and quick skips. Seen means at least 2 seconds and longer view at least 10 seconds; these are exposure estimates. Social app layout changes can affect accuracy. See TEST-GUIDE.md and POWER-AND-INVITES-1.2.2.md.

Automatic friend uploads are batched at two-minute intervals while the process runs. Friends refreshes every 30 seconds while visible; hourly background jobs are a fallback that Android may delay. Backend deployment instructions are in backend/README.md.

## Build

JDK 17, Android SDK 36, build-tools 35.0.0, Gradle 8.13 wrapper and AGP 8.10.1.

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :app:lintDebug
```

GitHub builds a debug APK automatically. Four signing secrets enable signed release APK/AAB builds. No private key is included. Use the GitHub workflow for production; tools/build-sdk.sh produces temporary test-signed APKs.

## Validation

Counting, encryption/invitations, SQLite/history, privacy, automatic/hourly sharing, default migration, Termux deployment and Worker regression checks passed. All app Java compiled against API 36. XML and archive contents were checked.

Full Gradle/lint and signed APK/AAB verification remain pending: Android Gradle Plugin resolution is blocked in this environment. The SDK resource/Java/DEX path works with PNG crunching disabled. The supplied GitHub workflow runs tests and build/lint on Ubuntu. Physical phone tests, Android 16 behavior, battery measurements and Play review remain required.

PRIVACY-POLICY.md explains the implemented data practices; verify the deployed public contact before publication. See RELEASE-AUDIT-1.2.4.md for exact verification and remaining gates. Local history excludes captions/messages/media. Optional sharing stores server metadata and encrypted totals. Backups are disabled. Earlier notes are historical; this README and setup/publishing guides define the current version and signing procedure.
