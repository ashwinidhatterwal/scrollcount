# ScrollCount architecture and reliability audit

Reviewed 14 October 2026. GitHub main at `4676bf70e35c15815eaeeb42376cdbd6f3a52598`, version 1.2.10. Its rerun completed successfully. This source contains version 1.2.11, build 29.

## Assessment

Event-driven Accessibility observation is a reasonable foundation for a passive, offline counter with modest battery use. It observes UI changes rather than a social app's actual playback history. The current implementation is not a universal or fully validated counter: its adapters depend on third-party layout IDs, labels and event behavior. A successful APK build cannot establish detection correctness on a phone.

The useful architectural choices should remain: a bounded background reader, main-thread ownership of counting state, generation fences against late results, asynchronous SQLite writes, separation of optional Friends networking, temporary content identities, and no automated swiping or screen recording. Replacing these with unrestricted polling or a foreground keep-alive would not solve incorrect identity detection or a disabled Accessibility permission.

The weaknesses lie in evidence handling, adapter coverage and verification. Observation must distinguish a confirmed player, a confirmed exit, an overlay, and an incomplete/unavailable read. Optional labels must not invalidate a complete positional observation. The same exposure must keep its identity when a signal becomes unavailable or appears late.

Alternatives do not remove the core difficulty. Android UsageStats provides application usage statistics and events rather than an individual Reel identity. Screen capture plus visual recognition could supply another signal, but MediaProjection requires user consent for each capture session and a foreground service for current target versions. It would change the quiet automatic workflow and add image-processing work. The recommendation is to retain event-driven observation, improve its evidence states, and validate each adapter against real sanitized traces before expanding support.

## Pipeline reviewed

| Stage | Implementation | Assessment |
|---|---|---|
| Consent and start | `ProbeActivity`, `OnboardingActivity`, service preferences | Counting requires disclosure version 3 and enabled running state. A fresh installation defaults the badge on; an existing choice is preserved. |
| Event admission | `ProbeService.handleEvent`, `Detectors` | Exact package allowlist, including explicitly selected modified apps. Unsupported apps provide foreground metadata only. |
| Source reading | Copied events, `ReadLane`, `applySource` | Background IPC and exact player-source rules avoid generic-feed counting. Missing/late sources can still prevent a transition. |
| Screen evidence | `readPlayerRoot`, `Snapshot` | Active/focused window checks, bounded traversal, player/overlay markers and geometry. Two-pass traversal and wrapper handling were inconsistent. |
| Identity and transition | `InstagramIdentity`, `TransitionProbe`, `FastFingerprintTracker`, `FeedEvidence` | Positions and scoped content are useful signals. Identity switching and incomplete frames had reproducible orchestration bugs. |
| Timing and saved totals | `CountingEngine`, `HistoryStore`, `DailyBadge` | Exposure timing and asynchronous persistence are separate from detection. First confirmed item contributes to Scrolls; transition diagnostics exclude that baseline. |
| Badge | `PlayerIndicator`, `refreshIndicator` | Touch-through Accessibility overlay. Failure is isolated; its disappearance can reflect paused/unconfirmed observation rather than lost history. |
| Optional work | Friends coordinator, hourly job, notifications | Network operations do not supply or authorize counts. Hourly jobs can be delayed by Android. |
| Lifecycle and failures | `CallbackGuard`, read watchdog, `ServiceHealth` | Recoverable callbacks are guarded and lifecycle/exit reasons recorded. A watchdog cannot interrupt an arbitrary Binder call or re-enable Accessibility. |
| Verification | Java/Python regressions, SDK compilation, GitHub lint/build | Existing helper tests mostly supplied already-valid evidence. New tests invoke the actual service paths to cover previously missed interactions. |

## Reproduced defects and changes

### 1. Instagram could become stuck on an old index

The service retained its last `pagerIndex` when a verified Reel-pager scroll supplied no unambiguous index. Once positional identity was selected, later frames continued to use the stale index and ignored changed captions. Every Reel looked like the same item.

The service-level reproduction establishes index 10 and content A, then supplies a verified unindexed swipe and stable content B. Before the change, the saved exposure count stays at one. With the change it reaches two, and a late index for B does not add another exposure.

Invalid/ambiguous index metadata now clears the usable index. Indexed frames keep a scoped content alias for recovery. A stable content change after verified pager movement can switch the session into content mode. Late indices cannot duplicate the recovered item. Label changes without a current verified movement remain rejected.

Recovery still requires distinguishable accessible content. Identical or absent labels cannot identify two different videos by themselves.

### 2. A truncated tree was treated as leaving the player

When a traversal hit its limit before finding a feed marker, `applyScan` entered the non-feed branch, closed the session and forgot its baseline. Returning to the same Reel could record a new baseline as another exposure. During swipes or on slower app responses this could repeatedly reset observation.

The production-path reproduction supplies a valid player, an incomplete markerless frame, and the same valid player again. Before the change, the incomplete frame closes the session. The corrected path pauses timing, retains the baseline and allows bounded recovery. A complete non-player frame still closes it.

Movement authorization is cleared on pauses and overlays while the previous content alias is retained. An old swipe cannot authorize an unrelated later label edit.

### 3. Structural and content traversal disagreed about wrappers

`Snapshot.walk` searched through non-visible structural parents, but `collect` and the footer search immediately returned at such a parent. A valid player could be recognized while visible descendant captions were never read.

The actual collector test creates an invisible wrapper with a visible, scoped caption descendant. It returns zero fields on the old path and one on the corrected path. A separate YouTube fixture confirms scoped title/channel extraction through a hidden root and excludes the adjacent action.

Collectors now traverse wrappers without reading their hidden content, retain bounds/depth limits, and exclude editable/password subtrees. Exhausting the content budget still rejects a content-based observation. If an indexed Instagram frame already completed its structural read, a slow optional caption alias is discarded without revoking the positional evidence.

## Remaining limitations and suspects

These are code-review findings and investigation targets, not confirmed causes on the affected phone:

- **Source availability and age.** The service deliberately rejects a source result older than 1.5 seconds. A busy reader or a missing source can prevent verified movement. Diagnostics now count missing and expired sources and record age before rejecting the result.
- **Shared reader pressure.** Up to 24 pending jobs are retained, with scan/footer coalescing. Many unique scroll jobs can compete with confirmation reads. Synthetic queue tests cover bounded resources, but physical IPC delay distributions have not been measured. Do not increase threads or timeout thresholds blindly.
- **Third-party adapters.** Official Instagram and YouTube depend on observed resource IDs and, for YouTube, footer row structure. App updates, language, experiments and OEM accessibility behavior can change those. Instagram Lite, Facebook/Lite and Snapchat currently use conservative heuristics; they are not physically validated adapters. Explicitly selecting a modified app only assigns a profile; it cannot create missing accessibility information.
- **Ambiguous content.** Reels or Shorts with identical accessible labels can be missed. The service cannot infer a video identity from a generic swipe without evidence that it occurred in the short-video player.
- **Transient layouts.** Complete reads that genuinely contain no player marker still close the session. A loading layout indistinguishable from a non-player layout may require a fixture-backed absence-confirmation policy. No affected-device trace currently establishes that case.
- **Footer confirmation leases.** Fast YouTube reads use recent full-player confirmation. Overlay and window event ordering should be checked against real traces, especially where the player remains accessible behind another panel.
- **Process termination.** Accessibility disabled in Android settings is distinct from a paused detector or a disconnected service. The exact reason requires the enabled-setting state, lifecycle log and Android process-exit evidence. No such current failing-device report was provided for this revision.

## Validation

The new harness compiles production sources against Android 36 and invokes the real `applySource`, `applyScan`, `readSnapshot`, caption and footer methods. Android boundary doubles provide deterministic time, screen geometry and node fixtures. It does not copy the service's counting algorithm. The old release fails the missing-index, truncated-frame and hidden-caption tests; the revised source passes. Additional cases cover later indices, expired sources, repeated recovery, action edits, protected content, YouTube scope and optional alias budgets.

The complete existing Java suite and Python checks for read admission, service health, privacy, SQL history, sync, jobs, defaults, deployment and release metadata pass. Backend tests pass. Final-source resources/Java/DEX compilation and local debug APK signature verification pass for build 29. Test-only Unsafe and existing deprecated-API warnings remain.

The base revision has successful GitHub CI. This new revision has not been pushed or run through GitHub lint. Local full Gradle lint remains blocked by corrupt jars in the local Gradle distribution. No physical phone was attached or tested; these findings must not be described as proof that every malfunctioning phone is fixed.

## A useful affected-device test

Enable Settings → Local diagnostics. Open Instagram and swipe through approximately 10 distinct Reels, including a pause on one Reel. Then open Diagnostics → Share sanitized report and share the text. Do this while the failure is observable. The report contains version, app versions, event/source evidence, rejection reasons and service health; no captions or viewed media. If Accessibility becomes disabled, share the report immediately after opening ScrollCount, before re-enabling it if practical.

The result separates: no events; missing/expired source; absent player markers; incomplete reads; stable/ambiguous identities; and service termination. That is the next evidence needed to name the actual phone-specific cause rather than guess.

For an installed Play Store build, distribute the release AAB through Play internal testing. A local/upload-key APK may conflict with the Play signing certificate; do not uninstall to work around it when preserving history matters.

## Platform references

- [Android AccessibilityService lifecycle and window access](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [AccessibilityRecord source can be null](https://developer.android.com/reference/kotlin/android/view/accessibility/AccessibilityRecord)
- [AccessibilityEvent delivery and parent filtering](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent)
- [Application usage statistics](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [MediaProjection consent and service requirements](https://developer.android.com/media/grow/media-projection)

The Android references establish platform behavior. The numbered defects above come from this repository and deterministic service-level reproductions; the affected phone's actual cause remains to be matched to its report.
