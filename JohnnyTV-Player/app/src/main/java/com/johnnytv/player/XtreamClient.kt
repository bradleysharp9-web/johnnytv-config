package com.johnnytv.player

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Category(val id: String, val name: String)

data class StreamItem(
    val streamId: String,
    val num: String,
    val name: String,
    val containerExtension: String,
    val isLive: Boolean
)

class XtreamException(message: String) : Exception(message)

/**
 * Minimal client for the Xtream Codes player API (player_api.php).
 * All calls are blocking - run them off the main thread.
 */
class XtreamClient(
    rawServer: String,
    private val username: String,
    private val password: String
) {

    val server: String = normalizeServer(rawServer)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ---------- public API ----------

    /** Validates the credentials. Returns a short status line, or throws XtreamException. */
    fun login(): String {
        val body = get("$server/player_api.php?${creds()}")
        val root = try {
            JSONObject(body.trim())
        } catch (e: Exception) {
            throw XtreamException("That address did not answer like a portal. Check the server and port.")
        }
        val info = root.optJSONObject("user_info")
            ?: throw XtreamException("The server did not return account info. Check the address.")

        val auth = info.opt("auth")?.toString() ?: "0"
        val status = info.optString("status", "")
        if (auth != "1") {
            throw XtreamException("Wrong username or password.")
        }
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException("Account is not active (status: $status).")
        }

        val expiry = info.optString("exp_date", "")
        return if (expiry.isNotBlank() && expiry != "null") {
            "Active until ${formatExpiry(expiry)}"
        } else {
            "Active"
        }
    }

    fun liveCategories(): List<Category> = categories("get_live_categories")

    fun vodCategories(): List<Category> = categories("get_vod_categories")

    fun liveStreams(categoryId: String): List<StreamItem> =
        streams("get_live_streams", categoryId, isLive = true)

    fun vodStreams(categoryId: String): List<StreamItem> =
        streams("get_vod_streams", categoryId, isLive = false)

    /** Candidate playback URLs, tried in order by the player. */
    fun playbackUrls(item: StreamItem): List<String> = if (item.isLive) {
        Config.LIVE_CONTAINERS.map { ext ->
            "$server/live/$username/$password/${item.streamId}.$ext"
        }
    } else {
        listOf("$server/movie/$username/$password/${item.streamId}.${item.containerExtension}")
    }

    // ---------- internals ----------

    private fun creds(): String = "username=${enc(username)}&password=${enc(password)}"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Config.USER_AGENT)
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw XtreamException("Server returned HTTP ${response.code}.")
                }
                return response.body?.string()
                    ?: throw XtreamException("The server sent an empty response.")
            }
        } catch (e: XtreamException) {
            throw e
        } catch (e: Exception) {
            throw XtreamException("Could not reach the server. ${e.message ?: "Check your connection."}")
        }
    }

    private fun categories(action: String): List<Category> {
        val array = asArray(get("$server/player_api.php?${creds()}&action=$action"))
        val out = ArrayList<Category>(array.length() + 1)
        out.add(Category("", "All"))
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.opt("category_id")?.toString() ?: continue
            val name = obj.optString("category_name", "").ifBlank { "Category $id" }
            out.add(Category(id, name))
        }
        return out
    }

    private fun streams(action: String, categoryId: String, isLive: Boolean): List<StreamItem> {
        val suffix = if (categoryId.isBlank()) "" else "&category_id=${enc(categoryId)}"
        val array = asArray(get("$server/player_api.php?${creds()}&action=$action$suffix"))
        val out = ArrayList<StreamItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.opt("stream_id")?.toString() ?: continue
            val name = obj.optString("name", "").ifBlank { "Untitled" }
            val num = obj.opt("num")?.toString() ?: (i + 1).toString()
            val ext = obj.optString("container_extension", "").ifBlank { "mp4" }
            out.add(StreamItem(id, num, name, ext, isLive))
        }
        return out
    }

    private fun asArray(body: String): JSONArray {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("[") -> try {
                JSONArray(trimmed)
            } catch (e: Exception) {
                throw XtreamException("The server sent a list this app could not read.")
            }
            trimmed.startsWith("{") -> JSONArray()   // panels send {} for an empty section
            else -> throw XtreamException("Unexpected response from the server.")
        }
    }

    private fun formatExpiry(raw: String): String = try {
        val seconds = raw.trim().toLong()
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000L))
    } catch (e: Exception) {
        raw
    }

    companion object {
        /** Accepts "host:port", "http://host:port/", ".../player_api.php" etc. */
        fun normalizeServer(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return s

            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                s = "http://$s"
            }
            val query = s.indexOf('?')
            if (query > 0) s = s.substring(0, query)
            s = s.trimEnd('/')

            val tails = listOf("/player_api.php", "/panel_api.php", "/get.php", "/index.php", "/c")
            val lower = s.lowercase(Locale.US)
            for (tail in tails) {
                if (lower.endsWith(tail)) {
                    s = s.substring(0, s.length - tail.length)
                    break
                }
            }
            return s.trimEnd('/')
        }
    }
}
