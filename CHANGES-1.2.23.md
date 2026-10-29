# ScrollCount 1.2.23 (41)

Base: latest GitHub main fetched for this task, commit 12779cc8fc20fdc847ac7cdee0d6af49c56b3eee (2026-10-26), version 1.2.22 (40). That version already contains YouTube browser detection; this change extends it.

- Added Instagram and Facebook browser Reel URL recognition, with exact-domain/route validation.
- Added platform routing to browser results, diagnostics and CountingEngine so Reel counts/time/history belong to Instagram or Facebook rather than YouTube.
- Separated transient URL fingerprints by platform. Query/tracking changes and trailing slashes cannot duplicate the same Reel.
- Preserved YouTube browser title fallback and native-app detectors. Instagram/Facebook do not read page text; unavailable/host-only URLs pause counting.
- Added spoofed-host, invalid-scheme, user-info, ordinary-page and route exclusions. Updated browser privacy disclosure and documentation.

Validation completed: pure-Java suite; all ten Python regression scripts; backend Node regression suite; git diff whitespace checks. The service suite compiles all Android source against official Android API 36 and passes 28 orchestration cases. New cases exercise the real browser event/read/result paths for Instagram/Facebook, query deduplication, platform switches, normal-watch exits, host-only pauses, full-URL toolbar descriptions, conflicting domains, address editing, page-text exclusion and diagnostic URL redaction.

No physical browser/device tests, Gradle APK/AAB packaging or lint run were performed. This archive contains full source, not an APK. Browser compatibility depends on a readable changing video URL; see BROWSER-SHORTS.md. Changes have not been pushed to GitHub.
