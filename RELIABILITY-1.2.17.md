# ScrollCount 1.2.17 (35): overlay false-exits, badge recovery, battery guidance

Based on 1.2.16. Code review only; no affected-device report was available. Not a guarantee for every phone.

## Changes
- **Overlay false exit.** If another package (floating sidebar, assistant, chat bubble, OEM overlay) owned the active window, `readPlayerRoot` declared the feed exited and wiped the session/baseline. It now looks for the target app's own visible application window first and only reports an exit when none exists. Full-player geometry checks still reject split-screen/floating windows.
- **Badge never recovered.** `PlayerIndicator` gave up permanently after one rejected `addView`. It now retries after 20 s, so a transient overlay failure no longer hides the badge until the service restarts.
- **Help text.** "If counting stops" now lists OEM battery/auto-start steps and the Android 13+ Restricted-settings step for sideloaded APKs.

## Not changed (needs real traces)
Instagram/YouTube resource IDs, geometry thresholds, 1.5 s source expiry, 2 s read watchdog, 1 s traversal budget. OEM shutdown of Accessibility cannot be prevented by the app.

## Verification
Pure-Java regression suite (`tests/run.sh`) passes locally. The service-pipeline/Gradle/lint steps need the Android SDK and run in GitHub CI.
