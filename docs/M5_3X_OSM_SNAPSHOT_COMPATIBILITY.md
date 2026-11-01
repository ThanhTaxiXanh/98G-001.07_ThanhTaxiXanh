# M5.3X — Fail-closed OSM snapshot provenance

The GPX offline review now compares source labels from all topology-linked road segments with the imported restriction snapshot and returns a compatibility reason. Empty snapshots, mixed sources and mismatched source labels are rejected.

**Important:** identical human-readable source labels do not prove that the road topology and restriction relations originate from the same OSM extract. Until both datasets carry an independently validated, content-addressed extract identity, `compatibleForReview` is deliberately always false. The resolver still reports how many relations are structurally mappable, but **none of those prohibitions are applied to GPX transitions**. This fixes the earlier M5.3W risk of applying unrelated OSM snapshots.

Reasons: `NO_RESTRICTION_SNAPSHOT`, `MISSING_OR_MIXED_PROVENANCE`, `SOURCE_LABEL_MISMATCH`, `MATCHING_LABEL_NOT_VERIFIED_EXTRACT`. Status remains `UNVERIFIED_REVIEW_ONLY`; no driver alerts are enabled.

Next: define a trusted extract manifest and bind both road and restriction imports to its immutable identifier before permitting even review-only restriction application.
