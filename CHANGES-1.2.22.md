# ScrollCount 1.2.22 (40) — browser Shorts detection and badge

Based on the attached 1.2.21 browser-shorts source. All existing native Instagram, Facebook, YouTube and Snapchat paths remain included.

## Findings

The supplied report recorded nine transitions with an empty package and zero event/scan counters. This matches the old browser path: it did not populate those diagnostics. The report alone cannot identify the browser or prove missed items.

Code review found that browser detection required a full Shorts URL in one exact toolbar text node. Domain-only display, alternate toolbar IDs and descriptions were not handled. Browser sessions also never enabled the badge, and its refresh guard rejected browsers even if counting succeeded. The old path relied on new accessibility events for every URL update.

## Changes

- Try read-only and edit toolbar variants, and a full Shorts URL exposed in a toolbar description when its text is missing or contains only the YouTube domain. A focused read-only node is allowed; focused editable/password nodes are never read.
- Add candidate browser mappings for Chrome Dev/Canary, Opera and vivo, alongside the previous Chrome/Beta, Brave, Edge, Vivaldi, Samsung Internet and Firefox profiles. These are compatibility candidates, not device-verified guarantees.
- On a confirmed YouTube domain, a bounded fallback can recognise an accessible reel-player container and hash its title locally. A Shorts navigation link alone cannot qualify. Titles must be inside the player subtree. A changed title requires fresh browser scroll evidence and stability; action/number changes alone cannot count. Ordinary watch pages and unrelated sites are excluded.
- Alias URL/title identities on source changes to avoid a second count when the toolbar changes display. A transition coinciding with a source change can be missed conservatively.
- Sample confirmed active Shorts every 500 ms, stopping on exit/app switch/reset/screen-off. Unavailable reads get at most three timed retries before waiting for another event. Reads use the existing background lane and stale-result guard.
- Enable the daily badge for confirmed browser Shorts. Browser Shorts contribute to YouTube totals.
- Populate browser package, event/read counts, read duration, detection mode and availability flags in diagnostics. No URL, title or raw content is exported.
- Update local disclosure and privacy pages to describe browser reads. Existing sharing, release keys and backend configuration are retained.

## Verification

All pure-Java regressions, including the production URL parser, pass. The Android service harness compiles all production Java against API 36 and passes 27 cases, including three new browser cases covering URL/description reads, read-only focus versus typing, domain-gated title fallback, non-Shorts/unrelated-page exclusion, exact URL changes, source aliasing, stability/swipe gating, badge readiness, event/read telemetry, polling without further events, and cancellation after switching apps. Existing 24 native counting regressions still pass. All ten Python regression scripts pass.

No physical browser/device test or APK/AAB build was performed. The player/title fallback fixtures are synthetic and do not establish what a particular browser exposes. Browsers hiding both the full URL and player identity remain unsupported; identical titles and very fast title-only swipes can be missed. Fullscreen with no readable toolbar is intentionally not inferred from arbitrary page content.

## Phone test

1. Build/install 1.2.22 using the existing signing key; leave Accessibility and the live badge enabled.
2. Open YouTube Shorts in the browser, pause briefly on the first item, then swipe through about ten distinct Shorts.
3. Check the badge and the home screen YouTube total. Regular YouTube videos, home/search and other sites should not increment it.
4. If it still fails, share a diagnostic file immediately and include the browser name. The package and browserState/mode/bar/shortsPlayer/titleAvailable flags now show which detection step failed. State 0 = unavailable, 1 = editing, 2 = other site, 3 = regular YouTube page, 4 = domain only/no player identity, 5 = confirmed Shorts.

Full source only. Nothing was pushed to GitHub or deployed.
