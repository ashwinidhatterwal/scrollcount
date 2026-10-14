# ScrollCount 1.2.9 — bounded background accessibility reader

Version: 1.2.9, build 27. Based on latest GitHub main 5b9c20c5649ac5682e2788cbc6ea62b2903c3282. That commit's GitHub Build ScrollCount run 38420685484 passed. The new source has not been pushed; the connected integration previously denied write access.

## What was found

The service previously called getRootInActiveWindow(), event source queries and recursive screen-tree/footer reads from its main Handler. Exception guards cannot prevent a main-thread stall caused by slow accessibility IPC. This is an identified architectural risk, not a confirmed reproduction of the affected phone's accessibility switch turning off.

## What changed

- A single background reader handles screen roots, window metadata, source nodes and footer labels. Counting, sessions, timers and badge rendering remain on the main thread.
- Pending work is bounded at 24 jobs. Repeated snapshots/footer checks replace pending copies; distinct scroll events remain ordered within that limit. Every accepted, replaced or cancelled event copy is released.
- Read results are fenced by the current package and invalidation epoch. App switches, screen-off, interruption, pause/consent withdrawal, history reset and service destruction reject old results. They cannot resume a stale session or recreate deleted history.
- A two-second per-active-read timer pauses observation and queues up to three recovery attempts. It does not spawn replacement threads or attempt to kill an Android Binder call. A stuck reader cannot block the main thread, but counting must wait until useful screen data returns.
- Traversal has a time budget as well as node/depth limits. Invisible wrappers may expose visible children; invisible nodes no longer consume the visible-node allowance, while a separate visited-node cap limits their traversal. Incomplete samples cannot count.
- If the active root is unavailable, the service tries the active/focused application window with the expected window ID and exact target package. The same fallback handles ScrollCount's own display-only badge masking the active root on an OEM device. It is never used to inspect a hidden player behind an unrelated foreground app.
- An indexed Instagram player can be counted without caption labels if player structure, controls and full-screen geometry are independently confirmed. Anonymous/generic indices are still rejected.
- Added finite expanded-hierarchy recovery, Android 12 hierarchy compatibility, two-row YouTube footer handling and the known Shorts page container as a movement source.
- Kept the previous health log, fresh-install badge default and app profiles. No new Android permission, wake lock, foreground keepalive or permanent screen-polling timer was introduced.

## Validation and limits

Counting, identity, timing, history, crypto, sharing, job, privacy, metadata, health and backend tests pass. The reader test checks a blocked reader while admission/cancellation stay responsive, burst coalescing, queue bounds, exact release, cancellation and reader/release fault recovery. A test invokes the actual service admission helper with mocked preferences and verifies rejection of old epochs/packages, pause, consent withdrawal, reset and destruction. This test does not bootstrap Android or exercise device IPC.

Android resources, Java and DEX compile with SDK 36, and debug APK signature verification passes. Full local Gradle lint remains unavailable because two Gradle distribution JARs are damaged. New framework calls used here are available below the app's minimum API 26, and the previously corrected API-30 diagnostic guard is retained. GitHub Actions still runs lint; a successful run on the new commit must be checked before release.

No physical-device endurance test or affected-device diagnostic report was available. The change cannot silently re-enable accessibility, override manufacturer service restrictions, count UI information an app does not expose, or guarantee correct operation on every phone. Lite/Facebook/Snapchat detection still requires real accessible-layout validation as recorded in the earlier notes. Queue saturation, inaccessible labels and extremely fast transitions can cause missed items; guessing counts is deliberately avoided.

## Install and test

Upload this complete source to GitHub and build. Upload the signed AAB through Play Console's internal testing track to update a Play-installed app without signature conflicts. Use a Play-signed universal APK if testing by direct APK install; the locally debug-signed APK cannot replace the Play installation.

On each affected phone, test at least 20 slow and 20 quick transitions, comments/sharing, ordinary feeds/Stories, lock/unlock, switching apps, backgrounding ScrollCount and a 30-minute session. Compare manual counts with Today and the badge. If it fails, reopen ScrollCount and share Settings → Open diagnostics → Share sanitized report, together with the exact phone model, Android version, social app version and whether Android's Accessibility switch is off or remains enabled. This evidence is needed to identify any remaining OEM shutdown, ANR or layout-specific failure.
