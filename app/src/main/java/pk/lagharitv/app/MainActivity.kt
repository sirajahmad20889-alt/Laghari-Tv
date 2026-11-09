package pk.lagharitv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.lagharitv.app.data.Channel

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
    var playlistUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Live TV") }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var message by remember { mutableStateOf("Add your own M3U playlist or Xtream account to begin.") }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Violet,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Row(Modifier.fillMaxSize().background(Ink).padding(28.dp)) {
            Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LAGHARI", color = Violet, fontSize = 27.sp)
                Text("TV", color = Color.White, fontSize = 34.sp)
                Spacer(Modifier.height(18.dp))
                listOf("Live TV", "Movies", "Series", "Favorites", "Settings").forEach { tab ->
                    var focused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { selectedTab = tab },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                            .onFocusChanged { focused = it.isFocused }.focusable(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == tab || focused) Violet else Panel
                        )
                    ) { Text(tab) }
                }
                Spacer(Modifier.weight(1f))
                Text("Original IPTV player", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.fillMaxSize()) {
                Text(selectedTab, fontSize = 28.sp, color = Color.White)
                Spacer(Modifier.height(12.dp))
                if (selectedTab == "Settings") {
                    Text("Playlist setup", color = Violet, fontSize = 20.sp)
                    OutlinedTextField(
                        value = playlistUrl, onValueChange = { playlistUrl = it },
                        label = { Text("M3U playlist URL (HTTPS recommended)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        message = if (playlistUrl.startsWith("https://")) "HTTPS playlist URL saved in this session."
                        else "Enter a valid HTTPS playlist URL."
                    }) { Text("Save playlist URL") }
                    Text("Xtream Codes integration is part of the next development phase.", color = Color.LightGray)
                } else {
                    Text(message, color = Color.LightGray)
                    Spacer(Modifier.height(14.dp))
                    if (channels.isEmpty()) {
                        Text("Your content will appear here after connecting an authorized provider.",
                            color = Color.LightGray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(channels) { channel ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel),
                                    modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(channel.name, color = Color.White)
                                        Text(channel.group ?: "Uncategorised", color = Color.LightGray, fontSize = 12.sp)
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
