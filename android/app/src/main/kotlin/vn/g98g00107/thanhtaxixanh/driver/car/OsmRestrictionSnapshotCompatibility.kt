package vn.g98g00107.thanhtaxixanh.driver.car

/**
 * M5.3X: conservative provenance compatibility gate.
 * A matching human-readable source label is NOT proof of identical OSM extracts.
 * This check can reject mismatches, but cannot establish a verified match.
 */
object OsmRestrictionSnapshotCompatibility {
    data class Result(val compatibleForReview: Boolean, val reason: String)

    fun evaluate(topologySources: Set<String>, restrictionSources: Set<String>,
                 restrictionCount: Int): Result {
        if (restrictionCount == 0) return Result(false, "NO_RESTRICTION_SNAPSHOT")
        if (topologySources.size != 1 || restrictionSources.size != 1)
            return Result(false, "MISSING_OR_MIXED_PROVENANCE")
        if (topologySources.single() != restrictionSources.single())
            return Result(false, "SOURCE_LABEL_MISMATCH")
        // Labels are not immutable extract identifiers: refuse activation until
        // both snapshots carry the same validated content-addressed extract ID.
        return Result(false, "MATCHING_LABEL_NOT_VERIFIED_EXTRACT")
    }
}
