package pk.lagharitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.lagharitv.app.data.*
import java.util.concurrent.TimeUnit
import java.text.DateFormat
import java.util.Date
import java.security.MessageDigest

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
    var pass by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var fav by remember { mutableStateOf(prefs.getStringSet("fav", emptySet())?.toSet() ?: emptySet()) }
    var recent by remember { mutableStateOf(prefs.getStringSet("recent", emptySet())?.toList()?.take(50) ?: emptyList()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Settings میں مجاز M3U یا Xtream اکاؤنٹ شامل کریں۔") }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Channel?>(null) }
    var connected by remember { mutableStateOf(false) }
    var playerStatus by remember { mutableStateOf("Player ready") }
    var fullscreen by remember { mutableStateOf(false) }
    var epgUrl by remember { mutableStateOf(prefs.getString("epg_url", "") ?: "") }
    var epgItems by remember { mutableStateOf(emptyList<EpgProgramme>()) }
    var selectedSeries by remember { mutableStateOf<XtreamStream?>(null) }
    var episodes by remember { mutableStateOf(emptyList<XtreamStream>()) }
    var autoReloadEnabled by remember { mutableStateOf(prefs.getBoolean("auto_reload_m3u", true)) }
    var lastReloadText by remember { mutableStateOf("Playlist reload status: not yet refreshed") }
    var pinHash by remember { mutableStateOf(prefs.getString("parental_pin_hash", "") ?: "") }
    var pinSetup by remember { mutableStateOf("") }
    var pinDialog by remember { mutableStateOf(false) }
    var pinEntry by remember { mutableStateOf("") }
    var lockedDestination by remember { mutableStateOf("") }
    fun digestPin(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    val playlistFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            status = try {
                val raw = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open playlist file")
                }
                val imported = M3uParser.parse(raw)
                check(imported.isNotEmpty()) { "No channels found in file" }
                channels = imported
                tab = "Live TV"
                "Imported ${channels.size} channels from file."
            } catch (e: Exception) { "File import error: ${e.message}" }
            busy = false
        }
    }
    val playbackEngine = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(playbackEngine) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerStatus = when (playbackState) {
                    Player.STATE_BUFFERING -> "Buffering…"
                    Player.STATE_READY -> "Playing"
                    Player.STATE_ENDED -> "Playback finished"
                    Player.STATE_IDLE -> "Player idle"
                    else -> playerStatus
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                playerStatus = "Playback error: ${error.errorCodeName}"
                status = "Stream play نہیں ہو سکی۔ نیٹ ورک یا playlist URL چیک کریں، پھر Retry دبائیں۔"
            }
        }
        playbackEngine.addListener(listener)
        onDispose {
            playbackEngine.removeListener(listener)
            playbackEngine.release()
        }
    }

    suspend fun reloadSavedM3u(): String {
        val savedUrl = prefs.getString("m3u", null)?.trim().orEmpty()
        require(savedUrl.startsWith("https://", true)) { "Saved HTTPS M3U URL not found" }
        val raw = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(savedUrl).build()).execute().use {
                check(it.isSuccessful) { "HTTP ${it.code}" }
                it.body?.string() ?: error("Empty playlist")
            }
        }
        val refreshed = M3uParser.parse(raw)
        check(refreshed.isNotEmpty()) { "Playlist returned no channels" }
        channels = refreshed
        url = savedUrl
        lastReloadText = "Last refreshed: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) + " · " + refreshed.size + " channels"
        return "Playlist refreshed: " + refreshed.size + " channels."
    }
    fun rememberWatched(channel: Channel) {
        recent = (listOf(channel.url) + recent.filterNot { it == channel.url }).take(50)
        prefs.edit().putStringSet("recent", recent.toSet()).apply()
    }

    fun loadXtream(kind: String) {
        scope.launch {
            busy = true
            try {
                val credentials = XtreamCredentials(server, user, pass)
                val categories = api.categories(credentials, kind).associate { it.id to it.name }
                val streams = api.streams(credentials, kind)
                channels = streams.map { s ->
                    Channel(s.name, api.playbackUrl(credentials, s, kind), categories[s.categoryId] ?: "Other", s.logo)
                }
                selectedSeries = null
                episodes = emptyList()
                tab = when (kind) { "movie" -> "Movies"; "series" -> "Series"; else -> "Live TV" }
                status = "${channels.size} items loaded."
            } catch (e: Exception) { status = "Catalog error: ${e.message}" }
            busy = false
        }
    }

    val shown = channels.filter {
        it.name.contains(query, true) && when (tab) { "Favorites" -> it.url in fav; "Recently Watched" -> it.url in recent; else -> true }
    }
    LaunchedEffect(autoReloadEnabled) {
        if (autoReloadEnabled && prefs.getString("m3u", null) != null) {
            busy = true
            status = try { reloadSavedM3u() }
            catch (e: Exception) { "Automatic playlist reload failed: ${e.message}" }
            busy = false
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = accent)) {
        Column(Modifier.fillMaxSize().background(bg).padding(10.dp)) {
            Text("LAGHARI TV", color = accent, style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Live TV", "Movies", "Series", "Favorites", "Recently Watched", "Settings").forEach { item ->
                    Button(onClick = {
                        if ((item == "Movies" || item == "Series") && pinHash.isNotBlank()) {
                            lockedDestination = item
                            pinEntry = ""
                            pinDialog = true
                        } else {
                            tab = item
                            if (connected && item == "Live TV") loadXtream("live")
                            if (connected && item == "Movies") loadXtream("movie")
                            if (connected && item == "Series") loadXtream("series")
                        }
                    }) { Text(item, maxLines = 1) }
                }
            }
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
                    Text("EPG / XMLTV Guide")
                    OutlinedTextField(epgUrl, { epgUrl = it }, label = { Text("HTTPS XMLTV URL") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            status = try {
                                require(epgUrl.startsWith("https://", true)) { "HTTPS EPG URL required" }
                                val xml = withContext(Dispatchers.IO) {
                                    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
                                    client.newCall(Request.Builder().url(epgUrl.trim()).build()).execute().use {
                                        check(it.isSuccessful) { "HTTP ${it.code}" }
                                        it.body?.string() ?: error("Empty EPG response")
                                    }
                                }
                                epgItems = XmlTvParser.parse(xml)
                                check(epgItems.isNotEmpty()) { "No programme data found in XMLTV" }
                                prefs.edit().putString("epg_url", epgUrl.trim()).apply()
                                "EPG loaded: ${epgItems.size} programmes."
                            } catch (e: Exception) { "EPG error: ${e.message}" }
                            busy = false
                        }
                    }) { Text("Load EPG Guide") }
                    Text("EPG صرف ان چینلز پر دکھے گا جن کا tvg-id، XMLTV channel ID سے match ہو۔", color = Color.Gray)
                    Divider()
                    Divider()
                    Text("Parental PIN protection", color = accent)
                    OutlinedTextField(pinSetup, { pinSetup = it.filter(Char::isDigit).take(6) },
                        label = { Text("New PIN (4-6 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Button(enabled = pinSetup.length in 4..6, onClick = {
                        pinHash = digestPin(pinSetup)
                        prefs.edit().putString("parental_pin_hash", pinHash).apply()
                        pinSetup = ""
                        status = "Parental PIN is enabled for Movies and Series."
                    }) { Text("Set / Change PIN") }
                    if (pinHash.isNotBlank()) {
                        TextButton(onClick = {
                            pinHash = ""
                            prefs.edit().remove("parental_pin_hash").apply()
                            status = "Parental PIN disabled."
                        }) { Text("Disable PIN") }
                    }
                    Text("PIN will be required to open Movies and Series.", color = Color.Gray)
                    Text("Xtream Codes")
                    OutlinedTextField(server, { server = it }, label = { Text("HTTPS server URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                check(api.authenticate(XtreamCredentials(server, user, pass))) { "Login rejected" }
                                prefs.edit().putString("server", server).putString("user", user).remove("pass").apply()
                                connected = true
                                status = "Xtream login successful."
                            } catch (e: Exception) { status = "Login error: ${e.message}" }
                            busy = false
                        }
                    }) { Text("Connect Xtream") }
                    Text(status, color = Color.LightGray)
                    Text("پاس ورڈ اس ڈیوائس پر محفوظ نہیں کیا جاتا؛ ایپ دوبارہ کھولنے پر دوبارہ درج کریں۔", color = Color.Gray)
                    Text("صرف اپنی یا مجاز سروس استعمال کریں۔", color = Color.Gray)
                } else {
                    Text(status, color = Color.LightGray)
                    if (tab == "Live TV" && prefs.getString("m3u", null) != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    status = try { reloadSavedM3u() }
                                    catch (e: Exception) { "Playlist reload error: ${e.message}" }
                                    busy = false
                                }
                            }) { Text("Refresh Playlist") }
                            TextButton(onClick = {
                                autoReloadEnabled = !autoReloadEnabled
                                prefs.edit().putBoolean("auto_reload_m3u", autoReloadEnabled).apply()
                            }) { Text(if (autoReloadEnabled) "Auto reload: ON" else "Auto reload: OFF") }
                        }
                        Text(lastReloadText, color = Color.Gray)
                    }
                    if (active != null) {
                        Text("Playing: ${active!!.name}", color = accent)
                        Text(playerStatus, color = Color.LightGray)
                        val now = System.currentTimeMillis()
                        val channelId = active!!.tvgId
                        val currentProgramme = epgItems.firstOrNull {
                            channelId != null && it.channelId == channelId && now >= it.startMillis && now < it.stopMillis
                        }
                        val nextProgramme = epgItems.firstOrNull {
                            channelId != null && it.channelId == channelId && it.startMillis > now
                        }
                        if (currentProgramme != null) {
                            Text("Now: ${currentProgramme.title}", color = Color.White)
                            Text("Until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(currentProgramme.stopMillis))}", color = Color.LightGray)
                        }
                        if (nextProgramme != null) Text("Next: ${nextProgramme.title} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(nextProgramme.startMillis))}", color = Color.LightGray)
                        if (channelId.isNullOrBlank() || (currentProgramme == null && nextProgramme == null)) {
                            Text("اس چینل کے لیے EPG دستیاب نہیں؛ tvg-id اور XMLTV IDs چیک کریں۔", color = Color.Gray)
                        }
                        AndroidView(factory = { PlayerView(it).apply {
                            this.player = playbackEngine
                            useController = true
                            controllerAutoShow = true
                        } }, modifier = Modifier.fillMaxWidth().height(210.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                TrackSelectionDialogBuilder(ctx, "Audio tracks", playbackEngine, C.TRACK_TYPE_AUDIO)
                                    .build().show()
                            }) { Text("Audio") }
                            OutlinedButton(onClick = {
                                TrackSelectionDialogBuilder(ctx, "Subtitles", playbackEngine, C.TRACK_TYPE_TEXT)
                                    .build().show()
                            }) { Text("Subtitles") }
                        }
                        Row {
                            Button(onClick = { playbackEngine.prepare(); playbackEngine.play() }) { Text("Retry / Play") }
                            OutlinedButton(onClick = { playbackEngine.pause() }) { Text("Pause") }
                            OutlinedButton(onClick = { playbackEngine.stop(); active = null }) { Text("Close") }
                        }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                    if (selectedSeries != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { selectedSeries = null; episodes = emptyList() }) { Text("← Series") }
                            Text(selectedSeries!!.name, color = accent)
                        }
                        if (episodes.isEmpty()) Text("اس سیریز میں اقساط دستیاب نہیں یا ابھی لوڈ نہیں ہوئیں۔", color = Color.LightGray)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(episodes) { episode ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    val credentials = XtreamCredentials(server, user, pass)
                                    val episodeUrl = api.playbackUrl(credentials, episode, "series")
                                    active = Channel(episode.name, episodeUrl, "Episode")
                                    rememberWatched(active!!)
                                    playbackEngine.setMediaItem(MediaItem.fromUri(episodeUrl))
                                    playbackEngine.prepare()
                                    playbackEngine.play()
                                }) { Text(episode.name, color = Color.White, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }
                    if (shown.isEmpty() && selectedSeries == null) Text("M3U import کریں یا Xtream connect کریں۔", color = Color.LightGray)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(shown) { ch ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f).clickable {
                                        if (tab == "Series" && connected) {
                                            val series = channels.firstOrNull { it.url == ch.url }
                                            if (series != null) {
                                                selectedSeries = XtreamStream(series.url.substringAfterLast("/").substringBefore("."), series.name, null, series.logo, null)
                                                scope.launch {
                                                    busy = true
                                                    try { episodes = api.seriesEpisodes(XtreamCredentials(server, user, pass), selectedSeries!!.id) }
                                                    catch (e: Exception) { status = "Episodes error: ${e.message}" }
                                                    busy = false
                                                }
                                            }
                                        } else {
                                            active = ch
                                            rememberWatched(ch)
                                            playbackEngine.setMediaItem(MediaItem.fromUri(ch.url))
                                            playbackEngine.prepare()
                                            playbackEngine.play()
                                        }
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
        if (pinDialog) {
            AlertDialog(
                onDismissRequest = { pinDialog = false; lockedDestination = "" },
                title = { Text("Parental PIN") },
                text = {
                    OutlinedTextField(pinEntry, { pinEntry = it.filter(Char::isDigit).take(6) },
                        label = { Text("Enter PIN") }, visualTransformation = PasswordVisualTransformation())
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (digestPin(pinEntry) == pinHash) {
                            val destination = lockedDestination
                            pinDialog = false
                            lockedDestination = ""
                            tab = destination
                            if (connected && destination == "Movies") loadXtream("movie")
                            if (connected && destination == "Series") loadXtream("series")
                        } else { status = "Incorrect PIN. Try again." }
                    }) { Text("Unlock") }
                },
                dismissButton = { TextButton(onClick = { pinDialog = false; lockedDestination = "" }) { Text("Cancel") } }
            )
        }
    }
}
