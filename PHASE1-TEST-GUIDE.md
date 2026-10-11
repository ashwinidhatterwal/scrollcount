# Phase 1 phone test

1. Update APK, open ScrollCount and complete the updated disclosure/Accessibility setup. Confirm the home screen says **Counting is on**.
2. Open Instagram Reels. Stay 5 seconds on the first item and view 5 more items, 3 seconds each. Return home. Expect about 6 seen exposures if every item was confirmed; Diagnostics counts 5 transitions. Feed timing is estimated and excludes detected overlays.
3. Close/reopen ScrollCount. Today/lifetime totals should remain. If Android kills the service abruptly, the current unfinished item may be absent; previously finalized items must remain.
4. Do a similar YouTube run; verify both platform counts on the same home screen. There is no manual platform switch.
5. During a watch, open comments or another app for 10 seconds, then return. Those 10 seconds should not be added as confirmed feed time. Check comments/profile return does not create duplicate exposures unexpectedly.
6. Tap Pause, use a feed for a few seconds and reopen. Saved counts should stay unchanged. Resume and confirm new items count.
7. Lock the phone during a watch, unlock and return. Locked time should not be counted.
8. Use Settings → Delete all history only when ready to discard the test data. Confirm zero totals and paused status, then resume.
9. Check readable text and buttons at your normal font scale and at a larger accessibility font size. Report any clipping.

Send discrepancies with actual item/swipe counts and a Settings → Diagnostics report. Main “seen” excludes quick skips and includes the initial item; the transition report deliberately uses the older probe definition.

0.1.2: test ten quick skips on each platform. Check the separate Quick skips total; Seen requires two seconds. Duplicate events, opening comments/share sheets, normal Home scrolling and likes should not add video changes. Extremely fast or inaccessible YouTube items may still lack a distinct content fingerprint.
