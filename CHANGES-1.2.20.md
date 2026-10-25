# ScrollCount 1.2.20 (38) — Snapchat Spotlight compatibility candidate

Includes all 1.2.18 Facebook and 1.2.19 diagnostics/friends changes.

## Supplied device evidence

The 1.2.19 (37) report from Android 16 / vivo I2302 has 39 complete scans, zero accepted transitions, full-screen bounds, obfuscated RecyclerView IDs, and later FrameLayout events with missing IDs and indices. Most player frames have like=true, share=false, feedMarker=false and no content identity. Accessibility is connected. This identifies resource-ID/identity requirements as blockers; it does not reveal the actual Spotlight widget geometry or labels because detailed tree dumps are disabled in that release.

## Changes

- Snapchat no longer requires a named Spotlight resource ID when explicit fixed Spotlight UI vocabulary confirms the selected navigation item or heading. A full-screen scrolling player with its own right-side Favourite/Like control is still required. Named Spotlight player IDs also remain usable.
- A Share label is no longer required for Snapchat confirmation. Other platform rules are unchanged.
- Unindexed FrameLayout/RecyclerView/ViewPager scroll metadata can authorize confirmation of a stable content change in the same window. No reel numbers are guessed from missing/wide indices or scroll distance.
- After Spotlight structure is confirmed, scoped lower-left leaf text is hashed as a transient identity. Numeric counters, timers and common action labels are excluded. Raw labels are not exported or stored in history. Unconfirmed body text is not collected.
- A stable identity must follow fresh movement. Baseline/accepted transitions consume the movement; duplicate frames and subsequent label edits cannot reuse it. Overlay inputs, substantial secondary list panels, wrong windows and expired movement cannot authorize counts.
- Release reports now retain bounded Snapchat layout metadata: classes, geometry, hierarchy depths, selection, scrollability and fixed-control flags. Raw text, captions and descriptions are omitted. Disabling diagnostics clears this metadata.
- App package versions refresh when diagnostics opens; failed lookups say unavailable/not visible rather than asserting the package is absent.

## Validation and limits

Pure-Java regressions pass, including Snapchat vocabulary/footer filters. All ten Python regression scripts pass after updating the existing version-inventory check for its new shared location. The production service suite compiles all app source against official Android API 36 and passes 23 orchestration cases, including an obfuscated Spotlight tree with missing source IDs/indices, no Share label, stable content changes, Stories, unconfirmed body-read exclusion, editable overlays, consumed/expired movement and metadata redaction. Backend Node tests and git diff whitespace checks pass.

These are synthetic layouts, not a replay of the real Snapchat tree. The supplied file is insufficient to prove that the new structural gates match the phone. Test the candidate in Spotlight, and also verify that Stories and chat do not count. If still unconfirmed, export the new diagnostic file immediately after several Spotlight swipes. The new Snapchat signals/layout section is the needed next evidence. Repeated videos with identical exposed author/caption text, inaccessible footers, other languages and layouts without explicit Spotlight evidence can remain uncounted. The existing 650 ms stabilization can miss rapid swipes.

No device test, Gradle packaging or lint run was performed. This archive contains full source, not an APK. Changes have not been pushed to GitHub.
