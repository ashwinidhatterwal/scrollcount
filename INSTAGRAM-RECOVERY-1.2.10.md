# ScrollCount 1.2.10 — Instagram recovery

Base: ashwinidhatterwal/scrollcount main, 6c3a2fd96b8641c69cd5af9ae3fd9edef067faff.
Version: 1.2.10, code 28.

## Changes

- Accept a recently advanced Instagram pager during a background scan instead of discarding every moving frame. Stale frames remain rejected.
- Recognize observed Instagram pager resource IDs even when the wrapper class changes. A confirmed pager plus video container can establish the player without like/share text labels.
- Start the bounded tree traversal deadline after acquiring the root, preserving the traversal budget on slower accessibility calls.
- Count stable author/caption changes when Instagram supplies no usable index, requiring a verified reel-pager movement. Lock the identity strategy after the initial baseline to prevent duplicate counts if indices appear later.
- Clear fallback movement on overlays and session changes. Existing background-reader safeguards, YouTube handling, supported social-app profiles, modified-app selection, and fresh-install live-badge defaults are retained.

## Validation

The Java regression suite passes, including two new Instagram tests covering moving-pager frames and unindexed identities (20 synthetic exposures each), duplicate suppression, action edits, overlays, stale frames, navigation, and strategy changes. Existing YouTube tests pass.

Android SDK compilation, resource packaging, DEX generation, and local debug APK signature verification pass for the final source. Read fences, privacy, service-health, automatic sync, history SQL, hourly scheduling, friend defaults, release metadata, deployment scripts, and backend checks passed during this revision.

These are code-path regressions; this revision has not been verified on the affected physical phone. The exact cause on that phone remains unconfirmed without diagnostic evidence. Full local Gradle lint was unavailable because the local Gradle distribution contains corrupt jars; new GitHub CI has not run.

## Updating

GitHub integration denied repository writes with HTTP 403, so these changes are delivered as a source bundle and have not been pushed. Build through the repository's existing signing workflow and validate lint/CI before distributing.

For an existing Play Store installation, upload the release AAB through Play internal testing to receive a Play-signed update. A locally signed APK can conflict with the Play signing certificate. The locally generated debug APK is not included in this bundle.

Android does not allow this app to silently re-enable accessibility after it is disabled. Service diagnostics and recovery safeguards reduce failures but cannot guarantee every manufacturer's process behavior or every social-app layout.
