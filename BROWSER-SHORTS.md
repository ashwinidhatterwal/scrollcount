# Browser Shorts and Reels — 1.2.23

Browser detections join the existing YouTube, Instagram and Facebook totals, including history, Friends totals and the daily badge. Native-app detectors are retained.

## Detection

The bounded reader checks known address-bar view IDs in the existing browser profiles. It recognises exact allowed hosts and individual-video routes:

| Platform | Hosts | Paths |
| --- | --- | --- |
| YouTube | youtube.com, www.youtube.com, m.youtube.com | /shorts/VIDEO_ID |
| Instagram | instagram.com, www.instagram.com | /reel/CODE or /reels/CODE |
| Facebook | facebook.com, www.facebook.com, m.facebook.com, web.facebook.com | /reel/NUMERIC_ID or /reels/NUMERIC_ID |

An optional trailing slash, query parameters and fragments do not change the video identity. Ordinary posts, Stories, profiles, home/search pages, regular watch pages, link redirects and non-video feed URLs do not qualify. Look-alike hosts, user-info authorities, non-HTTP(S) schemes, encoded IDs and extra path segments are rejected.

Read-only toolbar descriptions can supply a full URL when the displayed text is trimmed to the same domain. Editable focused address bars are not read. Only a salted, platform-scoped HMAC leaves the reader; raw URLs/video IDs are neither logged nor stored. Switching from one platform to another inside the same browser closes the old platform's session and establishes a new baseline.

Instagram/Facebook use URL identities only: their page captions, messages and other page content are not read. Domain-only or hidden/unreadable address bars pause counting. YouTube retains its existing bounded, scoped player-title fallback on a verified YouTube domain.

## Browser profiles and limitations

The inherited profiles include Chrome variants, Brave, Edge, Vivaldi, Samsung Internet, Opera, Opera Mini, vivo Browser and Firefox. Address-bar IDs and available URL detail vary by browser/version; these profiles have not all been device-tested. There is no support for arbitrary embedded browser views.

The browser must expose a complete video URL and update it as the visible video changes. If a site/browser retains the same URL while swiping, later videos cannot be reliably counted by this path. A matching URL is an exposure signal, not proof playback started or completed. Extremely short URL changes, loading/redirect transitions and hidden toolbars can affect accuracy. Phone testing is required.
