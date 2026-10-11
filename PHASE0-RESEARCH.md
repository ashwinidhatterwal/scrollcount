# Phase 0 research — 7 October 2026

Android documents package/event filtering and optional accessibility-window retrieval. These APIs make an event-driven detection probe technically possible. They do not promise stable Reels/Shorts semantics or video identifiers. Only an actual device trace can establish which signals Instagram and YouTube currently expose.

Sources checked:
- Android accessibility service guide: https://developer.android.com/guide/topics/ui/accessibility/service
- Android service API: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Google Play AccessibilityService policy: https://support.google.com/googleplay/android-developer/answer/10964491

ScrollCount is a wellbeing utility, not a disability-support accessibility tool. The probe declares `isAccessibilityTool=false` and includes an explicit disclosure and affirmative consent before arming. The policy requires disclosure/consent for non-accessibility-tool uses and an Accessibility API declaration for Play distribution. This implementation does not establish Play approval; publication is outside Phase 0.

Probe hypotheses: feed-specific resource identifiers plus like/share controls establish provisional feed presence; selected content-field values can distinguish some settled items; a brief stability window can reject transient UI changes. None has been confirmed against installed app versions. Repeated captions, generic creator labels, mutable captions, missing resource IDs, overlay structures and rapid swipes are known failure modes to measure.

Design corrections to the original conceptual plan: use a random in-memory session key with HMAC rather than an unsalted content hash; never export UI text; gate content hashing behind provisional feed checks; represent unknown signals as unavailable instead of inventing counts. Do not treat absence of events as proof that viewing ended—paused videos may emit no events. Production session timing belongs in later phases.

Next engineering decision depends on the attached phone-test protocol. Full product work should wait until both detectors have evidence or a documented feasible alternative.

## First device evidence and revision 0.0.2

The supplied report from Android 16 / vivo I2302 / Instagram 449.0.0.52.84 establishes that the service receives events and exposes the player tree. Its 1,430 events and 435 scans produced no counts because the old `reel` marker missed `clips_viewer_view_pager` / `clips_video_container`. Content collection was gated off, so `contentFields=0` did not prove missing content.

The tree includes `clips_author_username` with text and a `clips_caption_component` containing anonymous descendants with descriptions. Revision 0.0.2 hashes those ephemerally after the player check. Likes, counts and the scrubber are excluded. The report also contains a scroll event with settled indices 28:28, but its source was not captured. Revision 0.0.2 records source IDs/classes and only treats exact clips-player-pager scrolls as movement evidence. Position-based identity can distinguish repeated captions. The mapping of that event to the pager remains a hypothesis until the next report.

This is positive feasibility evidence for UI access, not proof of accurate video counting. The overlay and multi-device gates remain open.

## YouTube trace and revision 0.0.3

YouTube's observed containers use `reel_recycler` and `reel_player_page_container`, not the old `shorts` resource-ID assumption. The final small window exposes no video labels or position index, and previous trees were overwritten. The new probe preserves richer earlier trees and samples event-source IDs. A YouTube-only expanded hierarchy profile is a documented Android diagnostic option, not a guarantee of recoverable content: https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_INCLUDE_NOT_IMPORTANT_VIEWS . Further counting logic must wait for the next trace rather than invent identity from playback updates.

## Deeper YouTube tree and revision 0.0.4

The 0.0.3 trace contains a full-screen player tree. It exposes anonymous footer content deeper than the diagnostic depth cap, and reports nine truncated scans. Revision 0.0.4 increases the bounded depth and hashes text in the observed left footer branches. The semantics of redacted strings and their uniqueness cannot be proven from structure alone; the five-swipe and no-swipe tests remain the decision gate.
