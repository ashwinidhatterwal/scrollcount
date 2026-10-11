# ScrollCount 1.2.2-rc1

## Battery changes

- New local counts still trigger uploads automatically. The first attempt waits about two seconds; later automatic background uploads are batched with at least two minutes between completed attempts. Open Friends continues refreshing every 30 seconds. Hourly Android jobs remain enabled according to the user's saved preference.
- Unchanged, previously authenticated incoming envelopes bypass repeated ECDH/AES-GCM decryption. Changed data is verified again; malformed/tampered data never replaces good totals. The cache is bounded by the friend list, held in memory, and cleared on sharing configuration changes.
- Identical saved friend cards no longer cause repeated preference writes.
- Full dashboard statistics and one-second redraws run only on Today/Insights. Friends receives count-change callbacks and updates timestamps once a minute rather than checking every five seconds.
- The seven-day chart only regenerates its description and redraws if its dates/counts change.
- The badge caches its accessibility window ID until recreated rather than allocating a new accessibility node for each window check.
- YouTube detection timing, retry limits, and counting decisions are unchanged. Retries already stop after a confirmed transition. Reducing these requires device profiling and accuracy testing.

The two-minute interval implies at most about 30 automatic background attempts/hour under continuous activity, compared with about 120 previously. This is a request-rate comparison, not a measured battery saving. Android/process scheduling can delay uploads; hourly work provides another opportunity after activity stops.

## Friend invitations

New invitations are HTTPS links, for example:
`https://scrollcount-friends.actionbridge.workers.dev/invite/<single-use-code>`

The recipient opens Friends directly and sees the inviter's name in an authenticated confirmation. No link auto-accepts a friend. A new user supplies their name and agrees to sharing first. Invitations retain existing 24-hour expiry and single-use semantics. Old SC1 invitations remain valid via manual paste. Preview crawlers cannot consume invitations.

The backend serves an Android Digital Asset Links association for the current distributed APK signing certificate and a browser fallback with an Open ScrollCount button. The app declares the production HTTPS host and a custom scheme for browser fallback/alternate workers.dev servers. Every link is validated before contacting its server.

## Update the existing backend in Termux

Download `ScrollCount-Friends-Backend-v3.zip`, then run:

```bash
unzip -o ~/storage/downloads/ScrollCount-Friends-Backend-v3.zip -d ~/scrollcount
cd ~/scrollcount/scrollcount-friends-backend
python deploy_termux.py
```

The helper reuses deployment.json if present. Otherwise enter the existing Cloudflare account ID and database ID. Enter the API token only at its hidden prompt. No new database is needed; profiles/friends are preserved.

Health should show version 3, snapshots true and inviteLinks true. The helper uploads the verification association as part of worker.mjs; no separate website or domain is required.

Install the APK on both phones without uninstalling. Create a new invitation, share it, and tap it on the friend's phone. If a messaging app opens an embedded browser, use Open ScrollCount. Android may need time to verify the host after deployment/install. App settings → Open by default → supported links can help diagnose verification. If Play App Signing later signs the app with a different certificate, add that SHA-256 certificate to the backend's APP_CERT association before distribution.

## Verification and remaining checks

Automated: counting/detection, recovery, reset/migrations, daily totals, encryption/authentication, new-data upload batching, in-flight changes, pause cancellation, unchanged-snapshot decryption skip, tampering, newer snapshot application, hourly-job lifecycle, defaults migration, invitation parser/unsafe-link rejection, backend acceptance/access/revocation/expiry, non-consuming landing page, verification JSON, and deployment helper. APK compilation/signature verification succeeds.

Not verified here: physical two-phone invitation opening and background delivery, phone UI rendering, and actual battery consumption. Android may delay background uploads; force-stop prevents them until reopening.
