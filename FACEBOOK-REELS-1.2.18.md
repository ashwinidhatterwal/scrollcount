# Facebook Reels compatibility candidate — 1.2.18 (36)

Base: GitHub main cc6eb697ffa0fdf05a01c974715af02537aa3c7a, version 1.2.17 (35).

The supplied Facebook 584.0.0.51.56 trace reports full-screen frames with Like/Share controls, but no feed marker or content fields. Scroll events identify an AndroidX RecyclerView whose ID is `(name removed)` and whose ranges advance 3:4 through 8:9. The previous detector requires descriptive reel/clip resource IDs and caption/author IDs, so it rejects these frames and movements.

The new Facebook path uses source positions only after a fresh complete same-window snapshot verifies the matching full-screen RecyclerView and a vertical right-side Like/Share action rail inside its subtree. The event source itself must also have full-player bounds. A compact one/two-item visible range supplies the first index; wide, missing or inverted ranges are rejected. Positions stabilize through the existing counter, preserving its initial baseline and duplicate guards. Caption reads are skipped for Facebook. Other platform counting paths retain their detector rules.

Expired new positions cannot transition; an already committed position remains eligible while the player remains confirmed, to preserve time tracking during a long watch. Window changes, target changes, screen-off and invalidated reads clear pending Facebook metadata. Partial sources, overlays, incomplete trees and offscreen controls cannot authorize transitions.

## Device validation required

The supplied sanitized trace has no control bounds or full tree, so it cannot establish that the observed Like/Share controls satisfy the action-rail gate. This is a compatibility candidate, not a device-verified fix. It deliberately fails closed if the layout does not match.

Install the 1.2.18 build. Open Facebook Reels, settle on one reel, then swipe through ten reels with a short pause between swipes. Confirm counting, then scroll the ordinary feed and open/scroll comments and sharing panels; those actions must not count. If it still waits, export fresh diagnostics; `facebookPlayer` and `facebookIndex` now expose which gate failed. A debug verbose sanitized tree can supply missing geometry without revealing captions.

Recycled list indices with no observable position change remain unsupported when captions are unavailable. Rapid swipes shorter than the existing 650 ms stabilization window may be missed.

## Verification completed

- All pure-Java regression tests pass, including Facebook range/geometry/deduplication tests.
- All ten Python regression scripts pass, including 22 production-service orchestration cases with synthetic Facebook subtree geometry and position events.
- Backend Node regression suite passes.
- All Android Java source compiles against the official Android API 36 android.jar with Java 17.
- `git diff --check` passes. Gradle packaging/lint and physical Facebook tests were not run; no APK is included.
- Changes have not been pushed to GitHub.
