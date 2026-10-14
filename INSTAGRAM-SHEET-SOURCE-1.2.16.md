# ScrollCount 1.2.16 (34): sibling-sheet rejection and source-reader starvation

Base: GitHub main 0df475e, version 1.2.15.

## Evidence

The phone report confirms 1.2.15 (33), Android 16/vivo I2302 and Instagram 450.0.0.50.77. Exact clips_viewer_view_pager events advance through positions 5–12. Complete, full-player frames with pager/video and Like/Share evidence are rejected by sheet_without_player. Earlier reads are truncated or discarded while source callbacks arrive 624–2045 ms after their events; one expires. The service stays connected.

The previous fix incorrectly classified every generic bottom-sheet subtree without a player as blocking. Small sibling controls and empty sheet hosts therefore still reject the whole player. It also left source metadata queued behind structural reads on a single reader. These are implementation defects; the report does not provide the offending sheet node's bounds, so its exact layout cannot be reconstructed.

## Changes

A generic sheet blocks only when it has visible descendants, no verified player subtree, and an on-screen panel footprint (at least 85% screen width and 30% screen height). This excludes small sibling controls and empty placeholder hosts. Explicit comments/share/profile/story panels, focused input and visible password fields retain their guards. Opt-in diagnostic decisions/signals now include each generic sheet's resource ID, bounds, descendant count and panel classification, without reading or storing content text.

Event-source reads use a separate bounded worker from structural/footer/window reads. Slow trees no longer delay exact pager event metadata. Results still enter the counting engine on the main thread and must pass the same epoch, package, age, reset/lock/exit and destruction checks. Both queues are cancelled and closed with the service. No guessed indices, UI-thread tree reads, polling timer, permissions, backend or database change is introduced.

## Verification

The new production-service cases fail on the base version: the first small-sibling trace records 0/20, and pager admission behind a held reader records zero items. The amended service records 40 exposures across small sibling/empty-sheet traces and all 40 rapid verified pager events while its structural reader remains blocked. The generic-modal negative fixture now includes actual visible panel contents and on-screen bounds rather than a childless placeholder.

All 340 synthetic exposures across 21 service cases pass. Existing Java regressions, actual read fences, service health, privacy/history, sync/hourly/defaults, metadata/deployment and backend checks pass. Fixtures exercise production orchestration with Android boundary doubles; they do not run Instagram or this phone.

Final-source Gradle assembleDebug, lintDebug, assembleRelease, bundleRelease and lintRelease all passed. Both lint reports contain zero errors. Local release artifacts are unsigned; use the existing upload key for Play. Publish the signed AAB through the existing Play channel. The source excludes local SDK settings and generated builds.
