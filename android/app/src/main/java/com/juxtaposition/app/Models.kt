package com.juxtaposition.app

import org.json.JSONArray
import org.json.JSONObject

data class Restaurant(
    val id: String, val name: String, val cuisine: String, val rating: Double,
    val area: String, val distanceKm: Double, val platforms: List<String>,
)

data class MenuItem(val id: String, val name: String, val basePrice: Int, val veg: Boolean)

data class QuoteLine(val name: String, val qty: Int, val unitPrice: Int, val lineTotal: Int)

data class Quote(
    val platform: String, val name: String, val colorHex: String, val available: Boolean,
    val reason: String?, val lines: List<QuoteLine>, val itemsTotal: Int, val deliveryFee: Int,
    val platformFee: Int, val taxes: Int, val discount: Int, val couponCode: String?, val total: Int,
    val etaMinutes: Int, val distanceKm: Double, val isCheapest: Boolean, val isFastest: Boolean,
    val isBestValue: Boolean, val savingsVsWorst: Int, val androidPackage: String?, val scheme: String?,
)

data class Comparison(
    val restaurant: Restaurant, val results: List<Quote>, val bestPlatform: String?,
    val maxSavings: Int, val simulated: Boolean,
)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun parseRestaurant(o: JSONObject) = Restaurant(
    o.getString("id"), o.getString("name"), o.optString("cuisine"), o.optDouble("rating", 0.0),
    o.optString("area"), o.optDouble("distanceKm", 0.0),
    o.optJSONArray("platforms")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
)

fun parseRestaurants(json: String): List<Restaurant> =
    JSONObject(json).getJSONArray("restaurants").objects().map(::parseRestaurant)

fun parseMenu(json: String): List<MenuItem> =
    JSONObject(json).getJSONArray("menu").objects().map {
        MenuItem(it.getString("id"), it.getString("name"), it.getInt("basePrice"), it.optBoolean("veg"))
    }

fun parseComparison(json: String): Comparison {
    val root = JSONObject(json)
    val results = root.getJSONArray("results").objects().map { q ->
        val link = q.optJSONObject("deepLink")
        Quote(
            platform = q.getString("platform"),
            name = q.optString("name", q.getString("platform")),
            colorHex = q.optString("color", "#888888"),
            available = q.optBoolean("available"),
            reason = q.optString("reason").takeIf { it.isNotEmpty() },
            lines = q.optJSONArray("items")?.objects()?.map {
                QuoteLine(it.getString("name"), it.getInt("qty"), it.getInt("unitPrice"), it.getInt("lineTotal"))
            } ?: emptyList(),
            itemsTotal = q.optInt("itemsTotal"), deliveryFee = q.optInt("deliveryFee"),
            platformFee = q.optInt("platformFee"), taxes = q.optInt("taxes"), discount = q.optInt("discount"),
            couponCode = if (q.isNull("couponCode")) null else q.optString("couponCode").takeIf { it.isNotEmpty() },
            total = q.optInt("total"), etaMinutes = q.optInt("etaMinutes"), distanceKm = q.optDouble("distanceKm"),
            isCheapest = q.optBoolean("isCheapest"), isFastest = q.optBoolean("isFastest"),
            isBestValue = q.optBoolean("isBestValue"), savingsVsWorst = q.optInt("savingsVsWorst"),
            androidPackage = link?.optString("androidPackage")?.takeIf { it.isNotEmpty() },
            scheme = link?.let { if (it.isNull("scheme")) null else it.optString("scheme") }?.takeIf { it.isNotEmpty() },
        )
    }
    return Comparison(
        parseRestaurant(root.getJSONObject("restaurant")), results,
        if (root.isNull("bestPlatform")) null else root.optString("bestPlatform"),
        root.optInt("maxSavings"), root.optString("mode") == "simulated",
    )
}

fun parseLocationNote(json: String): String? =
    JSONObject(json).let { if (it.isNull("locationNote")) null else it.optString("locationNote").takeIf { n -> n.isNotEmpty() } }
