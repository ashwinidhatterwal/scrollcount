# ScrollCount — Play review pack

Use the final signed build and deployed backend. Do not submit until the release audit gates are complete.

## Store text

Short description: **Understand your Reels and Shorts habits with a private daily scroll counter.**

Full description:

ScrollCount helps you understand how much you scroll through Instagram Reels and YouTube Shorts. After you explicitly agree and enable its Accessibility service, it counts confirmed short-video items and estimates active feed time in the background.

See your daily Scrolls total, quick skips, viewing-time estimates and recent patterns. The optional live badge shows one combined daily count over Reels and Shorts. Pause counting whenever you want. Local history works offline and can be exported or deleted.

Optional Friends sharing lets you compare daily totals with people you accept. Sharing needs internet access and a separate profile agreement. Updates are encrypted for each friend; initial public keys are trusted from the server, then pinned. You can pause sharing, remove friends or delete the profile.

Accessibility access is used to recognise short-video feeds and transitions. ScrollCount briefly processes visible Instagram/YouTube labels and layout; it does not store captions, creators, messages, media or content URLs. It does not automate taps or swipes or record the screen. Other apps provide foreground/window package metadata only, so timing can stop. The service can also display the optional touch-through badge. Counting continues while ScrollCount is closed when access remains enabled.

Counts and feed time are estimates, not proof that a video played to completion. App layout changes, missing accessibility labels or very fast transitions can affect detection. Android may delay background sharing. ScrollCount is independent and is not affiliated with Instagram, Meta or YouTube.

No advertising or analytics SDK. Android 8.0 and newer; detection on older social-app versions requires device testing.

## Accessibility declaration evidence

- Accessibility tool: **No** (`isAccessibilityTool=false`). Core purpose: app functionality, personal awareness of short-video scrolling habits.
- Explain why a narrower API cannot supply this feature: ordinary app-usage duration does not expose individual Reels/Shorts transitions or identify a full-size short-video feed. No automated interaction is performed.
- Do not answer "no data" merely because most history is local: optional Friends sharing transmits encrypted counts derived from Accessibility observation. Accurately report access, collection/sharing and purposes according to the final form's definitions.
- Video sequence: fresh app opening → introductory screen → full separate Accessibility disclosure (scroll slowly through all text) → affirmative agreement → Android settings enabling → actual Reels/Shorts counting → combined badge → pause → optional Friends agreement and deletion controls.
- Show both agree and back/decline controls, and that counting does not start without consent. Do not substitute Android's service description for the in-app disclosure.

## Data safety preparation

This is a source-grounded inventory to map to the current Console form; the publisher must confirm the final selections.

| Data | Handling and purpose |
| --- | --- |
| Chosen name | Optional profile; sent/stored for friend identification and account functionality |
| Random profile ID and credential hashes | Optional account authentication, pairing, deletion and security; credentials travel only over HTTPS |
| Friend/invitation/public-key metadata | Optional pairing and encrypted sharing; server-visible metadata |
| Daily Reels/Shorts counts, local date, sequence and capture time | Optional sharing; encrypted for accepted friends, latest server copy expires after 7 days |
| IP/request metadata | Hosting/transport and anti-abuse processing by Cloudflare; application payload/header logging disabled |
| Local timestamps, sessions, durations, categories and item records | On-device counting/history; not automatically uploaded as full history |
| Sanitized diagnostics/CSV | User chooses whether and where to export; destination privacy practices apply |

No collected location, contacts, camera/audio, message content or media. No advertising ID, analytics SDK or automatic crash-reporting SDK. Review optional data collection under personal name/identifiers and app activity categories. Do not assume encryption exempts collection, or that all automatic ongoing friend sharing is exempt from sharing declarations. Use the current form definitions and exceptions.

## App access, content and publication

Counting requires no account. Testers/reviewers can create an optional profile with any chosen name, but Friends needs two phones/profiles and a single-use invitation. Provide reviewer instructions, a stable deployed endpoint and any requested access without posting private authentication/deletion keys publicly.

Set target audience/content rating truthfully; do not select child-directed/Families without a separate assessment of social-app content and sharing. Declare ads absent for this build. Use real screenshots, the included 512px icon and a 1024×500 feature graphic.

After backend v4 is deployed and verified, use:

- Privacy: `https://scrollcount-friends.actionbridge.workers.dev/privacy`
- Account deletion: `https://scrollcount-friends.actionbridge.workers.dev/delete`

The saved deletion key permits deletion without reinstalling. Lost-key support requires ownership verification; the app does not collect an email/phone for recovery. Monitor the public support email and process legitimate requests. Reapply deletions before using a restored D1 database.

Signed builds do not bypass Android's restricted-settings protections. Play review, tester/production access eligibility and Android/OEM permission behavior remain outside a source-code guarantee.

Official policy references: [Accessibility API](https://support.google.com/googleplay/android-developer/answer/10964491), [User Data](https://support.google.com/googleplay/android-developer/answer/10144311), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).
