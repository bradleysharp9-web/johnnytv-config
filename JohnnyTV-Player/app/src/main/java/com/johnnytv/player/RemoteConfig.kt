package com.johnnytv.player

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The contents of your hosted config.json. Every field is optional except "server".
 *
 * "server" may be a plain address OR base64-encoded, so the portal address is not
 * sitting in a public file as readable text.
 *
 * {
 *   "server": "aHR0cDovL3lvdXItcG9ydGFsLmNvbTo4MDgw",
 *   "notice": "",
 *   "latest_version_code": 1,
 *   "download_url": "https://example.com/JohnnyTV.apk",
 *   "force_update": false
 * }
 */
data class RemoteConfig(
    val server: String,
    val notice: String,
    val latestVersionCode: Long,
    val downloadUrl: String,
    val forceUpdate: Boolean
)

object RemoteConfigLoader {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Blocking. Returns null on any failure - the caller falls back to the cached server. */
    fun fetch(url: String): RemoteConfig? {
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Config.USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.trim()
                if (body.isNullOrEmpty()) return null
                val json = JSONObject(body)
                RemoteConfig(
                    server = decodeServer(json.optString("server", "").trim()),
                    notice = json.optString("notice", "").trim(),
                    latestVersionCode = json.optLong("latest_version_code", 0L),
                    downloadUrl = json.optString("download_url", "").trim(),
                    forceUpdate = json.optBoolean("force_update", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Accepts either a plain address or a base64-encoded one. Anything that already
     * looks like a URL is passed straight through, so both forms keep working.
     */
    private fun decodeServer(raw: String): String {
        if (raw.isBlank()) return ""
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
        return try {
            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.startsWith("http://", true) || decoded.startsWith("https://", true)) {
                decoded
            } else {
                raw
            }
        } catch (e: Exception) {
            raw
        }
    }
}
