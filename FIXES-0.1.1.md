# 0.1.1 — preserve fast Instagram exposures

The supplied 0.1.0 report contains exact player positions 47 through 78, including rapid runs roughly 0.4–0.9 seconds apart, but only 12 accepted changes. The original 650-ms fingerprint debounce was also being applied to exact settled player positions. It discarded short exposures, so the app could not reliably classify quick skips or attribute their dwell time.

Change: after a recent confirmed Instagram feed scan, an exact clips-player-pager event with matching non-negative from/to indices commits the reported position directly. Duplicate positions are ignored. Each observed changed position closes the prior exposure at the event time and begins the next. Unknown/generic scrolls are still excluded. A feed/context guard expires after five seconds and is invalidated on screen/app exits or unavailable trees. Tree scanning stays coalesced; there is no full traversal for every indexed event. YouTube remains on its previous fingerprint + player-scroll rule.

Seen still requires two seconds. A fast skip is recorded as SKIPPED, not SEEN. Diagnostics counts changes, including short exposures. Returning to a position counts as another exposure, as before.

Validation: a synthetic continuous sequence using the report's 47..78 indices and fast intervals produces exactly 31 changes/32 exposures, ignores duplicates, retains all ten short-duration intervals, and handles a backward position and unknown index correctly. This is a regression scenario, not a claim that every original action or timestamp was replayed. Existing timing and SQLite checks pass; the Android build/signature verify.

The report does not include the home-screen Seen/Quick skip totals or all intermediate app/overlay states. Those totals and exact overall accuracy cannot be inferred from this diagnostic count alone.

Update over 0.1.0. The SQLite schema and debug signer are unchanged, so saved history remains. Missed old items cannot be safely reconstructed from this redacted report and are not added to history.

Retest: view the initial Reel for three seconds, make ten rapid forward swipes with roughly 0.3–0.5 seconds between them, hold the final Reel for three seconds, then return. For a confirmed continuous run, the new exposures should contain two seen items and nine quick skips, with ten diagnostic changes. Send before/after home totals and the diagnostic summary if they differ. Also confirm likes/comments without a video change do not add seen exposures.
