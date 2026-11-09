package pk.lagharitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class XtreamCredentials(val server: String, val username: String, val password: String)
data class XtreamCategory(val id: String, val name: String)
data class XtreamStream(
    val id: String,
    val name: String,
    val categoryId: String?,
    val logo: String?,
    val extension: String?
)

class XtreamApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun base(credentials: XtreamCredentials): String {
        val value = credentials.server.trim().trimEnd('/')
        require(value.startsWith("https://", true)) { "Use an HTTPS server URL." }
        return value
    }
    private fun apiUrl(c: XtreamCredentials, action: String? = null): String {
        val query = "username=${segment(c.username)}&password=${segment(c.password)}"
        return "${base(c)}/player_api.php?$query" +
            (action?.let { "&action=${segment(it)}" } ?: "")
    }
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Server returned HTTP ${response.code}" }
            response.body?.string() ?: error("Empty server response")
        }
    }
    suspend fun authenticate(credentials: XtreamCredentials): Boolean {
        require(credentials.username.isNotBlank() && credentials.password.isNotBlank()) {
            "Enter username and password."
        }
        val json = JSONObject(get(apiUrl(credentials)))
        return json.optJSONObject("user_info")?.optString("auth") == "1"
    }
    suspend fun categories(c: XtreamCredentials, kind: String): List<XtreamCategory> {
        val action = when (kind) {
            "live" -> "get_live_categories"
            "movie" -> "get_vod_categories"
            else -> "get_series_categories"
        }
        return parseArray(get(apiUrl(c, action))).mapNotNull { item ->
            val id = item.optString("category_id")
            if (id.isBlank() || id == "null") null
            else XtreamCategory(id, item.optString("category_name", "Other"))
        }
    }
    suspend fun streams(c: XtreamCredentials, kind: String): List<XtreamStream> {
        val action = when (kind) {
            "live" -> "get_live_streams"
            "movie" -> "get_vod_streams"
            else -> "get_series"
        }
        return parseArray(get(apiUrl(c, action))).mapNotNull { item ->
            val id = item.optString(if (kind == "series") "series_id" else "stream_id")
            if (id.isBlank() || id == "null") null
            else XtreamStream(
                id = id,
                name = item.optString("name", "Untitled"),
                categoryId = item.optString("category_id").takeIf { it.isNotBlank() && it != "null" },
                logo = item.optString("stream_icon").takeIf { it.startsWith("http") },
                extension = item.optString("container_extension").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
    suspend fun seriesEpisodes(c: XtreamCredentials, seriesId: String): List<XtreamStream> {
        val raw = get(apiUrl(c, "get_series_info") + "&series_id=${segment(seriesId)}")
        val root = JSONObject(raw)
        val episodes = root.optJSONObject("episodes") ?: return emptyList()
        val result = mutableListOf<XtreamStream>()
        val seasons = episodes.keys()
        while (seasons.hasNext()) {
            val season = episodes.optJSONArray(seasons.next()) ?: continue
            for (index in 0 until season.length()) {
                val item = season.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank() || id == "null") continue
                result += XtreamStream(
                    id = id,
                    name = item.optString("title", "Episode"),
                    categoryId = null,
                    logo = item.optString("info").takeIf { it.startsWith("http") },
                    extension = item.optString("container_extension").takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }
        return result
    }

    fun playbackUrl(c: XtreamCredentials, stream: XtreamStream, kind: String): String {
        val folder = when (kind) { "live" -> "live"; "movie" -> "movie"; else -> "series" }
        val ext = stream.extension ?: if (kind == "live") "ts" else "mp4"
        return "${base(c)}/$folder/${segment(c.username)}/${segment(c.password)}/${stream.id}.$ext"
    }
    private fun parseArray(raw: String): List<JSONObject> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }
}
