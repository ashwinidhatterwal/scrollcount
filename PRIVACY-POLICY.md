# ScrollCount privacy policy

Operator: Ashwini Dhatterwal · Effective 2026-10-11
 

ScrollCount counts your confirmed Instagram Reels and YouTube Shorts and estimates feed exposure time. Counting works offline. Friends sharing is optional and starts only after a separate agreement. This policy covers the default ScrollCount service; if you configure another sharing server, its operator is responsible for that server’s practices and contact details.
 

## Accessibility and local history

With your explicit agreement and Android Accessibility permission, ScrollCount briefly reads the visible Instagram/YouTube interface to recognise Reels/Shorts and transitions, including while ScrollCount is closed. Temporary content fingerprints are discarded. Other apps provide foreground/window package metadata only. Typed text, passwords and unrelated app content are not inspected.
 

The app stores platform, sessions, timestamps, active durations, categories and confirmed-item records on your phone. It does not save or upload captions, creators, messages, media or content URLs. There is no screen recording, automated tapping/swiping, advertising SDK or analytics SDK. Device backups are disabled.
 

## Optional features

The optional touch-through badge shows the combined daily Scrolls count. Optional reminders use notifications. Both are off by default. CSV exports and sanitized diagnostics go only to a destination you choose, whose own privacy practices apply. Diagnostic metadata is temporary in memory; release builds never capture accessibility trees.
 

## Friends and server data

If you create a sharing profile, the server stores your chosen name, random profile identifier, hashed authentication/deletion credentials, friendships, invitations and public encryption key. Daily Reels/Shorts counts, local date, sequence and capture time are encrypted separately for each accepted friend and stored as the latest encrypted update per recipient. Private encryption keys stay on your phone. Accepted friends can read your shared daily totals.
 

Sharing uploads automatically in small batches after saved counts and around each hour in the background; Friends refreshes every 30 seconds while visible. Android can delay work. Pausing sharing stops future uploads but does not immediately erase earlier updates. Remove a friend or delete your profile to remove server copies.
 

## Protection and providers

Transfers use HTTPS. Friend updates use P-256 ECDH/HKDF and AES-GCM encryption. Initial friend public keys are trusted from the server, then pinned; there is no independent verification or forward secrecy. The operator can see profile/connection metadata and encrypted data, but plaintext totals are not uploaded.
 

Cloudflare Workers and D1 provide hosting, storage, transport and rate limiting. Cloudflare processes network information such as IP addresses and request metadata for delivery and security. The supplied deployment disables application request logging and does not log credentials or payloads. Cloudflare may retain provider security/network records under its privacy policy. These providers may process data outside your country. ScrollCount does not sell personal data or use it for advertising.
 

## Retention and deletion
- Phone history remains until you delete it or uninstall the app. Reinstall loses local history and sharing credentials; CSV export is readable but cannot currently be imported back.
- Sharing profiles, friendships, credential hashes and public keys remain until profile deletion.
- Encrypted snapshots expire after 7 days, invitations after 24 hours, and legacy connection details after 2 minutes. Expired records are never served and are cleaned by the scheduled job, normally every 10 minutes.
- Profile deletion immediately removes its active database rows and related friendships, invitations, keys, snapshots and connection details.
- Cloudflare D1 recovery copies may retain deleted records for up to 30 days (7 days on the Free plan). They are not used in normal app operation. A recovery restore requires reapplying deletions before service resumes.
- Copies already received by friends or exported to another destination cannot be remotely erased.
 

## Your controls and contact

Pause counting in Settings or disable Android Accessibility. Delete local history in Settings. Delete the sharing profile through Friends → Sharing settings → Manage sharing or the public deletion page. Save your dedicated deletion key privately before uninstalling to delete without the app. For privacy/deletion requests or a lost key, contact ashwinidhatterwal@gmail.com; ownership verification may be needed. A display name alone cannot safely establish account ownership.
 

This policy will be updated when data practices change; changes requiring new consent will be disclosed in the app.

---

Public contact metadata is configured. Deploy backend v4 and verify the public URLs before Play submission.
