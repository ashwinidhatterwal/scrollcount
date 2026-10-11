# Validation — ScrollCount 1.0.5-rc6, code 14

Completed locally:

- Android API35 resource/manifest compilation/linking, Java compilation, DEX generation at min API28, ZIP alignment and signature verification. Candidate is non-debuggable and uses the same application ID and certificate as delivered v0.2.0.
- Nine Java regression programs: transition stability/baseline/duplicates, Instagram/YouTube signals, dwell thresholds/pause/resume/platform exits, both rapid-transition paths, partial layout/geometry leases, checkpoint/session metadata, and opt-in reminder thresholds/duplicate suppression.
- Exact SQLite tests: v1→v2 history preservation, daily/weekly/platform/session aggregates, replaceable active row, recovery to saved duration once, repeated reopen, atomic finished-item removal, rollback, CSV query and deletion.
- Static privacy checks: only notification runtime permission, backup disabled, two visible package queries, observation-only service, unsupported package early return, typed/password exclusion before text access, opt-in debug-only tree serialization and content-free history code.
- UI design/source review: consistent native components, accessible headings/chart alternative, large-font stacking, scalable hero count, tablet width cap, touch targets and both themes. Palette checks: muted-on-paper >=4.5:1, light primary-button contrast 6.43:1, dark muted-on-surface 7.73:1 and hero secondary text 8.40:1.
- Performance implementation review: bounded/coalesced full scans and YouTube footer checks, no indexed-Instagram content traversal, no default tree construction/log queues, ordered background writes, revision/date-cached reads, foreground-only UI refresh and event-driven checkpoint/reminder logic.

Unverified here:

- Physical Android UI/font-scale/TalkBack rendering, OEM accessibility and SQLiteOpenHelper lifecycle, actual reminder permission/channel/delivery, phone reboot/process kill, CSV provider behavior, multi-device >=95% transition precision/recall, ads/live/buffering/account changes and battery/CPU usage.
- Gradle/lint/Actions/AAB run. Dependency access is unavailable to the JVM here; SDK APK compilation succeeds. Workflow/source configurations are included but not reported as executed.
- Play approval or public-release signing. The candidate is signed with the retained tester identity; a publisher-owned private upload key is required for the supplied public release workflow.

Earlier phone reports informed the regression cases: Instagram 17→36 yielded 19 transitions; an outside-navigation test yielded zero; the user confirmed one seven-transition YouTube run. v0.1.2 Instagram 19→47 exposed 28 changes but accepted 27. A partial-layout cause is plausible rather than proven; rejection diagnostics and a synthetic 28-transition regression were added. No untested build is represented as a verified fix on the physical phone.

See TEST-GUIDE.md and RELEASE-READINESS.md for the remaining measurable checks. All phases have engineering implementations; device/policy/performance validation is not silently marked complete.

## YouTube recovery update (1.0.5-rc6)

All 11 Java regression programs pass, including two new tests for description-only content, canonical labels, bounded traversal and the combined movement/exposure pipeline. A synthetic pair of 20-item sessions produces 40 finalized exposures (20 Seen, 20 Quick skips) and 38 transitions because the first item of each session establishes a baseline. This is a software regression result, not a replay of the exact device trees: release diagnostics intentionally do not retain those trees. Existing Instagram partial-layout, quick-skip, overlay/exit, lifecycle, reminder, SQL and privacy tests pass. SDK resource compilation, Java/DEX compilation and APK signing pass.

The observed device report recorded 31 transitions for the user's 20 slow and 20 quick Shorts. The report lacks per-item identities, so it cannot prove the cause of every miss. This patch corrects identifiable weaknesses: description-only labels were excluded, action-label recognition gated content extraction, and late labels could outlive the retry window. Physical accuracy must be retested on YouTube 21.40.161.

## Live badge update (1.0.5-rc6)

12 Java regression programs pass. LiveItemsTest verifies initial/quick/slow inclusion, duplicate suppression, pause/resume, platform/session reset and unchanged exposure persistence. Existing Instagram and YouTube recovery tests, privacy boundary checks and SQLite migration/recovery checks pass. Full SDK APK compile/sign passes. No additional permissions or database schema changes. Overlay appearance, gesture pass-through and OEM accessibility-window event filtering are not physically verified.

## Daily badge (1.0.5-rc6)

The previous session-only badge counter has been removed. DailyBadgeTest replaces LiveItemsTest and verifies history plus current item, platform separation, repeated refresh, session exit/reentry, restored history, deletion, local midnight and a 23-hour DST day. The exact SQLite aggregate is exercised against both slow and quick exposures and a new-day boundary. The serialized read captures the current item before enqueueing; stale callbacks are rejected after transitions/session changes/date changes to avoid counting the current item twice.

All 12 Java test programs, SQL/history and privacy tests pass; full SDK compile/sign passes. The midnight broadcast and actual phone UI still need device verification.

## Interruption-resistant daily totals (1.0.5-rc6)

The badge no longer reconstructs its total from finalized durations and a volatile current-item increment. The counting engine emits one confirmed-item record on begin, independent of pause/finish/close. Schema 3 seeds legacy exposures and active checkpoints once with a session/start primary key; explicit history deletion clears these records. DailyBadgeTest covers volume pause/resume, feed exit/return, minimize/return, new process/session, zero-duration interruptions, duplicates, midnight/DST and platform separation. SQL tests cover v1 and v2 upgrades, active-checkpoint migration without double counting, and item durability without a finalized timing row. All existing counting, privacy, recovery and reminder checks pass.

Own badge events with missing window IDs use active-window package/class metadata to distinguish the actual app opening from its non-interactive badge. Third-party temporary window events over an active player pause/revalidate rather than discard identity. No unrelated UI text/tree traversal is added. SDK APK compile/sign passes. Exact OEM window-event behavior remains subject to phone testing.

## 1.0.6-rc7
Existing detection, timing, recovery, daily totals and privacy regression suite retained. Daily dashboard totals query the same persisted detected-item aggregate as the badge. SDK resource/Java compilation and APK signing verification run. No emulator or physical phone was available: visual layouts and first-run/delete interactions require the above device acceptance checks.

## 1.1.0-rc8 / code 16

Friends UI, explicit optional sharing setup, private invitation pairing, server-side friendships with revocation, HTTPS signaling and WebRTC data channels are implemented separately from detection/timing. Server SQL/routes exercised against local SQLite D1 adapter; bundled protocol exercised with controlled RTC callbacks. Original detector/history/midnight regressions retained. Actual Playwright browser test could not run: browser download returned unusable content. No physical Android or deployed Cloudflare account was available. Deployment and two-phone verification remain required.
