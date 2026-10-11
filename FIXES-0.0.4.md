# Phase 0 revision 0.0.4 — scoped YouTube footer fingerprint

The 0.0.3 report changes the diagnosis: a full-screen Shorts tree was accessible, with like/share controls and deep footer labels. Nine of ten scans were truncated despite only about 122 nodes: the depth-32 cap, rather than the node cap, stopped traversal. Any truncation disabled content collection, and the old collector also depended on named content resource IDs while this UI's footer labels are anonymous.

Corrections:
- Increase YouTube's bounded depth limit to 52, retaining the 360-node limit. Instagram limits and rules stay unchanged.
- Match the supplied hierarchy: `reel_player_footer_container` → `player_overlay` → content group. Hash accessible text in the first left row and the content branch of the second left row.
- Validate that the selected rows lie in the left part of the content group. Exclude the right action rail, the adjacent second-row action, the third/audio row, and playback-progress descriptions.
- Use text values rather than geometry or arbitrary control descriptions as the temporary HMAC input. Hash a parent's text once when it summarizes descendants.
- Require a recent scroll signal from the exact `reel_player_underlay` or `reel_recycler`, plus a changed stable fingerprint, before YouTube counts. The reported -1 indices remain unknown; no fake position counter is introduced.
- Export repeated retained trees only once when the two retained snapshots are identical.
- Bump version to 0.0.4 / code 4; keep the prior debug signer so installation can update in place.

The supplied tree only confirms field presence, not the semantic content of the redacted labels. Their identity stability and per-video uniqueness remain hypotheses. Repeated titles/channel labels, missing scroll events, buffering, overlay mutations and future layouts can still cause misses or false counts. A recent-scroll requirement can miss videos when YouTube emits no usable player scroll signal. This is an experimental counting rule, not a production accuracy claim.

Retest:
1. Update the APK. Start a fresh YouTube test, open Shorts full-screen and wait 5 seconds on the first Short.
2. Make exactly 5 swipes with 3-second pauses. Export before reset. Send the summary, signals and accepted-transition timestamps; if zero, also include the retained feed tree.
3. In a separate run without video swipes, test like/unlike, pause/resume, comments open/close, title expansion and subscribe-state changes. Expected transitions: zero. Capture immediately after the test.
4. If the five-swipe count matches, use the existing 20-swipe benchmark before proceeding to Phase 1.
