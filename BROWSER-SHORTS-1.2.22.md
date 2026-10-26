# Browser YouTube Shorts (draft for 1.2.22)

Counts YouTube Shorts watched in a mobile browser, as "YouTube" in the existing totals.

## How it works
- `BrowserSignals` (new, pure Java) knows each supported browser's address-bar view id and parses
  its text: `youtube.com | www. | m.youtube.com` + `/shorts/<11-char id>` = a Short.
  Look-alike hosts (`youtube.com.evil.com`, `music.youtube.com`) are rejected.
- `ProbeService.browserEvent / readBrowser / applyBrowser` (new, in ProbeService) reads ONLY the
  address bar. No page content is touched. A focused (being typed in) bar is never read.
- Each new id = one new Short. It goes through the same `TransitionProbe` + `CountingEngine` as app
  Shorts, so seen / skipped / longer-view classification and history work unchanged.
- Only a salted HMAC of the id is kept, in memory (same policy as in-app identities). The URL is
  never stored or logged.
- Leaving /shorts/ (or switching app, screen off) closes the session.
- Ordinary pages are sampled at most about once per second; an active Shorts session about 4x/s.

## Supported browsers
Chrome and Chrome Beta, Brave, Edge, Vivaldi (ids follow Chrome's `url_bar` pattern),
Samsung Internet and Firefox (ids best-effort). Edit `BrowserSignals.BROWSERS` to add more.

## MUST be verified on a real phone (not testable off-device)
1. Does the address bar's accessibility text include `/shorts/<id>` or only the domain?
   If only the domain, the status line reads "Browser shows only the domain..." and nothing counts.
2. Does the address bar update as you swipe to the next Short?
3. Confirm the view id for each non-Chrome browser you care about (Diagnostics tree dump).

## Known limits
- No live badge for browser Shorts yet (the badge is tied to supported-app targets).
- Shorts embedded inside normal YouTube pages/feeds (not /shorts/) are not counted.
- Browser-in-app webviews and Custom Tabs from other apps are not covered.
- Not compiled with Gradle here (no Android SDK in this environment).
  `tests/BrowserSignalsTest.java` was run with the JDK: all checks passed.
