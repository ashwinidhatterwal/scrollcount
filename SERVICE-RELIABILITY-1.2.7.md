# ScrollCount 1.2.7 — accessibility-service shutdown investigation

## Corrected diagnosis

The reported failure occurs on Android 12 and newer: counting works initially, then the badge disappears and Android's accessibility switch is found off. This is a service availability problem, distinct from the separate Lite/Facebook/Snapchat layout compatibility work in 1.2.6. The exact cause remains unconfirmed without an affected-device report.

The app has no disableSelf(), secure-setting write, accessibility auto-toggle, or process-kill call. The system manages accessibility binding. Java crashes, ANRs and OEM lifecycle changes are possibilities, not proven causes for this incident. A process exit is not by itself proof that Android changed the accessibility switch.

## Changes

- Protected scheduled scan/footer callbacks, event handling, preference changes, screen broadcasts, badge-history results, profile setup and individual shutdown cleanup steps. Recoverable runtime failures pause the current observation and leave the next event able to run. VM errors are not swallowed.
- Isolated history listeners and optional Friends callbacks/listeners so a sharing failure cannot escape these boundaries and terminate counting's shared process. Optional reminder failures are also isolated.
- Added bounded local service-health evidence: twelve lifecycle/fault entries, fault recording limited to once per thirty seconds. Only timestamps, code locations and exception types are retained; exception messages and viewed content are excluded. Writes are best effort and may be lost during abrupt process death.
- Diagnostics shows the exact Android accessibility component setting separately from the in-process connection flag, battery-exemption status and up to four Android-reported process exit reasons on API 30+. Process-exit reads are cached for thirty seconds while diagnostics is open. No screen polling, wake lock, keepalive loop, extra service or new permission was added.
- Added a Clear service health log button and a Background settings shortcut. Privacy text documents the local evidence. The policy files are supplied for deployment; no hosted backend or GitHub repository was changed.

## How to investigate on the affected phone

1. Build/install with the existing signing identity using GitHub Actions. Keep the currently installed package and data. The locally generated debug signature cannot update an existing Play installation.
2. Accept the expanded supported-app disclosure once if upgrading from 1.2.5, then enable Android Accessibility. Set ScrollCount's battery mode to Unrestricted / Allow background activity where available. If the manufacturer offers Auto-start, allow it. Exact settings depend on the phone model.
3. Use the app normally. If counting stops again, first reopen ScrollCount and export Settings → Local diagnostics → Open diagnostics → Share sanitized report. The service-health evidence remains available without requiring label diagnostics to have been enabled beforehand.
4. Include the phone brand/model and Android version. Check whether the report says accessibility off or enabled-but-disconnected, and whether a recent exit is labeled Java crash, ANR, low memory, user stop or another reason. Android exit labels are evidence and may be ambiguous; they do not identify every OEM action.

An app cannot silently grant itself Accessibility permission after the user/system turns it off. A manual re-enable is required. If the cause is OEM disabling, the callback guards alone cannot prevent that. If the cause is an ANR in synchronous accessibility IPC, exception guards cannot cure that either; the new exit evidence is needed to direct the next architecture change.

## Validation

All portable counting, privacy, history, sharing, jobs, deployment and backend tests pass. Fault-injection tests cover next-event recovery, independent listener continuation, failed recovery and propagation of fatal VM errors. The health-storage test executes the real health class against Android metadata doubles and checks bounded retention, omission of exception messages, throttling, exact-component matching, exit metadata/cache, clearing and API guards.

Android resources, Java and DEX compile with SDK 36; a debug APK was generated and signature verification passed. Local Gradle lint/AAB generation remains blocked by the damaged local Gradle distribution described in COMPATIBILITY-1.2.6.md. No physical-device endurance test or confirmed reproduction was available. This is hardening plus diagnosis, not a guarantee the reported automatic toggle-off is resolved.

## Follow-up in 1.2.8 (build 26)

Fresh installations save Live badge = on at first launch. Existing installations and explicitly saved off preferences remain unchanged. The badge still requires Accessibility setup and a confirmed supported feed; enabling the preference does not grant Android access. Local/public privacy text was updated. Release metadata/privacy checks and the Android SDK compilation/signature verification passed.
