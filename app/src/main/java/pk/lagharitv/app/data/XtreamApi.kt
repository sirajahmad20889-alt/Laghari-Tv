package pk.lagharitv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class XtreamCredentials(val server: String, val username: String, val password: String)

class XtreamApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    suspend fun authenticate(credentials: XtreamCredentials): Boolean = withContext(Dispatchers.IO) {
        val base = credentials.server.trimEnd('/')
        require(base.startsWith("https://")) { "Use an HTTPS server URL." }
        val user = URLEncoder.encode(credentials.username, Charsets.UTF_8.name())
        val pass = URLEncoder.encode(credentials.password, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$base/player_api.php?username=$user&password=$pass").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body?.string() ?: return@withContext false
            JSONObject(body).optJSONObject("user_info")?.optString("auth") == "1"
        }
    }
}
