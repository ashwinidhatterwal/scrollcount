# YouTube recovery — 1.0.1-rc2

This update targets missed slow and quick Shorts on YouTube 21.40.161. Instagram's counting path, UI layout, permissions and database schema are unchanged.

- Read a content description when scoped title/channel nodes expose no text. Prefer descendant labels over parent summaries. Normalize and deduplicate labels to avoid counting wrapper changes. Only the existing title/channel scope is read; action rail, subscribe sibling, audio row and progress controls remain excluded.
- Require both exact Shorts recycler/page markers and full-player geometry. Unavailable Like/Share accessibility labels no longer prevent title extraction. Overlays, editable/password nodes, unrelated packages and small player windows remain blocked. Content identity and actual Shorts movement are still mandatory for a transition.
- Refresh player confirmation independently of loading labels. Retry briefly at 70 ms, then 120 ms, with a maximum of 36 reads and a four-second movement expiry. Stop after acceptance. Null-root reads can retry within the same bounded window. There is no ongoing background polling.
- One stable content change consumes the movement lease. Missing labels or generic feed scroll events never increment the counter.

## Retest

Install over the existing app. Record the starting Seen and Quick skip totals. Watch 20 different Shorts slowly (over 2 seconds each), then 20 different Shorts with quick skips (under 2 seconds). Leave Shorts after the final item to finalize it. Verify the category deltas and send the report, including any comments/ads/open-menu interruptions. The first visible item is an exposure but not a transition: two separate 20-item sessions normally mean 38 transitions; one continuous 40-item session means 39. Do not equate diagnostic transitions with Seen alone.

The fix is build/regression verified; exact phone accuracy is not yet verified. Identical accessible title/channel labels on distinct videos cannot be distinguished reliably without a different accessible identity. The code does not fabricate missing transitions from raw scroll events.
