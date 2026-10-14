# ScrollCount 1.2.12 — Instagram pipeline correction

Base: GitHub main `90fc224a1c121a7b848653fe3139220114357387` (1.2.11, successful CI). Version 1.2.12, code 30.

This revision corrects additional failure paths in the actual Instagram service pipeline. It does not establish the exact cause on a physical phone without observing that phone.

## Changes

- Request expanded hierarchy and view IDs from startup, including the initial unknown-target state. Instagram previously omitted expanded hierarchy on modern Android until some player evidence had already appeared. That could prevent the evidence needed to enable recovery from ever being obtained.
- Recognize known full-screen player wrappers with visible descendants even when the wrapper itself is not marked visible. Offscreen wrappers and protected subtrees cannot establish a player. An inline video plus action labels no longer substitutes for a verified viewer pager.
- Recover movement when a scrollable descendant belongs to the known player pager. Only metadata is inspected while ascending a bounded parent chain; captions, comments, profiles and stories terminate the search. Descendant indices are discarded because they are not verified video positions.
- Recover missing source handles using a pager event's class and window, followed by confirmed full-player content. Generic scrolling and explicit navigation sources remain rejected. No index is guessed from the missing source.
- Reconfirm a pending movement against a fresh player frame after a long quiet view outlasts the confirmation lease. Exits, overlays, expired movements and new exact source evidence invalidate pending recovery.
- Treat a repeated index as insufficient to veto a stable distinct content change after verified player movement. This fixes pagers that reuse index zero.
- Bind later positional evidence to an already confirmed content exposure without recording it twice or changing its timing. An initial content baseline no longer permanently disables useful positions.

## Verification

`tests/service_pipeline_test.py` now constructs the production service and drives its real event callback, reader, scheduled scan, tree extraction, result handling and counting engine through deterministic Android boundary doubles. Six 20-item event traces cover ordinary positions, reused positions, missing source handles, hidden wrappers, descendant sources and 20-second quiet views. A further 20-item trace covers a content baseline followed by late indices and identical captions. Expected and observed totals are 140 exposures across these traces.

Fifteen service cases also cover wrong windows, action/navigation scrolling, protected content, overlays/partial reads, stale results, optional content budgets and superseded pending recovery. The new reused-index and hidden-wrapper reproductions failed on the base release. The long-view/missing-source trace counted only one of 20 exposures before its correction. The expanded-profile fixture explicitly models a modern SDK rather than the SDK stub's zero default.

All existing Java regressions and Python checks for fences, service health, privacy, history, sync, jobs, defaults, deployment and release metadata pass. Backend checks pass. Final-source Android resource packaging, Java compilation, DEX generation and debug signature verification pass. These fixtures simulate Android conditions; they do not execute Binder IPC, Instagram itself, OEM process management or the Play-installed binary.

The pipeline test already runs in the repository's required regression step. Full new GitHub lint/CI still needs to run after upload. Local Gradle lint is unavailable because the local distribution has two corrupt jars; the SDK build is not a substitute for lint. No device was attached. Accessibility being turned off by Android remains a separate unconfirmed failure, not a resolved claim in this revision.

## Install/update

Build using the existing GitHub workflow and upload the signed AAB through Play testing to update a Play Store installation. A locally signed debug APK cannot replace the Play-signed installation. The source ZIP excludes generated APKs and signing keys. Preserve existing history by updating through the same signing channel.

## Platform reference

Android documents the explicit expanded-hierarchy flag and source/window/class metadata in [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) and [AccessibilityRecord](https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord).
