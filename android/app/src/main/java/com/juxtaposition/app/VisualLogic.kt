package com.juxtaposition.app

/** Walking-time estimate shared by the list, the details card and the radar rings. */
object Walking {
    const val SPEED_KMH = 5.0
    const val DETOUR = 1.3 // streets are longer than the straight line

    fun minutesExact(km: Double) = km * DETOUR / SPEED_KMH * 60
    fun minutes(km: Double) = Math.max(1, Math.round(minutesExact(km)).toInt())
    fun kmForMinutes(min: Double) = min / 60 * SPEED_KMH / DETOUR
}

/** A food-specific emoji for a place, from its cuisine (then its name), falling back to its type. Only long-supported emoji (Unicode <= 10). */
object CuisineIcon {
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf("biryani", "biriyani") to "🍛",
        listOf("dosa", "idli", "vada", "south indian", "udupi", "tiffin") to "🥞",
        listOf("pizza", "pizzeria") to "🍕",
        listOf("burger", "burgers") to "🍔",
        listOf("momo", "momos", "dumpling", "dumplings", "dim sum") to "🥟",
        listOf("noodle", "noodles", "ramen", "chinese", "thai", "asian", "pan asian") to "🍜",
        listOf("sushi", "japanese") to "🍣",
        listOf("fried chicken", "chicken", "wings") to "🍗",
        listOf("kebab", "kebabs", "bbq", "barbecue", "grill", "tandoori", "shawarma", "tikka") to "🍢",
        listOf("seafood", "fish") to "🐟",
        listOf("sandwich", "sandwiches", "sub", "subs") to "🥪",
        listOf("pasta", "italian") to "🍝",
        listOf("mexican", "taco", "tacos", "burrito") to "🌮",
        listOf("steak", "steak house", "steakhouse") to "🥩",
        listOf("salad", "healthy", "vegan") to "🥗",
        listOf("breakfast", "brunch", "egg", "eggs") to "🍳",
        listOf("ice cream", "icecream", "gelato", "frozen yogurt") to "🍦",
        listOf("dessert", "desserts", "cake", "cakes", "bakery", "pastry", "sweets", "sweet", "donut", "waffle", "waffles") to "🍰",
        listOf("tea", "chai") to "🍵",
        listOf("coffee", "coffee shop", "cafe", "cafes") to "☕",
        listOf("juice", "smoothie", "milkshake", "shake", "shakes") to "🥤",
        listOf("indian", "north indian", "curry", "mughlai", "punjabi", "hyderabadi", "andhra", "rice", "thali") to "🍛",
    )

    private fun normalise(s: String?) = " " + (s ?: "").lowercase().replace(Regex("[^a-z ]+"), " ").replace(Regex(" +"), " ") + " "

    private fun match(text: String): String? = RULES.firstOrNull { (words, _) -> words.any { text.contains(" $it ") } }?.second

    fun emoji(type: String, cuisine: String?, name: String? = null): String =
        match(normalise(cuisine)) ?: match(normalise(name)) ?: when (type) { "fast_food" -> "🍔"; "cafe" -> "☕"; else -> "🍽️" }
}

fun Place.emoji() = CuisineIcon.emoji(type, cuisine, name)

/** Distance rings for the radar, labelled in walking minutes (people think in minutes, not metres). */
data class RingMark(val distanceKm: Double, val label: String, val isBoundary: Boolean)

object RingScale {
    private val STEPS = listOf(1, 2, 3, 5, 10, 15, 20, 30)

    fun distanceLabel(m: Double): String =
        if (m < 1000) "${(Math.round(m / 10.0) * 10).toInt()} m" else (m / 1000).let { if (Math.abs(it - Math.round(it)) < 0.05) "${Math.round(it)} km" else "%.1f km".format(it) }

    fun rings(visibleRadiusM: Double): List<RingMark> {
        val maxMin = Walking.minutesExact(visibleRadiusM / 1000)
        val step = STEPS.firstOrNull { maxMin / it <= 4.0 } ?: STEPS.last() // at most about four rings
        val out = mutableListOf<RingMark>()
        var k = 1
        while (k * step < maxMin * 0.9) { // keep clear of the outer edge so labels don't collide
            out += RingMark(Walking.kmForMinutes((k * step).toDouble()), "${k * step} min", false)
            k++
        }
        out += RingMark(visibleRadiusM / 1000, "${distanceLabel(visibleRadiusM)} · ${Walking.minutes(visibleRadiusM / 1000)} min", true)
        return out
    }
}

/** Pinch-zoom rules for the radar. */
object RadarZoom {
    const val MIN = 1f
    const val MAX = 6f
    const val DOUBLE_TAP = 2.5f

    fun apply(current: Float, gestureZoom: Float) = (current * gestureZoom).coerceIn(MIN, MAX)
    fun toggle(current: Float) = if (current > 1.05f) MIN else DOUBLE_TAP
    fun visibleRadiusM(radiusM: Int, zoom: Float) = radiusM / zoom.toDouble()
}
