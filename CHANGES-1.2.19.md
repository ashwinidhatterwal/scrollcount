# ScrollCount 1.2.19 (37)

Includes the 1.2.18 Facebook Reels compatibility candidate.

- Local diagnostics default to enabled when no preference has been saved. An explicitly saved off setting remains respected; Settings can toggle diagnostics at any time.
- Diagnostics offers Save diagnostic file through Android's document picker, plus Share diagnostic file as an actual UTF-8 `.txt` attachment. File writes run off the main thread. Sharing uses a private, read-only provider with temporary access to generated diagnostic files only. No storage permission is required. Recent cache attachments are retained for receivers; attachments older than seven days are removed on a later share.
- Removed the “Reels & Shorts” subtitle beneath the home screen's main counter.
- Friend cards use a compact avatar/name/update-time/Scrolls layout. Scrolls combines the existing non-YouTube and YouTube totals, covering all supported platforms. Update time comes from the sender's captured timestamp and follows the phone's time format; older updates include their date. Larger system text uses a stacked layout.
- Friend details use a bottom popup with exact update time, prominent side-by-side daily Scrolls totals, animated comparison bars, Sync now, and a quieter Remove friend action. Old-day data is explicitly labelled and is never compared against today's totals. Long names wrap, the popup scrolls, and it is capped at 85% of screen height.

Validation: full Android source compiles against official API 36 using Java 17; pure-Java regression suite including diagnostic-file path isolation passes; all ten Python regression scripts and backend Node checks pass; git diff whitespace check passes. UI/device checks, Android document-provider interaction, share-target interaction and Gradle lint/packaging were not run. This archive contains full source, not an APK. Changes have not been pushed to GitHub.
