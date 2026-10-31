# M5.3P — Stable-ID GPS candidate scoring prototype

This phase adds `GpsSequenceMatcher`, a pure Kotlin, bounded, fail-closed review helper. It consumes **caller-supplied stable road IDs** and candidate geometry/heading costs; no road ID is inferred from SQLite row order. It rejects ambiguous parallel candidates, out-of-range observations and transitions explicitly marked disconnected. Outputs always remain `UNVERIFIED_REVIEW_ONLY`.

**Important:** This is a **greedy sequence scorer, not a full Viterbi/HMM implementation**. It does not yet query SQLite IDs, compute road-to-road transition connectivity itself, use speed/time physics, or emit driving alerts. The `connectedFromPrevious` flag must be computed by a trusted topology adapter before this helper can be used on real traces. It is not yet wired into the phone UI.

M5.3O had Flutter formatting failures due to invalid escaped quotes in the GPX coverage strings. Commit `a924d81b` removes the escapes; CI must confirm.
