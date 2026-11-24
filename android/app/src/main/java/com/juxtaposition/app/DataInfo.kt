package com.juxtaposition.app

/** What the "data source" line under the list says: where the list came from and how fresh the position is. */
data class DataInfo(val via: String?, val tookMs: Long?, val atMs: Long, val saved: Boolean)

object DataLine {
    fun describe(info: DataInfo?, fix: String?, nowMs: Long): String? {
        val parts = mutableListOf<String>()
        when {
            info == null -> {}
            info.saved -> parts += "Saved copy from ${agoText(nowMs - info.atMs)}"
            else -> {
                parts += "OpenStreetMap via " + "${info.via ?: "?"} (phone)"
                info.tookMs?.let { parts += "%.1f s".format(it / 1000.0) }
                parts += "updated ${agoText(nowMs - info.atMs)}"
            }
        }
        fix?.let { parts += it }
        return parts.joinToString(" • ").ifEmpty { null }
    }

    /** A short version for the footer: "Updated 3 min ago • GPS ±12 m". Tap the footer for [describe]. */
    fun short(info: DataInfo?, fix: String?, nowMs: Long): String? {
        val parts = mutableListOf<String>()
        info?.let { parts += if (it.saved) "Saved copy from ${agoText(nowMs - it.atMs)}" else "Updated ${agoText(nowMs - it.atMs)}" }
        fix?.let { parts += it }
        return parts.joinToString(" • ").ifEmpty { null }
    }

    /** "GPS ±12 m", "Network ±300 m" or null when there is no fix yet. */
    fun fix(provider: String?, accuracyM: Float?): String? {
        val name = when (provider) { "gps" -> "GPS"; "network" -> "Network"; "fused" -> "Fused"; null -> return null; else -> provider.replaceFirstChar { it.uppercase() } }
        return if (accuracyM != null) "$name ±${Math.round(accuracyM)} m" else name
    }
}
