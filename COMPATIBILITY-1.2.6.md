# ScrollCount 1.2.6 — compatibility candidate

Based on GitHub main commit 2b0bfb5 (version 1.2.5, build 23). Version 1.2.6 uses build 24. No remote repository or deployed backend was changed.

## Changes

- Instagram Lite, Facebook, Facebook Lite and Snapchat Spotlight now have explicit package profiles. Lite counts appear under Instagram. Facebook and Snapchat have separate By App rows and session/day history.
- Renamed modified apps can be assigned to the matching original platform in Settings → Modified apps. Exact-package consent is required; removal revokes inspection immediately. Modifications retaining the official package use the existing profile. Matching a name alone never grants access.
- Instagram accepts additional pager/caption IDs and ViewPager2. Budgets cover deeper layouts. Older Android (8–11) receives expanded accessibility hierarchy while a supported app is selected.
- Up to three delayed retries handle unavailable screen roots. Expensive scans increase the coalescing interval, bounded at 1.6 seconds. Event exceptions pause timing rather than escaping the accessibility callback.
- YouTube footer lookup uses the selected package namespace, with a bounded hierarchy fallback if direct ID lookup is unavailable.
- Social profiles require a full-screen Reels/Spotlight player marker, like/favorite and share controls, stable non-empty caption/author evidence and verified player movement. Comments, stories, editable content and ordinary-feed scrolls are rejected by the available signals. Captions are hashed temporarily, never saved or uploaded.
- The combined daily badge and Friends totals include all supported platforms. The existing encrypted Friends wire format is retained: non-YouTube feeds are grouped as Reels; YouTube remains Shorts. Instagram Lite is not a separate row.
- Updated local/public privacy text. Existing installations must accept the expanded accessibility disclosure once; saved history is preserved.

## Validation

Portable counting, lifecycle, crypto, privacy, history, sharing/job/deployment and backend regression tests pass. New profile tests cover exact-package mapping/removal, Lite/platform attribution, ViewPager2 and ordinary-feed/story/comment negatives.

Android resources, Java sources and DEX compile using Android SDK 36. A locally debug-signed APK was generated and its signature verified. The local Gradle distribution is damaged (invalid gradle-model-core JAR), so Gradle lint and Play AAB generation were not completed here. The repository's GitHub Actions workflow remains available for the full signed build with its existing signing secrets.

## Required device validation

This is a compatibility candidate, not a claim that all app versions work. Facebook, Lite and Snapchat signal rules are conservative and have not been validated against actual accessibility trees from the user's devices. They may expose anonymous controls or hide captions; then the counter intentionally does not count. Identical content labels and very fast swipes may also be missed. The older-device failure's precise cause remains unconfirmed without its model, Android version and diagnostics.

Test 20 slow and 20 quick transitions in each app, then normal feeds, Stories, comments, sharing, lock/unlock and app switching. Compare manually counted exposures with Today and sessions. For renamed apps, first select the original platform and installed modified package in Settings. Remove it afterward to verify that inspection stops.

If an app stays at zero, enable Local diagnostics, open its short-video feed, swipe several times, then export the sanitized report. It contains layout metadata and redacted label presence, not actual content. Real traces are needed to replace provisional rules with tested layouts.

The build still supports Android 8.0 and later; support was not extended below API 26. Accessibility disabled by Android or OEM settings cannot be silently re-enabled by the app.
