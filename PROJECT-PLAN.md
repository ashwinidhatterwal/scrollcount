# ScrollCount — Project Plan & Work-Mode Handoff

## 1. Product Goal

Build an **Android app that automatically counts how many short-form videos a user watches**, starting with:

- Instagram Reels
- YouTube Shorts

The app should also estimate:

- Total short videos seen
- Time spent on short-video feeds
- Average dwell time per video
- Quick skips
- Longer watches
- Session lengths
- Daily / weekly trends
- Lifetime count

The app should work **fully on-device**.

Primary promise:

> **Your phone counts your steps. ScrollCount counts your scrolling.**

Alternative positioning:

> **You know your screen time. But do you know how many Reels you watched?**

The app is a **digital-wellbeing utility**, not a social app.

---

# 2. Core Product Philosophy

The app must be:

- Automatic after setup
- Low battery
- Local-first
- Privacy-preserving
- Easy to understand
- No account required
- No backend required for V1
- No screen recording
- No screenshots
- No storing Reel/Short content
- No storing captions, creators, comments, or audio
- No AI required for V1
- No unnecessary dependencies
- Clean and maintainable code

The product should count behavior, not collect content.

---

# 3. What the App Can and Cannot Claim

Important distinction:

The app cannot reliably know whether the user **fully watched** a Reel/Short.

Therefore use terminology carefully.

Recommended internal/event categories:

```text
SEEN
SKIPPED
WATCHED_LONGER
```

Example interpretation:

- < 2 seconds: skip / accidental exposure
- >= 2 seconds: seen
- >= 10 seconds: longer watch

These thresholds should be configurable internally and tuned through testing.

The app should report:

> "Short videos seen"

not:

> "Videos fully watched"

unless we later obtain a reliable playback-completion signal.

---

# 4. Main Technical Approach

The likely Android implementation uses:

## AccessibilityService

Why:

Android's standard UsageStats APIs can tell us that Instagram or YouTube was active, but they cannot reliably count individual Reel/Short transitions.

Accessibility events can potentially tell us:

- Which app is foreground
- Whether the visible screen is a Reels/Shorts feed
- When content changes
- When the user scrolls
- When accessibility tree labels/layout change

The app should use AccessibilityService only for this narrow purpose.

---

# 5. High-Level Detection Pipeline

```text
Foreground app
     ↓
Is supported app?
     ↓
Platform detector
     ↓
Is short-video screen?
     ↓
Observe UI/accessibility events
     ↓
Build temporary content fingerprint
     ↓
Detect stable transition
     ↓
Close previous item dwell timer
     ↓
Classify previous item
     ↓
Increment counters
     ↓
Start next item timer
```

---

# 6. Platform Adapter Architecture

Do NOT hardcode platform-specific logic throughout the app.

Create a clean detector interface.

Conceptually:

```text
ShortVideoDetector
├── InstagramDetector
├── YouTubeDetector
├── FacebookDetector      // later
└── SnapchatDetector      // later
```

Possible interface responsibilities:

```text
supportsPackage(packageName)
isShortVideoScreen(rootNode)
extractTransientFingerprint(rootNode)
detectTransition(event, state)
```

Keep the shared counting/session logic completely independent of Instagram/YouTube-specific UI details.

This is critical because social apps may change their UI frequently.

If Instagram changes:

> Update InstagramDetector only.

Do not rewrite the application.

---

# 7. Short-Video Screen Detection

## Instagram

Goal:

Determine whether the user is inside the Reels viewing experience.

Potential signals:

- Foreground package
- Accessibility node structure
- Visible navigation labels
- Reels-specific controls
- Content descriptions
- Stable screen-layout pattern

Avoid relying on one exact UI string if possible.

Use multiple weak signals.

Example:

```text
package = Instagram
AND
reels-like control pattern detected
AND
full-screen vertical content layout
```

## YouTube

Goal:

Determine whether the user is inside the Shorts player.

Potential signals:

- Foreground YouTube package
- Shorts-specific navigation state
- Shorts action controls
- accessibility node patterns
- visible labels / content descriptions

Again:

Use a combination of signals instead of one brittle string.

---

# 8. Content Fingerprint

We need to know:

> "Did the user move to a different Reel/Short?"

Do not store visible content.

Instead generate a **temporary fingerprint** from stable accessibility properties.

Possible inputs:

```text
selected accessibility labels
creator label if exposed
caption fragment if exposed
button structure
node IDs
layout signature
content descriptions
```

Then hash the normalized representation.

Example:

```text
fingerprint = SHA-256(normalizedTransientData)
```

Only retain the hash long enough to detect a transition.

Prefer not to persist fingerprints permanently unless testing shows a real need.

If stored temporarily, delete at session end or after a very short TTL.

---

# 9. Transition Detection

Do not count every scroll event as a new video.

Use multiple signals:

```text
vertical scroll event
+
accessibility tree change
+
fingerprint change
+
new content stable for N milliseconds
```

Then declare:

```text
NEW_SHORT_VIDEO
```

This prevents:

- UI animations
- opening comments
- expanding captions
- liking
- pausing
- temporary overlays

from being counted as a new Reel.

---

# 10. Stability Window

Use a small debounce/stability window.

Example:

```text
candidate new fingerprint
↓
wait 500–1000 ms
↓
still stable?
↓
yes → transition accepted
```

Exact values should be tuned experimentally.

Do not make transitions feel delayed in the dashboard.

The user does not need real-time millisecond accuracy.

Reliability matters more.

---

# 11. Dwell-Time Tracking

For each short video:

```text
startTimestamp
endTimestamp
duration
platform
sessionId
classification
```

Example:

```text
Instagram
start 20:31:04
end   20:31:18
duration 14s
classification SEEN
```

Then calculate:

- count
- average dwell
- median dwell
- quick skips
- longer watches
- total session duration

---

# 12. Session Detection

Define a short-video session.

Possible rule:

A session begins when:

```text
user enters Reels/Shorts
```

and ends when:

```text
user leaves short-video screen
OR
supported app leaves foreground
OR
inactivity exceeds threshold
```

Possible inactivity threshold:

```text
60–120 seconds
```

Tune during testing.

Store:

```text
Session
├── platform
├── startedAt
├── endedAt
├── seenCount
├── skippedCount
├── longerWatchCount
└── totalDuration
```

---

# 13. Minimal Data Model

Keep it compact.

Possible entities:

## ShortViewEvent

```text
id
timestampStart
timestampEnd
durationMs
platform
classification
sessionId
```

## Session

```text
id
platform
startedAt
endedAt
seenCount
totalDurationMs
```

## DailySummary

Optional cache, not source of truth.

```text
date
instagramCount
youtubeCount
totalCount
totalDuration
quickSkips
longerWatches
```

Could be computed dynamically at first.

Do not create redundant data until needed for performance.

---

# 14. Storage

Use local persistence.

Recommended:

- Room OR SQLite
- Keep schema simple
- No cloud sync in V1

For tens of thousands of events, storage is tiny.

No media files are stored.

---

# 15. Privacy Requirements

This is one of the product's strongest selling points.

The app should say:

> **No account. No cloud. No browsing history.**

Store only:

```text
timestamp
duration
platform
classification
session
```

Do NOT store:

- Reel caption
- creator name
- Reel URL
- comments
- likes
- audio title
- screenshots
- screen recording
- private messages
- typed text
- passwords
- accessibility tree dumps

Any UI text used for fingerprinting should be processed ephemerally and discarded.

---

# 16. AccessibilityService Privacy Rules

The AccessibilityService must be narrowly scoped.

Implementation principles:

- Only react to supported packages.
- Ignore all other apps immediately.
- Never inspect unrelated app content.
- Never log raw accessibility text in production.
- No telemetry containing accessibility content.
- No remote upload.
- No keylogging-like behavior.
- No input interception.
- No automated taps/swipes.
- No screen control.

The service is for:

> detecting short-video feed presence and transitions.

Nothing more.

---

# 17. Onboarding

The hardest part of this app is trust.

Do not ask for Accessibility permission immediately without explanation.

Recommended onboarding:

### Screen 1

> **Know how much you really scroll.**

### Screen 2

> ScrollCount counts Reels and Shorts automatically.

Show:

```text
Today
284 short videos
1h 47m
```

### Screen 3

> **Why Accessibility is needed**

Explain:

- Android does not expose individual Reel/Short counts.
- ScrollCount uses Accessibility only to detect when you move between short videos.
- It does not record your screen.
- It does not read messages.
- It does not store Reel content.
- Everything stays on-device.

### Screen 4

Button:

> **Enable counting**

Deep-link to Accessibility settings.

---

# 18. Dashboard — V1

Keep homepage extremely simple.

Example:

```text
TODAY

        327
   SHORT VIDEOS

Instagram          211
YouTube            116

Time             1h 58m

Average             21s

Quick skips         142

Longest session      31m
```

Then:

```text
7 DAY TREND
```

Simple chart.

No clutter.

---

# 19. Lifetime Counter

This should be a strong product hook.

Example:

```text
LIFETIME

12,842
short videos seen

93h 14m
estimated scrolling time
```

Also show:

```text
Since 12 Oct 2026
```

Do not imply history from before installation.

---

# 20. Healthy Progress, Not Addiction Gamification

Do NOT celebrate higher Reel counts.

Bad:

> Congratulations! 10,000 Reels!

Good:

```text
Today
83 Reels

Your usual Tuesday
176

↓ 53%
```

Possible encouragement:

> You stopped earlier than usual tonight.

Avoid shame-heavy language.

The app should inform, not scold.

---

# 21. Optional Goals — Later

Possible feature after core counting is reliable:

```text
Daily short-video goal
```

Example:

> Keep below 150 today

or:

> Keep short-video time below 45 min

But do not build intervention/blocking before counting is accurate.

First become the best counter.

---

# 22. Notifications — Later

Possible notifications:

```text
You have watched 100 short videos today.
```

or:

```text
This scrolling session has lasted 30 minutes.
```

Must be optional.

Do not spam users.

No notifications in the earliest prototype unless needed for testing.

---

# 23. No Backend Requirement

V1 requires:

```text
₹0/month infrastructure
```

No:

- Firebase
- server
- API
- account system
- cloud database
- AI service
- OCR
- video upload
- screen capture

The application can be fully local.

---

# 24. Battery Strategy

Accessibility services can be cheap if implemented carefully.

Rules:

- Ignore unsupported packages immediately.
- Avoid repeated full accessibility-tree traversal.
- Debounce events.
- Do not poll continuously.
- Do not run timers at high frequency.
- Use timestamps instead of per-second background updates.
- Batch database writes where useful.
- Update UI only when app is open.
- Keep logging disabled in release builds.

Goal:

> User should not notice ScrollCount in battery usage.

---

# 25. Performance Architecture

Preferred event flow:

```text
Accessibility event
↓
package check
↓
supported?
    no → return immediately
↓
platform detector
↓
relevant short-video screen?
    no → update exit state
↓
lightweight fingerprint check
↓
transition state machine
↓
persist compact event
```

Do not run expensive logic on every Android accessibility event.

---

# 26. State Machine

Use one small shared state machine.

Possible states:

```text
OUTSIDE
ENTERING
ACTIVE
TRANSITION_CANDIDATE
ACTIVE_NEW_ITEM
EXITING
```

Track:

```text
currentPlatform
currentFingerprint
candidateFingerprint
currentItemStart
sessionStart
lastRelevantEvent
```

Do not spread counting logic across UI and detectors.

---

# 27. Clean Code Structure

Suggested package structure:

```text
app/
├── accessibility/
│   ├── ScrollAccessibilityService
│   └── DetectionStateMachine
│
├── detector/
│   ├── ShortVideoDetector
│   ├── InstagramDetector
│   └── YouTubeDetector
│
├── data/
│   ├── AppDatabase
│   ├── ShortViewEvent
│   ├── Session
│   └── StatsRepository
│
├── domain/
│   ├── Platform
│   ├── ViewClassification
│   └── StatsCalculator
│
└── ui/
    ├── onboarding/
    ├── dashboard/
    └── settings/
```

Avoid adding more layers unless necessary.

No Hilt/Koin required initially.

Manual dependency wiring is acceptable for a project this size.

---

# 28. Technology Choice

Recommended:

- Kotlin
- Jetpack Compose
- Android AccessibilityService
- Room
- DataStore for preferences
- Material 3

Avoid extra dependencies.

Charts can initially be custom/simple Compose drawing or a lightweight library only if needed.

---

# 29. Supported Android Versions

Target modern Android first.

Possible starting target:

```text
minSdk 28 (Android 9)
```

If Accessibility behavior across old Android versions creates unnecessary complexity, reevaluate based on actual testing.

Do not sacrifice reliability for theoretical device coverage.

---

# 30. Platform Scope

## V1

Only:

- Instagram Reels
- YouTube Shorts

## Later

Possible:

- Facebook Reels
- Snapchat Spotlight
- other short-video feeds

Do NOT add more apps until Instagram + YouTube counting is reliable.

---

# 31. Phase Plan

## Phase 0 — Research / Detection Probe

Goal:

Verify that Instagram and YouTube expose enough accessibility information to reliably detect Reel/Short transitions.

Build a minimal internal/debug APK.

Features:

- AccessibilityService
- Log supported package events
- Show event types
- Show sanitized node structure
- Show temporary fingerprint
- No production dashboard

Test:

- Enter Reels
- Swipe 20 times
- Like video
- Pause
- Open comments
- Open profile
- Return
- Switch apps
- Rotate phone
- Lock/unlock

Success criterion:

> Transition count is close to actual swipes without false counting from overlays.

Do NOT build the full app before this is proven.

## Phase 1 — Counting Engine

Build:

- ShortVideoDetector interface
- InstagramDetector
- YouTubeDetector
- Shared DetectionStateMachine
- dwell tracking
- session tracking
- local persistence
- debug counter screen

Goal:

Accurately count short-video transitions.

No charts.

No fancy UI.

No notifications.

## Phase 2 — Reliability

Focus entirely on edge cases.

Test:

- fast swipes
- slow watches
- swiping backward
- comments open
- sharing screen
- profile screen
- pause
- app backgrounded
- picture-in-picture
- internet buffering
- ads inside feed
- live videos
- promoted/sponsored videos
- switching Instagram accounts
- YouTube ads
- screen rotation
- split-screen
- accessibility service restart
- phone reboot

Add regression test cases.

Target:

```text
>= 95% transition-count accuracy
```

on the supported tested versions before polishing UI.

## Phase 3 — Product UI

Build:

- onboarding
- permission explanation
- dashboard
- Today stats
- 7-day trend
- platform breakdown
- time spent
- average dwell
- quick skips
- longest session
- lifetime counter
- settings

Keep UI extremely clean.

## Phase 4 — Battery & Performance

Measure:

- CPU use
- battery use
- memory
- event volume
- database write frequency

Optimize:

- early package filtering
- debouncing
- tree traversal
- caching
- batching

Target:

> Negligible noticeable battery impact.

## Phase 5 — Privacy / Release Hardening

Audit:

- no raw accessibility text stored
- no sensitive logs
- no Internet permission unless genuinely needed
- no analytics initially
- clear privacy policy
- clear accessibility disclosure
- explicit consent
- data deletion/reset feature

Add:

```text
Reset all ScrollCount data
```

## Phase 6 — Optional Wellbeing Features

Only after counting is trusted.

Possible:

- Daily goal
- Session reminder
- Personal baseline
- Weekly summary
- "Lower than usual today"
- optional streak for staying under goal

Do not gamify higher consumption.

---

# 32. Testing Strategy

Create a manual benchmark protocol.

Example:

### Test A

Watch exactly:

```text
50 Instagram Reels
```

Record manually.

Compare ScrollCount result.

### Test B

Watch:

```text
25 YouTube Shorts
```

Include:

- 5 quick skips
- 5 >20 sec watches
- comments on 3
- like 4
- go back twice

Compare app result.

### Test C

Mixed:

```text
Instagram 20
YouTube 20
Instagram 10
```

Verify sessions.

---

# 33. Accuracy Metrics

Track during internal testing:

```text
True transitions
Detected transitions
False positives
Missed transitions
```

Calculate:

```text
precision
recall
absolute count error
```

Primary user-facing target:

> Daily total should be close enough that the user trusts it.

False positives are especially damaging because they inflate counts.

---

# 34. Device Test Matrix

Test at minimum:

- Pixel / stock-ish Android
- Samsung One UI
- Xiaomi/HyperOS if possible
- Vivo/iQOO/Funtouch if possible
- OnePlus/OxygenOS if possible

Accessibility behavior and process management may differ.

Also test Android 12 through Android 16 where practical.

---

# 35. UI Change Resilience

Instagram/YouTube may update.

Each detector should expose a health status.

Possible internal state:

```text
WORKING
DEGRADED
UNSUPPORTED_VERSION
```

If a detector appears broken:

> Do not silently report nonsense.

Better:

```text
Instagram counting temporarily unavailable.
YouTube counting is working.
```

Reliability beats pretending.

---

# 36. Detector Self-Test

Optional later feature:

When the user opens Instagram Reels for the first time after an update:

- observe a few transitions
- ensure expected signals exist
- if detector confidence is low, mark degraded

Do not upload UI structures.

Everything stays local.

---

# 37. Play Store / Policy Considerations

This project relies on AccessibilityService, so release policy must be treated seriously.

Before Play submission:

- Re-check current Google Play AccessibilityService policy.
- Declare AccessibilityService use honestly.
- Do not claim the app is an accessibility tool for disabled users.
- Provide clear prominent disclosure.
- Explain exactly what data is accessed.
- Explain that counting is local.
- Obtain explicit user consent.
- Limit use strictly to the declared purpose.

Do not build unrelated automation features into the service.

Do not make hidden interactions with other apps.

---

# 38. Open-Source Possibility

This app is a strong candidate for open-source release because:

- no backend secrets
- privacy benefits from transparency
- community can help repair detectors after UI updates
- device-specific compatibility can improve faster

Possible model:

```text
Core app — open source
Detector adapters — open source
No cloud dependency
```

A public detector test suite could be useful later.

---

# 39. Possible Product Names

Working names:

- ScrollCount
- ReelCount
- ScrollMeter
- ScrollSteps
- ReelMeter
- SwipeCount
- ScrollTrack
- ShortCount

Best current working name:

# ScrollCount

It clearly explains the job.

---

# 40. What NOT to Build Initially

Do not add:

- blocking apps
- parental controls
- VPN monitoring
- screen recording
- OCR
- AI
- cloud sync
- accounts
- social leaderboards
- friends
- gamification
- browser extension
- iOS version
- desktop dashboard
- Facebook/Snapchat
- detailed content categorization
- Reel creator tracking
- caption history
- mood analysis

The product must first answer one question reliably:

> **How many short videos did I watch?**

---

# 41. V1 Success Definition

A successful V1:

1. User installs app.
2. Reads clear privacy explanation.
3. Enables Accessibility.
4. Uses Instagram Reels / YouTube Shorts normally.
5. ScrollCount automatically counts transitions.
6. At night the user sees an accurate estimate.
7. Battery impact is negligible.
8. No content leaves the device.
9. No account is required.
10. App survives normal Instagram/YouTube navigation without overcounting.

---

# 42. Long-Term Vision

If V1 works well, ScrollCount can become a broader **behavioral counter for infinite-scroll consumption**.

Possible future metrics:

```text
short videos
feed refreshes
scroll sessions
late-night scrolling
average stopping point
platform switching
```

But keep the original identity:

> **A pedometer for scrolling.**

The analogy is powerful:

```text
Steps today        8,432
Short videos today   284
```

---

# 43. Immediate Next Task for Work Mode

Start with **Phase 0 only**.

Build a minimal Android debug app that:

1. Registers an AccessibilityService.
2. Filters events to Instagram and YouTube only.
3. Detects whether the user is in:
   - Instagram Reels
   - YouTube Shorts
4. Shows a local debug panel containing:
   - current supported app
   - current screen state
   - last accessibility event type
   - sanitized temporary fingerprint
   - transition candidate count
   - confirmed transition count
5. Logs no raw sensitive text permanently.
6. Uses no backend and no Internet permission.

Do not build Room, charts, onboarding, goals, notifications, or polished UI until Phase 0 proves that reliable detection is technically feasible.

The first milestone is:

> **Manually swipe through 50 Reels and have the debug counter report approximately 50 without counting likes/comments/overlays as extra videos.**

If Phase 0 cannot reach acceptable reliability, redesign the detector before building the rest of the product.

---

# 44. Coding Principles to Preserve

The project owner explicitly wants clean and efficient code.

Rules:

- Do not write more code than needed.
- Keep detectors isolated.
- Keep counting logic independent.
- Prefer simple state machines.
- Avoid premature abstraction.
- Avoid unnecessary dependencies.
- Remove dead code after experiments.
- Never leave debug UI/logging in production builds.
- Never store raw accessibility content.
- Measure before optimizing.
- Preserve working checkpoints after each phase.
- Each phase should remain independently testable.
- Do not add features until the previous phase is reliable.

---

# 45. Core Product Sentence

If a future model loses context, return to this sentence:

> **ScrollCount is an offline Android app that automatically counts how many Instagram Reels and YouTube Shorts a user sees, using a narrowly scoped AccessibilityService, while storing only local timing/count metadata and never collecting the content itself.**
