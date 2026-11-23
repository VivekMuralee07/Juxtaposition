package com.juxtaposition.app

/** Walking-time estimate shared by the list, the details card and the radar rings. */
object Walking {
    const val SPEED_KMH = 5.0
    const val DETOUR = 1.3 // streets are longer than the straight line

    fun minutesExact(km: Double) = km * DETOUR / SPEED_KMH * 60
    fun minutes(km: Double) = Math.max(1, Math.round(minutesExact(km)).toInt())
    fun kmForMinutes(min: Double) = min / 60 * SPEED_KMH / DETOUR
}

data class CuisineCategory(val label: String, val emoji: String)
data class CuisineChip(val label: String, val emoji: String, val count: Int)

/** A food-specific emoji and category for a place, from its cuisine (then its name). Only long-supported emoji (Unicode <= 10). */
object CuisineIcon {
    private class Rule(val label: String, val emoji: String, val words: List<String>)
    // Order matters: specific dishes before generic cuisines ("kebab" before "indian").
    private val RULES: List<Rule> = listOf(
        Rule("Biryani", "🍛", listOf("biryani", "biriyani")),
        Rule("South Indian", "🥞", listOf("dosa", "idli", "vada", "south indian", "udupi", "tiffin")),
        Rule("Pizza", "🍕", listOf("pizza", "pizzeria")),
        Rule("Burgers", "🍔", listOf("burger", "burgers")),
        Rule("Momos", "🥟", listOf("momo", "momos", "dumpling", "dumplings", "dim sum")),
        Rule("Chinese & Asian", "🍜", listOf("noodle", "noodles", "ramen", "chinese", "thai", "asian", "pan asian")),
        Rule("Japanese", "🍣", listOf("sushi", "japanese")),
        Rule("Chicken", "🍗", listOf("fried chicken", "chicken", "wings")),
        Rule("Kebabs & BBQ", "🍢", listOf("kebab", "kebabs", "bbq", "barbecue", "grill", "tandoori", "shawarma", "tikka")),
        Rule("Seafood", "🐟", listOf("seafood", "fish")),
        Rule("Sandwiches", "🥪", listOf("sandwich", "sandwiches", "sub", "subs")),
        Rule("Italian", "🍝", listOf("pasta", "italian")),
        Rule("Mexican", "🌮", listOf("mexican", "taco", "tacos", "burrito")),
        Rule("Steak", "🥩", listOf("steak", "steak house", "steakhouse")),
        Rule("Healthy", "🥗", listOf("salad", "healthy", "vegan")),
        Rule("Breakfast", "🍳", listOf("breakfast", "brunch", "egg", "eggs")),
        Rule("Ice cream", "🍦", listOf("ice cream", "icecream", "gelato", "frozen yogurt")),
        Rule("Desserts & bakery", "🍰", listOf("dessert", "desserts", "cake", "cakes", "bakery", "pastry", "sweets", "sweet", "donut", "waffle", "waffles")),
        Rule("Tea", "🍵", listOf("tea", "chai")),
        Rule("Coffee", "☕", listOf("coffee", "coffee shop", "cafe", "cafes")),
        Rule("Juices & shakes", "🥤", listOf("juice", "smoothie", "milkshake", "shake", "shakes")),
        Rule("Indian", "🍛", listOf("indian", "north indian", "curry", "mughlai", "punjabi", "hyderabadi", "andhra", "rice", "thali")),
    )

    private fun normalise(s: String?) = " " + (s ?: "").lowercase().replace(Regex("[^a-z ]+"), " ").replace(Regex(" +"), " ") + " "

    private fun match(text: String): Rule? = RULES.firstOrNull { r -> r.words.any { text.contains(" $it ") } }

    /** The category from the cuisine tag, else from the name; null when neither says anything about the food. */
    fun category(cuisine: String?, name: String? = null): CuisineCategory? =
        (match(normalise(cuisine)) ?: match(normalise(name)))?.let { CuisineCategory(it.label, it.emoji) }

    fun emoji(type: String, cuisine: String?, name: String? = null): String =
        category(cuisine, name)?.emoji ?: when (type) { "fast_food" -> "🍔"; "cafe" -> "☕"; else -> "🍽️" }
}

fun Place.category() = CuisineIcon.category(cuisine, name)

object CuisineChips {
    /** The categories present in [places], most common first. Empty when there is nothing to choose between (fewer than two). */
    fun from(places: List<Place>, max: Int = 8): List<CuisineChip> {
        val chips = places.mapNotNull { it.category() }.groupBy { it }
            .map { (c, list) -> CuisineChip(c.label, c.emoji, list.size) }
            .sortedWith(compareByDescending<CuisineChip> { it.count }.thenBy { it.label })
        return if (chips.size < 2) emptyList() else chips.take(max)
    }
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
