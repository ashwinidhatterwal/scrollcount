# Phase 0 benchmark and decision gate

Status: NOT EXECUTED ON A PHONE. Do not mark Phase 0 validated from compilation or synthetic tests.

For each run record phone model, Android version, platform app version (also included in report), app language, test date, and exact manual transitions. One supported platform per run; export before reset.

| Run | Actions | Expected transitions | Actual | Result |
|---|---|---:|---|---|
| IG-1 | First Reel, then 20 forward swipes with 2-second pauses | 20 | Pending | Pending |
| YT-1 | First Short, then 20 forward swipes with 2-second pauses | 20 | Pending | Pending |
| IG-2 / YT-2 | Like/unlike; pause/resume; comments open/close; caption expand/collapse, no video changes | 0 | Pending | Pending |
| IG-3 / YT-3 | Profile/return; another app/return; rotate; lock/unlock, no deliberate new item | 0 | Pending | Pending |
| IG-4 / YT-4 | 5 forward then 2 backward swipes, settled between moves | 7 | Pending | Pending |
| IG-5 / YT-5 | 20 fast swipes | 20 | Pending | Characterization only |

Use the accepted-transition timestamps and your action notes to match detections to real transitions. Totals alone cannot distinguish false positives from misses.

Metrics: precision = TP/(TP+FP); recall = TP/(TP+FN); absolute count error = abs(detected - actual). A zero denominator is N/A, not 100%. A count of 20 can conceal both false positives and misses.

Proposed preliminary gate, to operationalize the plan's “close to actual swipes”: on both platforms, three repeated settled 20-swipe runs produce 19–21 detections, event-matched precision and recall are each at least 95%, and each no-transition overlay test adds zero. This is a small feasibility sample, not production reliability proof. The later Phase 2 multi-device target still applies.

If IDs are unavailable, do not relax to counting every scroll. Inspect redacted structures and event scroll indices first, then revise the relevant adapter. If no safe content/position signal exists, record that limitation and revisit the architecture before Phase 1.

## Return these results

- Instagram: manually made ___ swipes; detected ___; sanitized report attached.
- YouTube: manually made ___ swipes; detected ___; sanitized report attached.
- Which non-swipe actions increased the count: ___ (with approximate action times).
- Missing signals, truncated tree or service problems: ___.
