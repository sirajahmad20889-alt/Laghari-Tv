package pk.lagharitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pk.lagharitv.app.data.Channel
import pk.lagharitv.app.data.M3uParser
import java.util.concurrent.TimeUnit

private val Ink = Color(0xFF10121B)
private val Panel = Color(0xFF1B2030)
private val Violet = Color(0xFF9B7BFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LaghariTvApp() }
    }
}

@Composable
private fun LaghariTvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlistUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Live TV") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var search by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }
    var message by remember { mutableStateOf("Settings میں اپنی مجاز HTTPS M3U playlist شامل کریں۔") }
    var loading by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val groups = remember(channels) { listOf("All") + channels.mapNotNull { it.group?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val visibleChannels = remember(channels, selectedGroup, search, selectedTab, favorites) {
        channels.filter { channel ->
            (selectedGroup == "All" || channel.group == selectedGroup) &&
            (channel.name.contains(search, true) || channel.group.orEmpty().contains(search, true)) &&
            (selectedTab != "Favorites" || channel.url in favorites)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Violet,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Row(Modifier.fillMaxSize().background(Ink).padding(20.dp)) {
            Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("LAGHARI", color = Violet, fontSize = 25.sp)
                Text("TV", color = Color.White, fontSize = 32.sp)
                Spacer(Modifier.height(12.dp))
                listOf("Live TV", "Movies", "Series", "Favorites", "Settings").forEach { tab ->
                    Button(
                        onClick = { selectedTab = tab },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == tab) Violet else Panel)
                    ) { Text(tab) }
                }
                Spacer(Modifier.weight(1f))
                Text("Laghari TV • v1.0", color = Color.LightGray, fontSize = 11.sp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.fillMaxSize()) {
                Text(selectedTab, fontSize = 26.sp, color = Color.White)
                Spacer(Modifier.height(10.dp))
                when (selectedTab) {
                    "Settings" -> {
                        Text("M3U Playlist Import", color = Violet, fontSize = 19.sp)
                        OutlinedTextField(
                            value = playlistUrl, onValueChange = { playlistUrl = it },
                            label = { Text("HTTPS M3U URL") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(enabled = !loading, onClick = {
                            scope.launch {
                                loading = true
                                message = try {
                                    require(playlistUrl.trim().startsWith("https://", true)) { "براہِ کرم HTTPS playlist URL درج کریں۔" }
                                    val text = withContext(Dispatchers.IO) {
                                        val client = OkHttpClient.Builder()
                                            .connectTimeout(15, TimeUnit.SECONDS)
                                            .readTimeout(30, TimeUnit.SECONDS).build()
                                        val request = Request.Builder().url(playlistUrl.trim()).get().build()
                                        client.newCall(request).execute().use { response ->
                                            check(response.isSuccessful) { "سرور نے HTTP ${response.code} جواب دیا۔" }
                                            response.body?.string() ?: error("Playlist خالی ہے۔")
                                        }
                                    }
                                    val parsed = M3uParser.parse(text)
                                    check(parsed.isNotEmpty()) { "اس URL پر کوئی M3U چینل نہیں ملا۔" }
                                    channels = parsed
                                    selectedGroup = "All"
                                    selectedTab = "Live TV"
                                    "کامیابی! ${parsed.size} چینلز لوڈ ہوگئے۔"
                                } catch (e: Exception) {
                                    "Playlist لوڈ نہیں ہوئی: ${e.message ?: "نامعلوم خرابی"}"
                                }
                                loading = false
                            }
                        }) { Text(if (loading) "Loading…" else "Import Playlist") }
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = Color.LightGray)
                        Text("صرف اپنی یا مجاز فراہم کنندہ کی playlist استعمال کریں۔", color = Color.Gray, fontSize = 12.sp)
                    }
                    "Movies", "Series" -> {
                        Text("یہ سیکشن اگلے مرحلے میں Xtream/VOD کیٹلاگ سے منسلک ہوگا۔", color = Color.LightGray)
                    }
                    else -> {
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                        if (selectedChannel != null) {
                            Text("اب چل رہا ہے: ${selectedChannel!!.name}", color = Violet)
                            AndroidView(
                                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                                update = { it.player = player },
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { player.play() }) { Text("Play") }
                                OutlinedButton(onClick = { player.pause() }) { Text("Pause") }
                                OutlinedButton(onClick = {
                                    player.stop()
                                    selectedChannel = null
                                }) { Text("Close") }
                            }
                        }
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            label = { Text("چینل تلاش کریں") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            groups.take(6).forEach { group ->
                                FilterChip(selected = selectedGroup == group, onClick = { selectedGroup = group },
                                    label = { Text(group, maxLines = 1) })
                            }
                        }
                        if (visibleChannels.isEmpty()) {
                            Text(if (channels.isEmpty()) "Settings سے M3U playlist امپورٹ کریں۔" else "کوئی چینل نہیں ملا۔",
                                color = Color.LightGray)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(visibleChannels) { channel ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Panel),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f).clickable {
                                                selectedChannel = channel
                                                player.setMediaItem(MediaItem.fromUri(channel.url))
                                                player.prepare()
                                                player.play()
                                            }) {
                                                Text(channel.name, color = Color.White)
                                                Text(channel.group ?: "Uncategorised", color = Color.LightGray, fontSize = 11.sp)
                                            }
                                            TextButton(onClick = {
                                                favorites = if (channel.url in favorites) favorites - channel.url else favorites + channel.url
                                            }) { Text(if (channel.url in favorites) "★" else "☆") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
