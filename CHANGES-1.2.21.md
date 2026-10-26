# ScrollCount 1.2.21 (39) — Snapchat 14.29 native layout

Includes all previous Facebook compatibility, diagnostics export and Friends UI work.

## What the new report established

The supplied 1.2.20 report on vivo I2302 / Android 16 with Snapchat 14.29.0.50 contains a full-size native ViewGroup player at bounds 0,110–1080,2179. It has a video backdrop, lower-left author/music/caption rows, and six vertically stacked right-side action groups. Every node is non-scrollable and the navigation nodes are not marked selected. Consequently the 1.2.20 pager/Spotlight-label/Favourite-label fallback could not confirm it. The old `textPresent=false` flags for footer rows meant those labels were not sampled during the structural pass; they did not establish that footer text was unavailable.

## Changes

- Recognise this native Snapchat structure without requiring a scrollable pager, a Spotlight label, or named resource IDs. Confirmation requires a full-size ViewGroup containing a full-size backdrop, at least two lower-left footer TextViews (including a compact clickable author), and a tall right-hand rail containing at least four separated clickable action groups with image descendants. These features must belong to the same subtree.
- Scope temporary content fingerprints to the confirmed native player subtree. Footer bounds now include the observed wide caption at 22,2112–926,2168. TextView descriptions can supply identity if text is empty. Numeric/action labels remain excluded; raw captions/authors are neither stored nor exported.
- Keep the previous explicit-labelled Spotlight path, duplicate suppression, movement freshness/window checks, editable-input/secondary-list-panel exclusions and bounded scan budgets.
- Missing-ID Snapchat scroll events now report metadata-only handling rather than claiming the whole event was rejected.
- Diagnostics adds geometry/rail/author/footer proof counters and a `labelsSampled` flag so unsampled text is distinguishable from absent labels.

## Verification

All pure-Java regressions, all ten Python regression scripts, backend Node tests and git diff whitespace checks pass. The service suite compiles all Android source against official Android API 36 and passes 24 orchestration cases. A fixture extracted from the uploaded content-free layout metadata now confirms the actual non-scrollable tree while correctly refusing to invent identity from geometry alone. With synthetic footer text added to that real geometry, baseline and stable swipe changes count exactly once. Removing all but two action rows or adding an editable overlay prevents confirmation; unconfirmed body text remains unread.

The fixture has no real captions or accessibility text. It validates structure, not whether the phone exposes enough footer text to identify each video. A phone test is still required. Open Snapchat Spotlight, pause briefly between several swipes, then check that Stories/chat do not count. If counting still waits, export diagnostics immediately; `snapchatGeometry` and `contentFields` distinguish structural failure from missing identity. Identical exposed author/music/caption combinations and very fast swipes can remain uncounted. Other Snapchat versions/layouts have not been verified.

No device test, Gradle packaging/lint run or APK creation was performed. Full source only. Nothing was pushed to GitHub.
