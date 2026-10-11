# Release audit — 1.2.4-rc1 / versionCode 22

## Implemented

- Live badge shows **Scrolls**, the sum of today's durable Instagram and YouTube confirmed-item totals, including quick skips. The same value continues across platform switches; platform breakdowns remain in history and Friends.
- Minimum SDK lowered from 28 to 26 (Android 8.0). API-28 accessibility heading and package version-code calls are guarded; a compiled API-version audit found and fixed the previously unguarded getLongVersionCode call; newer insets/receiver/notification APIs retain their version guards. Java Base64 and notification channels set a practical API-26 floor without adding a compatibility dependency.
- Public `/privacy` and `/delete` HTML, operator/effective date/contact metadata, app privacy summary/website links and expanded sharing consent. Effective date: 2026-10-11. Operator defaults to Ashwini Dhatterwal. The user-supplied public contact is ashwinidhatterwal@gmail.com.
- One metadata generator keeps app/backend details synchronized. Incomplete contact metadata prevents signed release builds/deployment; policy HTML returns 503 until configured. CI accepts the public `SCROLLCOUNT_SUPPORT_EMAIL` repository variable as an alternative app-build input.
- Dedicated independent, hashed deletion-only keys; new profiles receive one, existing profiles can generate one and save it privately. The web page deletes without an app session. Key rotation, invalid keys, cross-origin requests and compatibility with earlier saved private keys are tested.
- Profile deletion uses one database transaction for profile, hashed deletion credential, friendships, invitations, public keys, incoming/outgoing snapshots and legacy signaling. The app's local delete action also accepts an already-deleted remote profile.
- Streamed backend request bodies stop at 32 KiB measured in bytes and reject non-object JSON. Expiry indexes reduce cleanup work. Public policy/association documents do not consume the API rate limit.
- Queued HTTP work checks the current sharing credentials before sending. Request redirects remain disabled; HTTPS and bounded responses remain required.
- Explicit cloud-backup and device-transfer exclusions protect credentials/history. Accessibility remains observation-only, consent-gated and non-tool-labelled; no control API or additional ordinary permission was introduced.
- Onboarding readiness checks stop after a short bounded window instead of polling indefinitely before permission is given.
- PNG crunching is disabled because the native cruncher crashed on the new artwork in this environment. The original compressed PNGs remain intact; resource compilation/linking succeeds.

## Completed verification

Java counting/detection, checkpoint, badge persistence, timing, invitation parsing, encryption/tampering and snapshot tests; SQLite migration/history; privacy boundaries and backup exclusions; actual sync coordinator/default migration/hourly job lifecycle with scheduling doubles; deployment transport tests; actual Worker SQL and public-page/deletion/body validation; metadata consistency and certificate/email rejection.

All app Java compiled against Android 36, DEX compiled with min API 26, all resources compiled/linked and a temporary unsigned bundle passed bundletool structural validation. No production signing key was generated. These checks do not substitute for phone tests or signed/Gradle validation.

## Required before submission

1. Public contact metadata is configured. Confirm the mailbox is monitored. Confirm operator identity and actual infrastructure retention/logging. The policy documents D1 recovery of up to 30 days (7 days Free) and Cloudflare's provider security records separately.
2. Run the full GitHub workflow with your signing secrets and verify APK/AAB and both lint tasks pass. Local Gradle was attempted but could not resolve the Android Gradle Plugin in this environment; full Gradle/lint validation remains pending.
3. Deploy all backend v4 modules and additive schema to your existing Worker; verify public URLs without login, health readiness, actual deletion/revocation, rate limiting and scheduled cleanup. No live backend deployment was performed during this source update.
4. Add the final Play app-signing certificate to metadata/App Links and redeploy. Verify HTTPS invitation → app → explicit friend confirmation on a recipient device.
5. Physical Android 8/9/current Android tests: startup, Accessibility consent/enable/disable, combined daily badge across both apps, midnight, pause/reset, screen-off/reboot, sharing, saved-key external deletion and battery/CPU measurements. Older Instagram/YouTube layouts are not certified by reducing minSdk.
6. Complete actual Console declarations, review video, screenshots, feature graphic, listing/contact, Data safety/content rating and testing requirements. Google decides Accessibility eligibility and app approval.

Earlier test signing identity differs from your new upload key. Follow the migration warning in GITHUB-TERMUX-SETUP.md; uninstall deletes history and there is no CSV import. Do not call the app fully production-validated until these gates have evidence.
