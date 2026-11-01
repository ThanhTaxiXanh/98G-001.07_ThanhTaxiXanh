# M5.3R — Directed heading and elapsed-time GPS sanity checks

This phase adds `GpxTransitionSanity` and integrates it into the existing read-only `GpxDirectedRoadReview`.

- Compute a road bearing from its segment geometry, reversing 180 degrees for reverse-directed graph edges. Compare inferred GPS movement heading with directed road bearing using a 45-degree tolerance.
- Between consecutive accepted points reject non-increasing timestamps, gaps greater than 30 seconds, and observed point-to-point displacement over 55 m/s. This is a conservative gross-error check, **not a road speed limit**.
- Phone GPX report adds rejected-heading and implausible-time counts. Tests cover reverse heading, wraparound, implausible jumps, normal travel and timestamp gaps.

**Remaining limitations:** GPX-derived heading is not compass-measured; the 25 m accuracy field is a placeholder and must not be treated as measured accuracy. This does not evaluate turn restrictions or grade separation, multi-edge transition paths, time along road geometry, or a full Viterbi/HMM model. All outputs remain `UNVERIFIED_REVIEW_ONLY`, and no Android Auto speed alerts are enabled.
