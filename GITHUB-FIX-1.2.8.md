# GitHub build correction for 1.2.8

Based on the latest GitHub main commit 5f9b2ec9b98bb6089415209177971633cb9e1dae.

GitHub run 38417177234 compiled and assembled the debug APK, then failed lint with four NewApi errors in ServiceHealth.exitReasons(): getHistoricalProcessExitReasons(), getTimestamp(), getReason() and getStatus(). These require API 30; the minimum supported API is 26. The caller had an API guard, but lint did not infer it across the private helper boundary.

The helper now returns immediately below API 30. This makes the runtime boundary explicit and statically visible. No lint errors were suppressed, no baseline was added, and the minimum supported Android version is unchanged. The targeted test additionally invokes the helper directly on a simulated API 26 device and verifies that the newer ActivityManager method is never called.

The health, metadata and privacy tests pass. Android SDK resource/Java/DEX compilation and debug signature verification pass. Full Gradle lint has not been rerun here: the local Gradle distribution is damaged, and the GitHub integration refused branch/file writes with HTTP 403 Resource not accessible by integration.

Upload this source package to the repository and run Build ScrollCount again. The release remains 1.2.8, build 26, because the failed run did not produce a signed Play release. The new-app badge default, compatibility profiles and service hardening are retained. The remote repository was not modified by this fix.
