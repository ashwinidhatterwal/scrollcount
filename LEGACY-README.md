# Historical documentation — superseded by README.md

# Current release: 1.2.2-rc1

See POWER-AND-INVITES-1.2.2.md for battery improvements, tappable invitations, backend deployment and validation.

# UI and sharing update

Aligned navigation labels, By App below the daily total, concise Friends screen with a top-right menu, animated comparison bars, and change-triggered encrypted uploads. Existing sharing profiles default to reliable sharing and hourly sync once on upgrade; paused sharing stays paused, and subsequent menu choices are preserved. New profiles still use the profile setup agreement. Changes are batched with at least 30 seconds between completed automatic attempts while the app/accessibility service process runs. Incoming snapshots refresh every 30 seconds while Friends is visible, and hourly background sync remains the fallback. Android can delay background work. Backend v2 is unchanged.

# ScrollCount 1.2.1-rc1

Encrypted saved friend updates replace direct-only sharing. See backend/README.md for Termux deployment and consent migration. Counting stays offline and unchanged. Hourly background jobs can be delayed by Android; offline friends see the latest uploaded snapshot with its date/time.

# Local counting and build instructions

An offline Android counter for Instagram Reels and YouTube Shorts. This combined implementation includes the remaining product, performance, privacy and optional wellbeing phases. It is a release candidate for phone testing, not a claim of finished multi-device validation.

## Install

Install the included APK over v0.2.0 or earlier builds. It uses the same application ID/signing certificate, with versionCode 16. Your history is retained; SQLite upgrades to version 3 preserve previous records and add durable confirmed-item records for the live daily badge. The delivered APK is not debuggable but retains the existing test signing identity so updates work.

Open **Today → Set up counting** if needed. Read the separate Accessibility disclosure and explicitly agree, then enable the service in Android settings. Existing consent remains valid because observation scope is unchanged. Use Reels/Shorts normally; the app can stay closed.

## The finished interface

- **Today:** readable count, feed time, finished quick skips, platform shares, interactive seven-day trend, average exposure, longer views, longest session, daily intention and lifetime totals with a start date.
- **Friends:** optional private invitations, last-received daily totals and a friendly comparison sheet; requires the included signaling backend to be deployed.
- **Insights:** seven-day summary, previous-period comparison, recent daily baseline, day details and up to 30 session breakdowns.
- **Settings:** pause/resume, light/dark appearance, optional live player badge, optional daily seen goal, optional session reminders, privacy, completed-history CSV export, deletion and opt-in local diagnostics.
- Separate onboarding and Accessibility consent screens; adaptive/themed app icon; readable contrast, accessibility headings, chart text alternative, large-font stacking and tablet-width limits.

No demonstration totals or fake history are inserted. Empty states explain what to do next.

## Meaning of the numbers

Seen means >=2 seconds in a confirmed feed. A finished exposure <2 seconds is a Quick skip; it is not temporarily counted as a skip while still being viewed. Longer view means >=10 seconds, and is a subset of Seen. Returning to an item can count again. These are exposure estimates, not proof of playback completion. Buffering in a confirmed feed may be included.

Today follows the phone's local date. Items spanning midnight belong to their starting day. Average per item includes skips. Session duration sums active feed time rather than the whole wall-clock interval. Comments/overlays, app switches and screen-off pause or finalize timing when detected. PiP and small split-screen players suspend counting.

## Lifecycle and performance

Completed exposures are durable. Event-driven checkpoints replace one active metadata row after the active duration changes sufficiently (normally around 15 seconds while events arrive), and when a suspension is detected. Recovery commits only that saved duration once, then closes interrupted sessions; downtime is never counted. Silent periods or sudden death before a checkpoint can still lose recent time.

Database operations use an ordered background executor. Dashboard reads are cached by revision and local day; UI refresh runs only while visible. Unsupported package events return before inspecting their UI. Full scans are bounded/coalesced; YouTube fast checks are bounded after player movement. Indexed Instagram transitions use structural author/caption evidence without an extra content traversal. Tree serialization is skipped unless a debuggable build has explicitly enabled diagnostics. Optional reminders are event-driven; no alarm, wakelock or extra foreground service is used. Optional Friends sharing adds bounded hourly network jobs; it does not alter counting.

Battery impact has not been measured on a physical phone. `tools/profile-device.sh` captures app memory/battery reports without resetting device state. Diagnostics include event volume and scan timing.

## Privacy

No Internet, storage, location, camera or microphone permission; no backend, account, advertising or analytics dependency. The only ordinary runtime permission is POST_NOTIFICATIONS, requested only when you choose a reminder on Android 13+. Accessibility is explicitly authorized separately.

Stored history contains platform, session, timestamps, active duration and category. No content fingerprint, caption, creator, URL, message, typed/password text, tree or media is written to history. Backup is disabled. Release builds never capture tree dumps. Diagnostics are opt-in memory-only metadata; sharing and CSV export happen only by user action. An export destination may be synced by the storage provider the user chooses.

See `PRIVACY-POLICY.md`, `RELEASE-READINESS.md` and `TEST-GUIDE.md`.

## Build and checks

The project remains dependency-free native Java/Android Views/SQLite. Platform observation, pure timing and UI/storage code are separate. Legacy component names preserve Android's installed Accessibility-service identity; they do not imply a probe-only product.

Use JDK 17, platform 35/build-tools 35.0.0 and the checksum-pinned Gradle wrapper:

```bash
bash tests/run.sh
python3 tests/history_sql_test.py
python3 tests/privacy_test.py
./gradlew :app:assembleDebug :app:lintDebug
```

The included GitHub workflow builds a debug APK by default. A manual release option uses your private upload-key secrets to produce a signed APK/AAB; see `RELEASE-READINESS.md`. Release signing keys are not in the source ZIP.

The delivered APK was built using:

```bash
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/sdk bash tools/build-sdk.sh
```

This creates a non-debuggable candidate signed with the retained local test certificate. Do not use that certificate as your public Play upload key. Gradle/lint, Actions and an AAB build were not executed successfully in this environment because dependency-network access is unavailable. Android resource/Java/DEX compilation, alignment and APK signature verification were completed.

## Remaining validation

Physical UI/font-scale checks, multi-device transition accuracy, real notification delivery, OEM reboot/lifecycle behavior, battery/CPU measurements and Play approval remain unverified. Repeated/unavailable YouTube labels and very fast transitions can still be missed. Social app updates can change accessibility layouts. See the supplied regression tests and the phone-test guide; source tests do not certify real app behavior.

## Live badge (1.0.3)

Enable Settings → Appearance → Live badge once. A small top-center touch-through pill appears in a confirmed full-size Reels/Shorts feed. It shows the detected total for that platform today, including the first item and quick skips, across all sessions. Leaving, reopening, pausing or switching platforms does not reset saved daily totals. Instagram and YouTube totals are separate.

The badge uses a durable confirmed-item record written when each item begins, independently of session timing. Existing completed exposures and active checkpoints seed these records during upgrade, without duplicates. A cached, serialized background aggregate is refreshed on item/history/day changes, not every UI event. No extra tree scans or continuous polling are added. DATE_CHANGED/TIME_CHANGED/TIMEZONE_CHANGED update local-date attribution, including while a feed remains open. An item spanning midnight belongs to its starting day, matching history: the new day starts at zero until a new item starts.

Comments/overlays, leaving a confirmed player, lock/off, pause, reset and service destruction hide the badge. Counts return from saved confirmed-item history. The Accessibility service owns the badge window; no SYSTEM_ALERT_WINDOW permission is added. Own badge events are filtered by window identity to avoid resetting the counter. Phone testing remains required for midnight behavior and OEM overlay handling.

## 1.0.6 UI and controls

Today now shows all confirmed daily items, matching the live badge, including quick skips. Viewing details and Insights keep the explicit 2-second watched threshold; they are not the same metric as the total. The daily total uses the existing detected-item table with one indexed aggregate per cached stats refresh, not extra accessibility scans.

First launch opens setup and defaults automatic counting on; disclosure acceptance and Android Accessibility enabling are still required. Existing pause preferences are preserved on updates. History deletion clears records while preserving the counting preference, invalidates queued detection state, and releases the reset gate even if deletion fails. The live badge background is 70% opaque (previously 90%), keeping the text readable. Native icon navigation, consistent full-width actions, concise status controls, and light/dark cards improve readability.

