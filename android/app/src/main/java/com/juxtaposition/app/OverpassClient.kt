package com.juxtaposition.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

/**
 * Asks the public OpenStreetMap (Overpass) servers for nearby places straight from the phone.
 * The phone's own connection is not rate-limited like a shared cloud server's address, and several
 * mirrors are tried with staggered starts: the first good answer wins and the rest are cancelled.
 */
object OverpassClient {
    val MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter",
    )
    val TYPES = listOf("restaurant", "fast_food", "cafe")
    private const val USER_AGENT = "NearbyEats-Android/1.4 (personal app)"
    private const val MAX_RESULTS = 80

    fun buildQuery(lat: Double, lng: Double, radiusM: Int, types: List<String> = TYPES): String {
        val around = "(around:$radiusM,$lat,$lng)"
        val clauses = types.joinToString("") { "nwr[\"amenity\"=\"$it\"][\"name\"]$around;" }
        return "[out:json][timeout:25];($clauses);out center tags ${MAX_RESULTS * 3};"
    }

    /** `post(url, query)` returns the response body or throws. Injectable for tests. */
    suspend fun nearby(
        lat: Double, lng: Double, radiusM: Int,
        mirrors: List<String> = MIRRORS, hedgeMs: Long = 3000, timeoutMs: Int = 20_000,
        post: suspend (String, String, Int) -> String = ::httpPost,
    ): NearbyResult {
        val query = buildQuery(lat, lng, radiusM)
        val body = firstSuccess(mirrors, hedgeMs) { url ->
            val text = post(url, query, timeoutMs)
            val remark = runCatching { JSONObject(text).optString("remark") }.getOrDefault("")
            val empty = runCatching { JSONObject(text).optJSONArray("elements")?.length() ?: 0 }.getOrDefault(0) == 0
            // Overpass reports overload as HTTP 200 + empty list + a remark: that is a failure, not "no places".
            if (empty && Regex("(?i)error|timed out|out of memory|too busy").containsMatchIn(remark)) {
                throw java.io.IOException(remark.removePrefix("runtime error: ").take(60))
            }
            text
        }
        return parse(body, lat, lng, radiusM)
    }

    /** Mirror i starts after i * hedgeMs. The first success wins and cancels the rest; if all fail, the error names each. */
    internal suspend fun <T> firstSuccess(mirrors: List<String>, hedgeMs: Long, attempt: suspend (String) -> T): T = coroutineScope {
        val results = Channel<Pair<String, Result<T>>>(Channel.UNLIMITED)
        val jobs = mirrors.mapIndexed { i, url ->
            launch {
                delay(i * hedgeMs)
                val r = try { Result.success(attempt(url)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
                results.send(url to r)
            }
        }
        val errors = mutableListOf<String>()
        repeat(mirrors.size) {
            val (url, result) = results.receive()
            result.onSuccess { value -> jobs.forEach { j -> j.cancel() }; return@coroutineScope value }
            result.onFailure { e -> errors += "${URL(url).host}: ${describe(e)}" }
        }
        throw ApiException(errors.joinToString("; "))
    }

    private fun describe(e: Throwable) = when (e) {
        is java.net.SocketTimeoutException -> "timeout"
        is java.net.UnknownHostException -> "no connection"
        else -> e.message ?: e.javaClass.simpleName
    }

    private suspend fun httpPost(url: String, query: String, timeoutMs: Int): String = suspendCancellableCoroutine { cont ->
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        cont.invokeOnCancellation { runCatching { conn.disconnect() } } // unblocks the reader thread
        Thread {
            try {
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) throw java.io.IOException("HTTP $code")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (cont.isActive) cont.resume(text)
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            } finally {
                runCatching { conn.disconnect() }
            }
        }.start()
    }

    fun parse(json: String, lat: Double, lng: Double, radiusM: Int): NearbyResult {
        val elements = JSONObject(json).optJSONArray("elements")
        val seen = HashSet<String>()
        val out = ArrayList<Place>()
        for (i in 0 until (elements?.length() ?: 0)) {
            val el = elements!!.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name").takeIf { it.isNotEmpty() } ?: continue
            val center = el.optJSONObject("center")
            val plat = if (el.has("lat")) el.getDouble("lat") else center?.optDouble("lat", Double.NaN) ?: Double.NaN
            val plng = if (el.has("lon")) el.getDouble("lon") else center?.optDouble("lon", Double.NaN) ?: Double.NaN
            if (plat.isNaN() || plng.isNaN()) continue
            val key = "${name.lowercase()}|%.4f|%.4f".format(plat, plng)
            if (!seen.add(key)) continue
            val km = Geo.distanceKm(lat, lng, plat, plng)
            val bearing = Geo.bearingDeg(lat, lng, plat, plng)
            fun tag(vararg k: String) = k.firstNotNullOfOrNull { tags.optString(it).takeIf { v -> v.isNotEmpty() } }
            out += Place(
                id = "osm:${el.optString("type")}/${el.optLong("id")}", name = name, type = tags.optString("amenity", "restaurant"),
                cuisine = tag("cuisine")?.replace(";", ", ")?.replace("_", " "),
                lat = plat, lng = plng, distanceKm = Math.round(km * 100) / 100.0, bearingDeg = bearing, direction = Geo.compass(bearing),
                address = listOfNotNull(tag("addr:housenumber"), tag("addr:street"), tag("addr:suburb", "addr:neighbourhood"), tag("addr:city"))
                    .joinToString(", ").takeIf { it.isNotEmpty() },
                openingHours = tag("opening_hours"), phone = tag("phone", "contact:phone"), website = tag("website", "contact:website"),
                vegetarian = when (tags.optString("diet:vegetarian")) { "only" -> "only"; "yes" -> "yes"; else -> null },
            )
        }
        return NearbyResult(radiusM, out.sortedBy { it.distanceKm }.take(MAX_RESULTS))
    }
}
