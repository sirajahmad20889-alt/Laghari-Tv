package pk.lagharitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.lagharitv.app.data.*
import java.util.concurrent.TimeUnit

private val bg = Color(0xFF10121B)
private val panel = Color(0xFF1B2030)
private val accent = Color(0xFF9B7BFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvApp() }
    }
}

@Composable
private fun TvApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("laghari_tv", 0) }
    val scope = rememberCoroutineScope()
    val api = remember { XtreamApi() }
    var tab by remember { mutableStateOf("Live TV") }
    var url by remember { mutableStateOf(prefs.getString("m3u", "") ?: "") }
    var server by remember { mutableStateOf(prefs.getString("server", "") ?: "") }
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var fav by remember { mutableStateOf(prefs.getStringSet("fav", emptySet())?.toSet() ?: emptySet()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Settings میں مجاز M3U یا Xtream اکاؤنٹ شامل کریں۔") }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Channel?>(null) }
    var connected by remember { mutableStateOf(false) }
    val playbackEngine = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(player) { onDispose { playbackEngine.release() } }

    fun loadXtream(kind: String) {
        scope.launch {
            busy = true
            try {
                val credentials = XtreamCredentials(server, user, pass)
                val streams = api.streams(credentials, kind)
                channels = streams.map { s ->
                    Channel(s.name, api.playbackUrl(credentials, s, kind), s.categoryId ?: "Other", s.logo)
                }
                tab = when (kind) { "movie" -> "Movies"; "series" -> "Series"; else -> "Live TV" }
                status = "${channels.size} items loaded."
            } catch (e: Exception) { status = "Catalog error: ${e.message}" }
            busy = false
        }
    }

    val shown = channels.filter {
        it.name.contains(query, true) && (tab != "Favorites" || it.url in fav)
    }
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent)) {
        Row(Modifier.fillMaxSize().background(bg).padding(14.dp)) {
            Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("LAGHARI TV", color = accent, style = MaterialTheme.typography.headlineSmall)
                listOf("Live TV", "Movies", "Series", "Favorites", "Settings").forEach { item ->
                    Button(onClick = {
                        tab = item
                        if (connected && item == "Live TV") loadXtream("live")
                        if (connected && item == "Movies") loadXtream("movie")
                        if (connected && item == "Series") loadXtream("series")
                    }, modifier = Modifier.fillMaxWidth()) { Text(item) }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.fillMaxSize()) {
                Text(tab, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                if (tab == "Settings") {
                    Text("M3U playlist URL")
                    OutlinedTextField(url, { url = it }, label = { Text("HTTPS M3U URL") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            status = try {
                                require(url.startsWith("https://", true)) { "HTTPS URL required" }
                                val raw = withContext(Dispatchers.IO) {
                                    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
                                    client.newCall(Request.Builder().url(url.trim()).build()).execute().use {
                                        check(it.isSuccessful) { "HTTP ${it.code}" }
                                        it.body?.string() ?: error("Empty playlist")
                                    }
                                }
                                channels = M3uParser.parse(raw)
                                check(channels.isNotEmpty()) { "No channels found" }
                                prefs.edit().putString("m3u", url.trim()).apply()
                                tab = "Live TV"
                                "Imported ${channels.size} channels."
                            } catch (e: Exception) { "M3U error: ${e.message}" }
                            busy = false
                        }
                    }) { Text("Import M3U") }
                    Divider()
                    Text("Xtream Codes")
                    OutlinedTextField(server, { server = it }, label = { Text("HTTPS server URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                check(api.authenticate(XtreamCredentials(server, user, pass))) { "Login rejected" }
                                prefs.edit().putString("server", server).putString("user", user).putString("pass", pass).apply()
                                connected = true
                                status = "Xtream login successful."
                            } catch (e: Exception) { status = "Login error: ${e.message}" }
                            busy = false
                        }
                    }) { Text("Connect Xtream") }
                    Text(status, color = Color.LightGray)
                    Text("صرف اپنی یا مجاز سروس استعمال کریں۔", color = Color.Gray)
                } else {
                    Text(status, color = Color.LightGray)
                    if (active != null) {
                        Text("Playing: ${active!!.name}", color = accent)
                        AndroidView(factory = { PlayerView(it).apply { this.player = playbackEngine } },
                            modifier = Modifier.fillMaxWidth().height(190.dp))
                        Row {
                            Button(onClick = { playbackEngine.play() }) { Text("Play") }
                            OutlinedButton(onClick = { playbackEngine.pause() }) { Text("Pause") }
                            OutlinedButton(onClick = { playbackEngine.stop(); active = null }) { Text("Close") }
                        }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                    if (shown.isEmpty()) Text("M3U import کریں یا Xtream connect کریں۔", color = Color.LightGray)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(shown) { ch ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f).clickable {
                                        active = ch
                                        playbackEngine.setMediaItem(MediaItem.fromUri(ch.url))
                                        playbackEngine.prepare()
                                        playbackEngine.play()
                                    }) {
                                        Text(ch.name, color = Color.White)
                                        Text(ch.group ?: "Other", color = Color.LightGray)
                                    }
                                    TextButton(onClick = {
                                        fav = if (ch.url in fav) fav - ch.url else fav + ch.url
                                        prefs.edit().putStringSet("fav", fav).apply()
                                    }) { Text(if (ch.url in fav) "★" else "☆") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
