# Phase 2 — Reliability, v0.2.0 (code 8)

## Changes

Recent confirmed feed evidence survives a partial swipe layout for at most five seconds. Partial scans do not refresh that timeout. A recognized exit, overlay, profile, truncated scan, small player, window change, screen-off or app switch clears confirmation. Exact Instagram position events still commit individually; YouTube still requires a distinct, briefly confirmed content fingerprint after player movement.

This addresses a plausible cause of the supplied v0.1.2 Instagram miss: 19→47 exposed 28 position changes but accepted 27, omitting index 21. The report has no per-scan rejection reasons, so the actual cause cannot be proven from it. New RAM-only counting decisions explain rejected indexed events and insufficient scans without storing UI text. No history migration, network, new platform or reminder features are added.

Picture-in-picture and small split-screen roots intentionally suspend counting. Full-player geometry is checked in full scans and quick YouTube reads. Do not interpret these layouts as supported yet.

## Benchmark on the phone

Install over v0.1.2. Existing saved history is retained. Record dashboard values before each run; Diagnostics resets when the observed platform changes, so export each platform separately. A continuous run of N new positions after the baseline should produce N accepted transitions. Seen includes initial exposures >=2 seconds; Quick skips is separate. Return visits count as exposures, not unique videos.

| Run | Actions | Expected behavior |
| --- | --- | --- |
| Instagram continuous | Stay on baseline 3 seconds; advance 30 times, mixing 0.5–1.5-second and 3-second intervals | 30 changes; short intervals retained as skips |
| YouTube continuous | Baseline 3 seconds; advance 20 times, with 10 quick skips | 20 accessible distinct changes; report any misses |
| Reverse | From baseline, advance 5 and go back 5 | 10 changes; repeated source events add zero |
| Controls | Stay on one item; like, pause, expand caption, open comments and share sheet | Zero new video changes; overlay time excluded |
| Navigation | Open profile/Home/results and scroll; return to the player | Outside scrolling adds zero; reentry establishes baseline |
| Foreground | Open another app for 20 seconds; return | Outside time excluded; stored history retained |
| Lock | Lock phone for 20 seconds; unlock into feed | Locked time excluded; new baseline |
| Geometry | Rotate; then enter PiP/small split screen | Normal full-player rotation needs verification; small players suspended |
| Restart | Disable/re-enable service, reboot, then open app | Completed history retained; no time invented during downtime |
| Content variants | Buffering, ads, sponsored items, live content, account switching | Distinct supported feed exposures only; unsupported states should suspend |

For each run record actual changes, accepted changes, observed false positives and missed changes. Count equality alone cannot prove accuracy because false positives and misses may cancel. Target >=95% precision and recall with no overlay false positives across repeated runs on both apps. More devices and app versions remain required before calling Phase 2 validated.

## Checks completed locally

Seven Java regression programs and the exact SQLite schema/query test pass. The new synthetic 19→47 scenario has all 28 transitions with a partial scan before 21, duplicate events and an overlay rejection. Guards cover stale evidence, clock reversal, truncation, PiP/split-screen sizes and baseline after exit. Existing YouTube rapid-fingerprint, timing, platform switching, pause/resume and history tests continue to pass. APK compiled and signature checked against v0.1.2.

No physical-device or emulator run was performed here. Real app callbacks, rotation, account changes, buffering/ads/live content, reboot, OEM behavior and power impact remain phone-test items. Abrupt process death can still lose the unfinished item; completed exposures remain saved. Identical or unavailable YouTube labels and transitions faster than accessible confirmation can still be missed. The original multi-device >=95% gate has not been met.
