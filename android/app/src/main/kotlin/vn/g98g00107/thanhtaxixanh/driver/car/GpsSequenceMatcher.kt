package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/**
 * M5.3P: deterministic, bounded Viterbi-style geometry matching.
 * Candidate IDs MUST be stable caller-supplied road IDs (never list indices).
 * This is for offline audit only, not for driving alerts.
 */
object GpsSequenceMatcher {
    data class Candidate(val roadId: String, val distanceM: Double,
                         val headingDifferenceDeg: Double, val connectedFromPrevious: Boolean)
    data class Result(val roadIds: List<String?>, val ambiguousIndices: List<Int>,
                      val status: String = "UNVERIFIED_REVIEW_ONLY")

    fun match(steps: List<List<Candidate>>, ambiguityMargin: Double = 6.0): Result {
        require(steps.size <= 5000)
        require(ambiguityMargin.isFinite() && ambiguityMargin in 0.0..30.0)
        val chosen = ArrayList<String?>()
        val ambiguous = ArrayList<Int>()
        var prior = emptyMap<String, Double>()
        for ((index, raw) in steps.withIndex()) {
            require(raw.size <= 128)
            val candidates = raw.filter {
                it.roadId.isNotBlank() && it.distanceM.isFinite() &&
                    it.distanceM in 0.0..30.0 &&
                    it.headingDifferenceDeg.isFinite() &&
                    it.headingDifferenceDeg in 0.0..45.0
            }.distinctBy { it.roadId }
            val scores = candidates.mapNotNull { c ->
                val observation = c.distanceM + 0.12 * c.headingDifferenceDeg
                val continuity = if (prior.isEmpty()) 0.0
                    else if (c.connectedFromPrevious) prior.values.minOrNull() ?: 0.0
                    else return@mapNotNull null
                c.roadId to (observation + continuity)
            }.sortedBy { it.second }
            val best = scores.firstOrNull()
            val runnerUp = scores.getOrNull(1)
            if (best == null || (runnerUp != null &&
                    runnerUp.second - best.second < ambiguityMargin)) {
                chosen.add(null)
                ambiguous.add(index)
                prior = emptyMap()
            } else {
                chosen.add(best.first)
                prior = mapOf(best.first to best.second)
            }
        }
        return Result(chosen, ambiguous)
    }
}
