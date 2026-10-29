package com.noyon.pai

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Blue = Color(0xFF4768F5)
private val Violet = Color(0xFF9360F5)
private val Ink = Color(0xFF17213B)
private val Muted = Color(0xFF77839C)
private data class ChatLine(val user: Boolean, val text: String)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("bn", "BD") }
        setContent { PAIApp { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "pai-reply") } }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable
private fun PAIApp(speak: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pai_settings", 0) }
    var page by remember { mutableStateOf("Home") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var accent by remember { mutableStateOf(prefs.getString("accent", "Blue") ?: "Blue") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var language by remember { mutableStateOf(prefs.getString("language", "বাংলা") ?: "বাংলা") }
    var background by remember { mutableStateOf(prefs.getString("background", "Soft Blue") ?: "Soft Blue") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    val lines = remember { mutableStateListOf<ChatLine>() }
    val history = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val primary = when (accent) { "Green" -> Color(0xFF0B987A); "Rose" -> Color(0xFFE34D70); "Violet" -> Violet; else -> Blue }
    val bg = if (dark) Color(0xFF151B2D) else when (background) { "Warm" -> Color(0xFFFFF7EF); "Mint" -> Color(0xFFEFFFF8); else -> Color(0xFFF4F7FF) }
    val card = if (dark) Color(0xFF202941) else Color.White
    val ink = if (dark) Color(0xFFF3F5FF) else Ink
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it; status = "Voice recognized" }
    }
    fun listen() {
        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "English") "en-US" else "bn-BD")
        }); status = "Listening" } catch (_: Exception) { status = "Voice unavailable" }
    }
    fun send() {
        val q = input.trim(); if (q.isEmpty() || busy) return
        input = ""; lines.add(ChatLine(true, q)); history.add(q); busy = true; status = "Thinking"
        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                if (apiKey.isBlank()) "এটি PAI-এর ডেমো উত্তর। Settings থেকে Gemini API key যোগ করলে AI উত্তর চালু হবে।"
                else try {
                    val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    val rule = if (language == "English") "Answer in English." else "Answer in simple Bengali."
                    val body = JSONObject().put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", "$rule\nUser: $q"))))).toString()
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (code !in 200..299) "AI API error (HTTP $code). API key/model settings পরীক্ষা করো।"
                    else JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: "AI থেকে উত্তর পাওয়া যায়নি।"
                } catch (e: Exception) { "সংযোগে সমস্যা হয়েছে: ${e.message ?: "unknown error"}" }
            }
            lines.add(ChatLine(false, answer)); busy = false; status = "Ready"
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = primary, background = bg, surface = card) else lightColorScheme(primary = primary, background = bg, surface = card)) {
        Scaffold(containerColor = bg, bottomBar = {
            NavigationBar(containerColor = card) {
                listOf("Home", "Chat", "History", "Profile", "Settings").forEach { item ->
                    val icon = when (item) { "Home" -> Icons.Default.Home; "Chat" -> Icons.Default.Chat; "History" -> Icons.Default.History; "Profile" -> Icons.Default.Person; else -> Icons.Default.Settings }
                    NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(icon, null) }, label = { Text(item, fontSize = 10.sp) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(primary, Violet))), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 25.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("PAI", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = ink); Text("Your Personal AI Assistant", fontSize = 11.sp, color = Muted) }
                    Surface(color = Color(0xFFE2FAF1), shape = CircleShape) { Text("● $status", Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = Color(0xFF168D72), fontSize = 10.sp) }
                }
                when (page) {
                    "Home" -> {
                        Text("Voice Assistant", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = ink)
                        Text("তোমার AI-এর সঙ্গে কথা বলো", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TAP TO TALK · PAI", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                                Spacer(Modifier.height(20.dp)); Orb(primary) { listen() }; Spacer(Modifier.height(16.dp))
                                Text("আমি তোমার PAI", color = ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("চাপ দাও—কথা বলো অথবা প্রশ্ন করো", color = Muted, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { listen() }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(4.dp)); Text("কথা বলো") }
                                    OutlinedButton(onClick = { speak("আমি তোমার PAI। কীভাবে সাহায্য করতে পারি?"); status = "Speaking" }) { Icon(Icons.Default.VolumeUp, null); Text("শোনো") }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("✍️ ASK PAI ANYTHING", fontWeight = FontWeight.Bold, color = ink, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("কিছু জিজ্ঞেস করো…") }, singleLine = true)
                                    IconButton(onClick = { page = "Chat"; send() }) { Icon(Icons.Default.Send, null, tint = primary) }
                                }
                            }
                        }
                        Spacer(Modifier.height(15.dp)); Text("QUICK ACCESS", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Quick("💬", "AI Chat", "প্রশ্ন করো", card, ink, Modifier.weight(1f)) { page = "Chat" }
                            Quick("◷", "History", "আগের চ্যাট", card, ink, Modifier.weight(1f)) { page = "History" }
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Quick("👤", "Profile", "Developer", card, ink, Modifier.weight(1f)) { page = "Profile" }
                            Quick("⚙", "Settings", "নিজের মতো সাজাও", card, ink, Modifier.weight(1f)) { page = "Settings" }
                        }
                    }
                    "Chat" -> {
                        Text("💬 AI Chat", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Settings-এ Gemini API key দিলে AI উত্তর পাবে", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(card).padding(12.dp)) {
                            if (lines.isEmpty()) item { Text("আমি PAI। তোমার প্রশ্ন লিখে পাঠাও।", color = Muted, modifier = Modifier.padding(8.dp)) }
                            items(lines) { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.user) Arrangement.End else Arrangement.Start) {
                                Surface(color = if (line.user) primary else if (dark) Color(0xFF2A3656) else Color(0xFFEAF0FF), shape = RoundedCornerShape(15.dp), modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 300.dp)) { Text(line.text, Modifier.padding(12.dp), color = if (line.user) Color.White else ink, fontSize = 13.sp) }
                            } }
                            if (busy) item { CircularProgressIndicator(Modifier.size(24.dp).padding(3.dp), strokeWidth = 2.dp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("মেসেজ লেখো…") })
                            IconButton(onClick = { send() }, enabled = !busy) { Icon(Icons.Default.Send, null, tint = primary) }
                        }
                        TextButton(onClick = { listen() }) { Icon(Icons.Default.Mic, null); Text(" Voice Input") }
                    }
                    "History" -> {
                        Text("◷ Chat History", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("এই ডিভাইসের সাম্প্রতিক প্রশ্ন", color = Muted, fontSize = 12.sp)
                        LazyColumn(Modifier.weight(1f)) { items(history.reversed()) { item ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { input = item; page = "Chat" }, colors = CardDefaults.cardColors(containerColor = card)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ChatBubbleOutline, null, tint = primary); Spacer(Modifier.width(10.dp)); Text(item, color = ink, maxLines = 2) } }
                        } }
                        OutlinedButton(onClick = { history.clear(); lines.clear() }, Modifier.fillMaxWidth()) { Text("Clear History") }
                    }
                    "Profile" -> {
                        Text("Developer Profile", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(primary, Violet))), contentAlignment = Alignment.Center) { Text("👨🏻‍💻", fontSize = 42.sp) }
                                Spacer(Modifier.height(12.dp)); Text("NOYON", color = ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold); Text("PAI AI Developer", color = Muted)
                                Spacer(Modifier.height(12.dp)); Text("Welcome to PAI — তোমার personal AI assistant.", color = ink)
                                Spacer(Modifier.height(18.dp))
                                Button(onClick = { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/allhackfree288"))) } catch (_: Exception) {} }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Join My Channel") }
                            }
                        }
                    }
                    else -> {
                        Text("⚙ Settings", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("PAI-কে নিজের মতো সাজাও", color = Muted, fontSize = 12.sp)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) {
                                Text("🔑 AI API Settings", fontWeight = FontWeight.Bold, color = ink)
                                OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API Key") }, singleLine = true)
                                TextButton(onClick = { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) } catch (_: Exception) {} }) { Text("Get API key ↗") }
                                Button(onClick = { prefs.edit().putString("api_key", apiKey).apply(); status = "Settings saved" }, Modifier.fillMaxWidth()) { Text("Save API Settings") }
                                Text("API key গোপন রাখবে; production-এ secure backend বেশি নিরাপদ।", color = Muted, fontSize = 10.sp)
                            } } }
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) {
                                Text("🌐 Language", color = ink, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("বাংলা", "English").forEach { FilterChip(language == it, { language = it; prefs.edit().putString("language", it).apply() }, label = { Text(it) }) } }
                            } } }
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) {
                                Text("🎨 Appearance & Background", color = ink, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Blue", "Violet", "Green", "Rose").forEach { FilterChip(accent == it, { accent = it; prefs.edit().putString("accent", it).apply() }, label = { Text(it, fontSize = 10.sp) }) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Soft Blue", "Warm", "Mint").forEach { FilterChip(background == it, { background = it; prefs.edit().putString("background", it).apply() }, label = { Text(it, fontSize = 10.sp) }) } }
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark Theme", color = ink, Modifier.weight(1f)); Switch(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }) }
                            } } }
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) {
                                Text("🎙 Voice Settings", color = ink, fontWeight = FontWeight.Bold)
                                Text("Voice input ও reply Android speech service ব্যবহার করে।", color = Muted, fontSize = 12.sp)
                                Button(onClick = { speak("আমি তোমার PAI। কীভাবে সাহায্য করতে পারি?") }, Modifier.fillMaxWidth()) { Text("Test Voice") }
                            } } }
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) {
                                Text("🛡 Advanced Settings", color = ink, fontWeight = FontWeight.Bold)
                                listOf("Microphone", "Floating orb / overlay", "Background service", "Accessibility control", "Screen capture").forEach { name ->
                                    var checked by remember(name) { mutableStateOf(name == "Microphone") }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text(name, color = ink, Modifier.weight(1f), fontSize = 13.sp); Switch(checked, { checked = it }) }
                                }
                                Text("Overlay, screen capture, background service ও Accessibility-এর জন্য পৃথক Android permissions এবং implementation দরকার। এই controls এখন UI demo।", color = Muted, fontSize = 10.sp)
                            } } }
                            item { Card(colors = CardDefaults.cardColors(containerColor = card)) { Column(Modifier.padding(14.dp)) { Text("ⓘ About PAI", color = ink, fontWeight = FontWeight.Bold); Text("PAI Personal AI Assistant · Version 1.0", color = Muted, fontSize = 12.sp) } } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Orb(color: Color, click: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "orb")
    val pulse by tr.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.size(176.dp).scale(pulse).clip(CircleShape).background(Color(0xFFDDE7FF)).clickable(onClick = click), contentAlignment = Alignment.Center) {
        Box(Modifier.size(151.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFC7E9FF), color, Violet, Color(0xFF343CA8)))), contentAlignment = Alignment.Center) {
            Box(Modifier.size(112.dp).border(1.dp, Color.White.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(76.dp).border(1.dp, Color.White.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontSize = 42.sp) }
            }
        }
    }
}

@Composable
private fun Quick(emoji: String, title: String, sub: String, card: Color, ink: Color, modifier: Modifier = Modifier, click: () -> Unit) {
    Card(modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp)) { Text(emoji, fontSize = 22.sp); Spacer(Modifier.height(6.dp)); Text(title, color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(sub, color = Muted, fontSize = 10.sp) }
    }
}
