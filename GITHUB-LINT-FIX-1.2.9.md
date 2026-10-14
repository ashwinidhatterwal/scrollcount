# GitHub lint fix for ScrollCount 1.2.9

Based on latest GitHub commit 18e0459a4c82d30b22c868090161c9ab7b314948. Run 38513624553 passed regression checks and assembled the debug APK, then failed Android lint with one SuspiciousIndentation error in ProbeService.applySource().

Corrected the indentation of applySource() and applyScan(). Verified that all non-whitespace source characters are identical. No counting behavior, API guard, version, signing configuration or lint rule was changed. The release remains 1.2.9, build 27.

The actual service-admission test compiles the complete Java source and passes. Privacy and release-metadata tests pass. Git diff has no whitespace errors and the ProbeService diff is empty when whitespace is ignored. Full GitHub lint must be rerun after uploading this corrected source; the integration previously denied repository writes with HTTP 403. No remote change or verified new GitHub run is claimed.
