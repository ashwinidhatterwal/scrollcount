# Phase 0 revision 0.0.3 — YouTube diagnostic capture

The YouTube 21.39.522 report shows 391 events, 48 scans, no truncation and zero counts. Its latest root is a small 334×594 window containing `reel_recycler`, `reel_player_page_container` and `reel_time_bar`. The reel page exposes zero children in that final snapshot. The observed Shorts-recycler scroll has indices -1:-1. These do not establish a usable per-video transition signal. We cannot conclude that earlier full-screen snapshots were equally sparse because the old report retained only the latest tree.

The old feed marker searched for `shorts` IDs and missed the observed `reel_*` container names. Revision 0.0.3 recognizes the recycler + page pair as a provisional Shorts container. This is screen detection, not a video counter fix.

New diagnostics:
- Preserve the richest recognized short-feed tree and richest supported-app tree through the test. Prefer larger root area, then node count, so a small return/mini-player window does not replace a full-screen tree. Each retained snapshot records bounds, time, truncation and detection signals.
- Use Android's `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS` for an armed YouTube test only, to check whether additional hierarchy is available. Instagram retains its previous hierarchy profile. This flag cannot recover labels that the app does not expose.
- YouTube traversal cap: 360 nodes / depth 32. Instagram retains 180 / depth 24. Caps and scan coalescing still apply; broader diagnostics are not proposed as a production battery strategy.
- Sample YouTube content-event source IDs/classes at most once per second; retain a separate 100-entry diagnostic log. No event text is exported. No new permissions or automation.
- Keep missing position indices as unavailable; do not count every content-change event or playback progress update.

Retest procedure (diagnostic, not an accuracy benchmark):
1. Install 0.0.3 over the prior probe. Disable and re-enable ScrollCount in Accessibility settings once if Android retains an old service connection.
2. Start a fresh YouTube test. Open Shorts full-screen, outside picture-in-picture, floating-window or split-screen mode.
3. Wait 5 seconds on the first Short. Make 5 deliberate forward swipes, waiting 3 seconds after each.
4. Return to ScrollCount and share the full sanitized report, including both retained-tree sections. It is now safe to return: those sections preserve earlier evidence. Do not reset before exporting.
5. Zero may remain the correct diagnostic outcome if YouTube still hides the necessary labels. The full report will distinguish a missed mapping from missing accessible data.

Instagram evidence to date: one captured pager sequence 17→36 produced exactly 19 accepted transitions; a separate navigation/no-transition run produced zero. This is positive device-specific evidence, not a completed multi-device accuracy benchmark. User physical swipe totals and complete overlay coverage remain unverified.

Source for diagnostic hierarchy flag: https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
