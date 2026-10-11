# Phase 0 revision 0.0.2

The first report showed a working accessibility connection, 1,430 supported events, 435 scans, no truncation and zero accepted transitions. The latest tree was the Reels player, but the detector looked for `reel` strings while the observed resource IDs use `clips`. That made `feedMarker=false` and blocked fingerprint collection. This was a detector mismatch in the probe.

Changes:
- Recognize the observed `clips_viewer_view_pager` and `clips_video_container`, not the generic clips navigation tab.
- Collect author text from `clips_author_username` and caption labels from descendants of `clips_caption_component`; the caption parent itself had no text or description.
- Exclude like counts, comments counts, playback scrubber, controls and geometry from content identity.
- Require settled scroll evidence from the exact clips player pager before Instagram can count a new position. Caption scrolls, other pager navigation and ordinary content updates cannot authorize a transition.
- Use the verified pager position as provisional counting identity when available; the session-salted content hash remains a diagnostic. This avoids duplicate counts on content mutations at the same known position and distinguishes repeated captions.
- Export scroll source resource ID, class and index in a separate 200-entry buffer, so a flood of playback updates cannot erase it from the general recent-event buffer.
- Coalesce content-change storms more slowly, prioritize scroll events and enforce a minimum 400-ms spacing between tree reads. No measured battery improvement is claimed yet.
- Bump version to 0.0.2 / code 2 and retain the same debug signer for updating the APK.

The supplied report did not contain the source ID of its 28:28 scroll event. A different source or unavailable index will deliberately leave counting unavailable rather than fabricate transitions. If counts remain zero, send the updated report: it now distinguishes missing player detection, content fields and scroll-source evidence.

Retest: update APK, start a fresh Instagram test, wait 3 seconds on the first Reel, make exactly 20 settled swipes with 3-second pauses, and export before resetting. Then run a separate no-swipe test for likes, pause and comments. Send both reports and actual swipe counts. YouTube rules are unchanged and still need their first trace.
