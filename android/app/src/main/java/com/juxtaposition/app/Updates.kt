package com.juxtaposition.app

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class UpdateInfo(val version: String, val downloadUrl: String, val pageUrl: String)

object Versions {
    /** "v1.8.0" or "1.8" -> [1, 8, 0]; null when it isn't a plain dotted number. */
    fun parse(s: String): List<Int>? {
        val parts = s.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/** Looks for a newer release on GitHub. The app is installed by hand, so this is how it finds out. */
object UpdateChecker {
    const val LATEST_RELEASE_API = "https://api.github.com/repos/VivekMuralee07/Juxtaposition/releases/latest"
    const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L

    fun shouldCheck(lastCheckMs: Long, nowMs: Long) = nowMs - lastCheckMs >= CHECK_EVERY_MS

    /** The update to offer, or null when the release isn't newer, is a draft/pre-release, or can't be read. */
    fun parse(json: String, currentVersion: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.getString("tag_name")
        if (!Versions.isNewer(tag, currentVersion)) return null
        val page = o.optString("html_url")
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) { apk = a.optString("browser_download_url"); break }
        }
        UpdateInfo(tag.removePrefix("v"), apk?.takeIf { it.isNotEmpty() } ?: page, page)
    }.getOrNull()

    /** Blocking; call off the main thread. */
    fun fetch(): String {
        val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NearbyEats-Android") // GitHub's API requires a User-Agent
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
