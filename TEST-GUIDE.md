# ScrollCount 1.0.6-rc7 — phone test

Install over your current app to preserve history. Keep a CSV copy if desired. Test both apps separately, enabling Local diagnostics before a counting benchmark and sharing each report before switching platforms.

1. **UI:** inspect Today/Insights/Settings, light and dark themes, gesture navigation, rotation, a small screen and the largest Android font/display sizes. Labels/buttons must wrap without clipping. Day-chart taps and the accessible day chooser should show correct details. Empty history must not show fake totals.
2. **Counts:** baseline for 3 seconds, then 30 Instagram moves and 20 YouTube moves. Mix normal viewing and 0.5–1.5-second quick skips. Go forward five/back five. Seen includes initial >=2s exposures; finished skips are separate. Report actual transitions, detected transitions, false positives and misses, not just equal totals.
3. **Exclusions:** like, pause, expand caption, comments/share sheet, profile/Home/results scrolling, notification shade, app switch and screen lock. These should not add new video transitions. Outside time should be excluded. PiP/small split screen should suspend counting.
4. **Persistence:** close/reopen app, disable/re-enable Accessibility, restart device and update over an earlier version. Completed records must remain. Interrupt a long item after a checkpoint: recover only saved active duration once. Repeated reopening must not duplicate it. Force-stop requires reopening/re-enabling as appropriate on the phone.
5. **Stats:** check day/platform totals, skips and longer views, average/longest session, seven-day details and lifetime against a small recorded test. Midnight-spanning items belong to the day they start. Compare CSV completed records with history; active items are exported after finalization.
6. **Wellbeing:** set/remove a goal. Enable a 15-minute session reminder, accept/deny notification permission and disable its Android notification channel. The app must remain usable without reminders, disclose blocked notifications and send at most one reminder per session. Paused intervals must not advance the active-session threshold.
7. **Privacy/reset:** leave Local diagnostics off by default; release reports must contain no trees or content. Cancel export, export to a chosen folder, cancel deletion, then delete a disposable history. Deletion must remove all rows/checkpoints and pause counting. Uninstall also removes local app data; exports stay where saved.
8. **Performance:** collect `tools/profile-device.sh` reports before/after normal use. Use a debug APK with Android Studio profiling for CPU detail. Check scan durations/volume in diagnostics and compare normal use with diagnostics off. Do not infer battery efficiency from APK size.

Use repeated runs on both app versions and more than one device before certifying >=95% precision/recall. The provided source tests cover the domain/SQL/privacy rules; they do not replace Android callback, UI or power measurements.

## Optional live badge

Turn on Settings → Appearance → Live badge, then enter Reels/Shorts. Check today’s saved platform count plus the current item, including slow/quick changes. Leave and reenter several times: the count must continue. Switch platforms and verify their independent daily totals. Same-video updates should not increment the number. Swipe and tap through the badge area; it must not consume touches. Open comments/share, leave the player, switch apps, lock the phone or pause counting: the badge should hide after detection. Turn the setting off: it should disappear immediately. Confirm no extra diagnostic transitions or reset sessions are caused merely by showing/updating/hiding the badge. Test font scaling, portrait/landscape and screen cutouts. Verify local midnight resets the displayed daily total, including while a feed stays open. A video started before midnight remains attributed to the previous day until the next item starts.

## Reported reset regression

Accumulate a nonzero daily badge total. Press volume several times without swiping: the restored badge must show the same total, with no additional confirmed item. Switch to the normal feed and return: the saved total must continue (returning to an item can count as another exposure under the existing counting rule). Minimize/return and switch between Instagram/YouTube; neither daily total should fall to zero. Reopen the app/service and check retained totals. Verify explicit Delete history still clears counts and local midnight starts the new day. Send the installed app version and diagnostics if any drop persists.

## 1.0.6 acceptance checks

- Fresh install: first open shows setup. After accepting the disclosure and enabling Accessibility, counting is on without another toggle. Declining setup must not observe content. An update must preserve an intentional paused preference.
- While counting is on, delete history: it clears and the switch stays on. Return to Reels/Shorts; new confirmed items count from zero. Repeat while paused: it stays paused. Repeat deletion twice and check no previous delayed badge results reappear.
- Today total and each app total match the daily badge; watched (2s+) and quick skips remain separately explained. Check large text, small screen, landscape and dark theme for wrapping and scrolling.
- Repeat volume overlay, normal feed return and minimize/return. Confirm the lighter badge stays legible over bright videos and passes all taps through.

Automated regression tests pass; physical UI and OEM lifecycle verification are still required.

## 1.1.0 Friends

Follow the two-phone checklist in backend/README.md. Test add/accept expiry and duplicates, peer-to-peer initial sync, live comparison, stale last-update states, removal, sharing pause, profile deletion (in-app and /delete page), no-server operation, app pause/resume and rotation. Verify local counting, volume/feed/minimize continuity, delete-history preference and local-day reset remain correct. Test large text and both themes, including long/emoji names. Actual Android WebView ICE compatibility is not certified by the protocol tests.
