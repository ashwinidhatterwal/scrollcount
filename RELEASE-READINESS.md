# ScrollCount 1.2.1-rc1 verification

Counting rules are unchanged. History emits a generic notification after a saved detection or successful history deletion to trigger optional sharing. Sharing now uses recipient-encrypted latest snapshots, sharing defaults migration, key pinning, hourly network jobs and offline recipient retrieval. Existing database migration is additive.

Automated gates: counting/history regressions, authenticated encryption/tampering/key restoration, actual hourly JobService lifecycle against scheduling doubles, Worker routes/SQL against SQLite, privacy boundaries, signed SDK build.

Remaining: deploy backend version 2, physical two-phone testing including screen-off, battery restrictions, volume/feed/minimize counting, deletion/pause, connectivity recovery, midnight and stale updates. No guarantee of exact hourly scheduling. No independent key verification/forward secrecy. Play declarations/operator privacy contact remain required.

Actual FriendSync coordinator tests cover debounce, burst coalescing, newest committed totals, changes during an in-flight transfer, pause cancellation and no continuous background polling. Native UI review on a physical phone remains required.
