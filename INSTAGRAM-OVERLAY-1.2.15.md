# ScrollCount 1.2.15 (33): correct false Instagram overlay rejection

Base: GitHub main 79e4df5999bea377d3c4e3a27e8f1d080a2065c0, version 1.2.14.

## Evidence from the phone

Android 16 / vivo I2302, Instagram 450.0.0.50.77. Accessibility is enabled and the service is connected. The report records exact clips_viewer_view_pager scroll sources, advancing positions 27 through 42, no missing or expired source reads, 31 scans, no truncation and maximum completed scan time 870 ms. Repeated full-player scans with a feed marker and Like/Share controls are rejected with overlay=true. No accepted transitions are recorded.

This establishes the rejection stage. It does not name the offending node: the old report records only a combined overlay flag. The final small root bounds are from a later window and must not replace the repeated full-player evidence. The previously suspected slow-read timeout does not explain the supplied trace.

## Defect and correction

The old walker blocks on every visible editable field and every ID containing bottom_sheet/share_sheet. This treats an unfocused inline comment field, a button opening a share panel, or a wrapper hosting the player as an already-open blocking panel.

The Instagram walker excludes protected text and descendants without reading them. An unfocused ordinary input no longer makes the entire player an overlay; focused input and visible password fields still pause counting. Panel-opening buttons/icons are distinguished from actual panels. A generic bottom sheet that contains its own verified full-player subtree is treated as a viewer wrapper; one without a player remains blocking. Explicit comments/share/profile/story panels remain blocking. Other platforms retain their previous overlay rules.

Diagnostics now record sanitized overlay reason categories in both scan decisions and signals. They include ScrollCount's installed version in the package-version list rather than displaying the stale hardcoded 1.2.12 report title. No new permissions or database changes.

## Verification

New tests drive the actual event callback, source reader, scheduled scans, tree traversal and counting engine. The old implementation records zero of 20 Reels in the first inline-input trace. The corrected implementation records all 60 exposures across inline inputs, sheet-opening controls and player-hosting wrappers. Actual overlays remain excluded; protected input text and its descendants are never read.

All 260 simulated exposures in 19 service cases pass, along with existing Java regressions, read fences, service health, privacy/history checks, actual automatic sharing/hourly jobs/defaults, metadata/deployment checks and backend tests. These are deterministic Android boundary doubles, not the physical phone. The exact offending node on that phone remains unrecorded in the supplied report.

Final-source Gradle assembleDebug, lintDebug, assembleRelease, bundleRelease and lintRelease all passed; both lint reports contain zero errors. Local release artifacts are unsigned and must be built with the existing upload key for Play. Update through the same Play signing channel to preserve the installed app's data.
