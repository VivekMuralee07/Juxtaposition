package com.juxtaposition.app

/**
 * Loads a wide search in widening steps (1 km, 2 km, ..., the requested radius), reporting each result as it arrives.
 * The nearest places show up fast, and if a wider step fails the closer results are kept instead of an error.
 */
object ProgressiveLoad {
    private val EARLY_STEPS = listOf(1000, 2000)

    fun steps(radiusM: Int): List<Int> = EARLY_STEPS.filter { it < radiusM } + radiusM

    data class Outcome(val result: NearbyResult?, val reachedRadiusM: Int?, val problems: List<String>) {
        fun complete(requestedM: Int) = reachedRadiusM == requestedM
    }

    /** `fetch(radius)` returns a result, or null plus the problems. `onStep` is called after every successful step. */
    suspend fun run(
        radiusM: Int,
        fetch: suspend (Int) -> Pair<NearbyResult?, List<String>>,
        onStep: (radiusM: Int, result: NearbyResult, isFinal: Boolean) -> Unit,
    ): Outcome {
        val steps = steps(radiusM)
        var best: NearbyResult? = null
        var reached: Int? = null
        for ((i, r) in steps.withIndex()) {
            val (res, problems) = fetch(r)
            if (res == null) return Outcome(best, reached, problems) // wider steps would fail the same way
            best = res; reached = r
            onStep(r, res, i == steps.lastIndex)
        }
        return Outcome(best, reached, emptyList())
    }
}
