# ScrollCount 1.2.14 (32): repair the Instagram structural regression

Based on 1.2.12. This is a targeted correction of the newer implementation, not the 1.2.5 rollback.

## Reproduced defect

1.2.5 accepts the observed clips_video_container marker plus Like/Share controls as player evidence. The newer structural rule unconditionally requires an accessible pager. It also discards pager markers whose accessibility bounds do not cover 70% of the screen. Consequently a recognized full-screen video and its controls can be present but the player is never confirmed, captions are never collected, the badge is hidden and all counts remain zero.

The new fixture drives the production event callback, source reader, scheduled scan, snapshot traversal and counting engine. Its first video-only 20-item trace recorded zero exposures before the correction. The amended pipeline records all 20 in that trace and all 60 across three layouts: no accessible pager, a pager wrapper whose reported bounds are smaller than the video, and unavailable pager source handles after the first item. This establishes a code defect; it does not establish the exact layout of the user's phone.

## Correction

Keep the existing exact-pager path. Add a compatibility path requiring the known video-container ID, visible bounds covering the existing full-player geometry threshold, and Like/Share evidence. Root geometry, privacy exclusions, overlays, complete-read requirements and stable identity/movement validation still apply. A generic video or Reels tab label cannot establish this path.

Use that same structural rule both when deciding to collect content and when admitting the finished scan. Record the confirmed window for the compatible layout, and let missing-source reconfirmation validate that layout without reintroducing the rejected pager requirement.

The existing modified-app/Lite/Facebook/Snapchat profiles, background read isolation, service diagnostics, history, sharing and default live badge are retained. No permissions, database schema or backend behavior changes.

## Verification

All Java regression checks, production service-pipeline cases (200 simulated exposures), read-fence/service-health/privacy/history checks, actual sharing/job/defaults tests and backend checks pass. The negative fixtures reject inline video previews and comments overlays. Physical device and Instagram Binder behavior are not simulated by these tests.

Final-source Gradle assembleDebug, lintDebug, assembleRelease, bundleRelease and lintRelease all succeeded. Both lint XML reports contain zero errors. Local release artifacts are unsigned; publication still requires the existing upload key in GitHub/Play. Use the same Play signing/testing channel to update the installed app with code 32. The source contains no keystore, generated APK or local SDK settings.
