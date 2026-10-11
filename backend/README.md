Public support/privacy contact is configured as **ashwinidhatterwal@gmail.com** in this package. App/backend metadata and the static privacy page have been regenerated. Backend deployment is still required. Contact configuration commands below apply if you change the address later.

# Backend v4 — privacy and external deletion

Configure the real public contact in the project root before deployment:

```bash
python3 tools/configure_release.py --email YOUR_REAL_EMAIL
```

Then run `python3 backend/deploy_termux.py` from the project root. It requires your own Cloudflare API token at a hidden prompt, reuses the existing deployment IDs and never saves the token. The update preserves existing profiles/friendships and adds the deletion-key table/indexes. It uploads all three modules: `worker.mjs`, `public-pages.mjs`, `release-info.mjs`. Do not upload only worker.mjs. Schema changes and deployment must both complete.

The helper and Wrangler config disable application request observability. Do not enable payload/header logging or Logpush without revising the policy and review. Cloudflare still processes provider security/network metadata; D1 Time Travel is automatic (7 days on Free, up to 30 on Paid). No operator-created long-term backup is configured by this source. If using exports/additional retention, disclose it before release. Reapply all post-restore account deletions before returning a recovered database to service.

After deployment verify `/health` reports version 4 and `privacyReady: true`. Open `/privacy` and `/delete` without an app/login. Privacy/deletion HTML returns 503 until a real email is configured. Existing clients and saved private deletion keys remain supported; new clients get a separate deletion-only key, hashed in storage and unable to read account data. Save it privately from Friends before uninstalling. Contact-based lost-key requests require manual ownership verification, not deletion by display name alone.

Add your Play app-signing certificate fingerprint(s) to `release-config.json`, run the generator and redeploy for verified App Links. The public pages and association document bypass the API rate limiter so policy/deletion instructions remain accessible; deletion API operations are still rate-limited. HTTPS invitations keep confirmation inside the app.

## Earlier deployment and service notes

The following older notes remain useful for account IDs, schema and rate-limit setup. Version 4 instructions above supersede references to earlier versions or editing certificate constants.

# Backend v3: invitation links + encrypted snapshots

Deploy over your existing ScrollCount database using `python deploy_termux.py` in Termux. New app invitations are HTTPS links. This Worker serves the invitation landing page and Android app-link verification file. Existing profiles, friendships and snapshot APIs remain compatible.

After deployment, `/health` returns version 3 with `snapshots: true` and `inviteLinks: true`. Install 1.2.2-rc1 on both phones, then share a new invitation. Existing SC1 codes remain usable in the app. This release needs no new database tables beyond the existing additive schema.

If distributed through Play App Signing with a new signing certificate, add its SHA-256 fingerprint to the `sha256_cert_fingerprints` array in the asset-links response. The included certificate matches the currently distributed APK.

# ScrollCount Friends backend 2

This update stores the latest recipient-encrypted daily totals, so phones do not need to be online together. Existing profiles, invitations and friendships are preserved. Legacy signaling remains available for old clients, but both phones need the new app and consent for snapshot sharing.

## Update from Termux

Download `ScrollCount-Friends-Backend-v2.zip` to Downloads. Use the same Cloudflare account and existing ScrollCount database ID; do not create a second database.

```sh
pkg install python unzip
termux-setup-storage
mkdir -p ~/scrollcount
unzip -o ~/storage/downloads/ScrollCount-Friends-Backend-v2.zip -d ~/scrollcount
cd ~/scrollcount/scrollcount-friends-backend
python deploy_termux.py
```

The helper reuses `deployment.json` if present, otherwise asks for the account ID and existing database ID. Enter your Cloudflare API token at its hidden prompt. Token permissions: account Workers Scripts Edit and D1 Edit. It is never saved or included in the app. The helper applies additive `CREATE IF NOT EXISTS` schema changes, updates only Worker `scrollcount-friends`, enables its workers.dev URL and schedules retention cleanup every ten minutes. It does not modify your other Workers. Namespace 16001 must be reserved for this application's rate limit.

Open your `/health` URL. Expected: `service: scrollcount-signaling`, `version: 2`, `snapshots: true`. Install ScrollCount 1.2.0-rc1 on both phones. Existing users: Friends → Enable reliable sharing → Agree & enable. Keep the current backend address. Open Friends once on each phone so public keys can register and totals upload. Thereafter each phone uploads/downloads independently.

## Privacy and reliability

Server stores names, hashed device credentials, accepted friendships, invitations, public encryption keys and the latest AES-GCM ciphertext per friend direction. It does not receive plaintext counts, captions, creators or local history. Each phone uses P-256 ECDH, HKDF-SHA256 and AES-256-GCM with both profile IDs authenticated. Private keys stay in app-private storage with Android backup disabled. Initial public keys are trusted from the backend and then pinned; there is no independent identity verification or protection from malicious initial key substitution. Static keys do not provide forward secrecy.

Encrypted snapshots expire after seven days, and expired data is never returned. Hourly uploads refresh unchanged totals too. Friends shows the source date and time; stale updates are not live data. Android may delay jobs due to battery restrictions, connectivity or force-stop. Friends also syncs on opening and approximately once per minute while visible. No exact alarms or always-running foreground service.

Pause stops future uploads; existing snapshots remain fetchable until expiry, friend removal or profile deletion. Removing a friend deletes both directions on the server. Deleting a profile removes its keys, encrypted snapshots, relationships, invitations and legacy signals; local counting remains. Already cached copies on friends' phones cannot be remotely erased. Reinstall loses device credentials and private keys; there is no recovery or multi-device identity.

Invitations are single-use, valid 24 hours, with a maximum of eight friends. Cloudflare request/D1 quotas still apply. Updates can require up to eight writes per uploading user, plus reads. The best-effort rate limit is 60 requests/minute per IP; shared networks can hit it. Cloudflare logs/backups are controlled by the account operator.

## Verification

`node test.mjs` requires Node with built-in SQLite. It executes actual Worker SQL using a D1-compatible adapter: legacy compatibility, authentication, pairing, immutable keys, bounded ciphertext, recipient isolation, latest-only storage, expiry and deletion. App tests verify authenticated encryption and background job lifecycle. Physical Android/two-phone tests and production D1 deployment remain required.

Before Play submission update your published privacy policy, Data Safety and deletion URL with the actual operator/contact. This candidate is not a Play approval guarantee.
