package com.juxtaposition.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the new APK inside the app and hands it to Android's installer (which always asks you to confirm). */
object ApkInstaller {
    /** Only GitHub release files over HTTPS are downloaded in-app; anything else falls back to the browser. */
    fun isTrustedUrl(url: String): Boolean {
        val u = runCatching { URL(url) }.getOrNull() ?: return false
        return u.protocol == "https" && u.host == "github.com" && u.path.endsWith(".apk", ignoreCase = true)
    }

    fun file(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "NearbyEats.apk")

    /** Blocking; call off the main thread. [onProgress] gets 0..100 (or -1 when the size is unknown). */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File {
        val target = file(ctx)
        val part = File(target.parentFile, "NearbyEats.apk.part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "NearbyEats-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L; var last = -2
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != last) { last = pct; onProgress(pct) }
                    }
                }
            }
            if (total > 0 && done != total) throw java.io.IOException("Download was cut off")
            target.delete()
            if (!part.renameTo(target)) throw java.io.IOException("Couldn't save the file")
            return target
        } finally { conn.disconnect(); part.delete() }
    }

    /** True when the file is this app (same package) with a higher version code, so a wrong file is never offered to the installer. */
    @Suppress("DEPRECATION")
    fun looksRight(ctx: Context, apk: File): Boolean {
        val info = ctx.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val mine = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val myCode = if (Build.VERSION.SDK_INT >= 28) mine.longVersionCode else mine.versionCode.toLong()
        return info.packageName == ctx.packageName && code > myCode
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the "allow installs from this app" screen. */
    fun askPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
