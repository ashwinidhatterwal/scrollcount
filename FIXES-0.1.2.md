# 0.1.2: quick skips on both platforms

Includes 0.1.1's exact-position Instagram fix and the readable Phase 1 dashboard.

YouTube now checks the visible player footer after exact `reel_player_underlay` or `reel_recycler` scroll events. A changed title/channel fingerprint must agree across two reads at least 60 ms apart. Up to twelve scoped checks run about 70 ms apart per movement; checks stop without a recent confirmed feed, on overlays/window changes, pause, screen lock or unsupported apps. Full scans and fast checks choose the same largest visible footer. Generic results lists, action counters, audio and subscribe state do not identify a new video.

Content is hashed in memory with the existing session key, never stored in history. History schema and the two-second seen threshold are unchanged. Quick skips appear separately from Seen; Diagnostics counts transitions, excluding the initial item.

Regression tests use synthetic content identities at the supplied rapid event times. They verify retained rapid exposures, duplicate suppression, missing content, overlay and expired movement guards. These sanitized reports cannot replay real fingerprints. Actual phone behavior still needs testing. Shorts that expose no distinct content label, identical labels, or changes shorter than the accessible confirmation interval may remain uncountable; scroll events alone are never treated as proof of a new video.

Install over the existing app to preserve history. Test five slow skips, ten quick skips, back-and-forth skips and comments/share sheets. Compare Quick skips as well as Seen, then send Diagnostics if totals differ.
