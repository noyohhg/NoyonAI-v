package com.lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF050511)
private val Panel = Color(0xFF141329)
private val Purple = Color(0xFF9B55FF)
private val Lavender = Color(0xFFD7A8FF)
private val Muted = Color(0xFF9A96B4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiaApp() }
    }
}

@Composable
fun LiaApp() {
    var page by remember { mutableStateOf("Home") }
    var prompt by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Purple,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(Modifier.fillMaxSize().background(Night).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = "History" }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIA AI", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("Y O U R   A I   A S S I S T A N T", fontSize = 8.sp, color = Muted)
                }
                IconButton(onClick = { page = "Settings" }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
            }
            when (page) {
                "Home" -> HomeScreen(prompt, { prompt = it }, { listening = !listening; page = "Chat" }, { page = it })
                "Chat" -> ChatScreen(prompt, { prompt = it })
                "History" -> SimplePage("History", "Your conversations will appear here.")
                else -> SettingsScreen()
            }
            Spacer(Modifier.weight(1f))
            NavigationBar(containerColor = Color(0xFF201B43), contentColor = Lavender) {
                listOf("Home", "Chat", "History", "Settings").forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Icon(when(item) { "Home" -> Icons.Default.Home; "Chat" -> Icons.Default.ChatBubbleOutline; "History" -> Icons.Default.History; else -> Icons.Default.Settings }, item) },
                        label = { Text(item) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFFC65C), selectedTextColor = Color(0xFFFFC65C), indicatorColor = Color(0xFF39305F), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(prompt: String, onPrompt: (String) -> Unit, onTalk: () -> Unit, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(224.dp).background(Brush.radialGradient(listOf(Color(0xFF4A1B70), Color(0xFF14102B), Night)), CircleShape).border(2.dp, Purple, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦", fontSize = 76.sp, color = Lavender)
                    Text("LIA", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("AI COMPANION", fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).size(112.dp).shadow(18.dp, CircleShape).background(Brush.linearGradient(listOf(Purple, Color(0xFF5941C9))), CircleShape).border(5.dp, Color(0xFF17152D), CircleShape).clickable { onTalk() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tap to Talk", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("• ─ ─ ─ • ─ ─ ─ •", color = Purple, fontSize = 17.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(28.dp)).border(1.dp, Color(0xFF573A83), RoundedCornerShape(28.dp)).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = Lavender, modifier = Modifier.padding(horizontal = 8.dp))
            TextField(value = prompt, onValueChange = onPrompt, placeholder = { Text("Ask me anything...", color = Muted) }, modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            IconButton(onClick = { onNavigate("Chat") }) { Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.background(Purple, RoundedCornerShape(14.dp)).padding(10.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile("Chat", "Get instant answers", Icons.Default.ChatBubbleOutline) { onNavigate("Chat") }
            HomeTile("Create Image", "Photos into sketches", Icons.Default.Image) { onNavigate("Settings") }
            HomeTile("Write", "Notes, code, content", Icons.Default.Description) { onNavigate("Chat") }
        }
    }
}

@Composable
fun HomeTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Column(Modifier.weight(1f).height(112.dp).background(Panel, RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF453061), RoundedCornerShape(20.dp)).clickable { click() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Lavender, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subtitle, color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

@Composable
fun ChatScreen(prompt: String, onPrompt: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Text("Chat with LIA", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 20.dp))
        Text("Hi! I'm LIA. Connect an AI provider in Settings to start chatting.", color = Muted)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = message, onValueChange = { message = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...") })
            IconButton(onClick = { onPrompt(message) }) { Icon(Icons.Default.Send, null, tint = Lavender) }
        }
    }
}

@Composable
fun SimplePage(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().weight(1f).padding(top = 28.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(detail, color = Muted)
    }
}

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 20.dp))
        val items = listOf("Connections  •  Gemini, Grok, ElevenLabs API", "Voice & Language", "Chats & Memory", "Look & Feel", "Voice Keyboard", "Broadcast Voice Rooms", "Phone Control & Permissions", "Privacy Policy & About")
        LazyColumn { items(items.size) { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Panel, RoundedCornerShape(16.dp)).clickable { }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = Lavender)
                Spacer(Modifier.width(12.dp))
                Text(items[i], fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        } }
    }
}
