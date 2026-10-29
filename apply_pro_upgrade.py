from pathlib import Path

root = Path("extracted/NoyonAI/app/src/main/java/com/priya/ai")

def replace(path, old, new):
    p = root / path
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"Expected source block not found in {path}; refusing unsafe patch.")
    p.write_text(s.replace(old, new, 1))

replace("Store.kt",
'''    fun setAlways(c: Context, v: Boolean) = p(c).edit().putBoolean("always", v).apply()
''',
'''    fun setAlways(c: Context, v: Boolean) = p(c).edit().putBoolean("always", v).apply()
    fun language(c: Context) = p(c).getString("language", "বাংলা + हिंदी")!!
    fun setLanguage(c: Context, v: String) = p(c).edit().putString("language", v).apply()
''')

replace("GeminiLive.kt",
'''        val sys = "Tum Noyon AI ho, ek friendly, caring AI assistant. Hinglish me, chhote natural sentences me, " +
            "female voice me baat karo (feminine grammar: 'main kar rahi hoon'). Phone control ke liye tools use karo: " +
            "app kholna, screen padhna, tap karna, type karna, scroll, call. Gana ya video bajane ko kaha jaye to play_youtube tool use karo (wo khud search karke play bhi karta hai), pause/next ke liye media_control. Koi bhi zaroori baat user bataye to remember tool se save karo. " +
            "Purani yaaddasht (memory):\\n" + Store.recent(ctx)
''',
'''        val languageRule = when (Store.language(ctx)) {
            "শুধু বাংলা" -> "শুধু স্বাভাবিক, সহজ বাংলায় কথা বলবে।"
            "केवल हिंदी" -> "सिर्फ सरल, स्वाभाविक हिंदी में बात करो।"
            else -> "User jis bhasha me baat kare, usi me jawab do; Bangla aur Hindi dono achchhi tarah use karo."
        }
        val sys = "You are Noyon AI, a friendly, caring female voice assistant. " + languageRule +
            " Keep replies natural and concise. Use feminine self-reference in Hindi where appropriate. " +
            "For phone control use tools: open apps, read screen, tap, type, scroll, call, and media controls. " +
            "For music or video use play_youtube; for pause/next use media_control. Save important user-provided facts using remember. " +
            "Prior conversation memory:\\n" + Store.recent(ctx)
''')

replace("MainActivity.kt",
'''import androidx.compose.runtime.*
''',
'''import androidx.compose.runtime.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
''')

replace("MainActivity.kt",
'''                Image(painterResource(bgs[design]), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                val w = maxWidth; val h = maxHeight
''',
'''                Image(painterResource(bgs[design]), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                val w = maxWidth; val h = maxHeight
                // Lightweight mouth animation over the existing avatar while Gemini audio is playing.
                val speaking = status.contains("bol raha hai", ignoreCase = true)
                val mouthMotion = rememberInfiniteTransition(label = "avatar-mouth")
                val mouthOpen by mouthMotion.animateFloat(
                    initialValue = 0.004f, targetValue = if (speaking) 0.014f else 0.004f,
                    animationSpec = infiniteRepeatable(animation = tween(180, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "mouth-open"
                )
                if (speaking) {
                    Box(Modifier.offset(x = w * 0.468f, y = h * 0.386f)
                        .size(width = w * 0.064f, height = h * 0.017f)
                        .clip(RoundedCornerShape(50)).background(Color(0xFFD99B88)), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth(0.74f).height(h * mouthOpen).clip(RoundedCornerShape(50)).background(Color(0xFF713D3B)))
                    }
                }
''')

replace("MainActivity.kt",
'''        Text("Key aistudio.google.com se milegi. Phone me hi save hoti hai.", color = Color.Gray, fontSize = 12.sp)
        Button({ Store.setApiKey(ctx, key); Store.setModel(ctx, model); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show(); back() }, Modifier.fillMaxWidth()) { Text("Save") }
''',
'''        Text("Gemini key Google AI Studio se milegi. Key ko kisi ke saath share mat karna.", color = Color.Gray, fontSize = 12.sp)
        OutlinedButton({
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }
            catch (_: Exception) { Toast.makeText(ctx, "Browser nahi mila", Toast.LENGTH_SHORT).show() }
        }, Modifier.fillMaxWidth()) { Text("Get Gemini API Key — Official Site") }
        var language by remember { mutableStateOf(Store.language(ctx)) }
        Text("AI response language", color = Color.White, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("বাংলা + हिंदी", "শুধু বাংলা", "केवल हिंदी").forEach { option ->
                FilterChip(selected = language == option, onClick = { language = option; Store.setLanguage(ctx, option) }, label = { Text(option, fontSize = 11.sp) })
            }
        }
        Button({ Store.setApiKey(ctx, key); Store.setModel(ctx, model); Store.setLanguage(ctx, language); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show(); back() }, Modifier.fillMaxWidth()) { Text("Save Settings") }
''')

# Make the microphone service keep CPU/audio processing alive during screen-off listening.
p = Path("extracted/NoyonAI/app/src/main/java/com/priya/ai/PriyaService.kt")
s = p.read_text()
s = s.replace("import android.os.IBinder" + chr(10), "import android.os.IBinder" + chr(10) + "import android.os.PowerManager" + chr(10), 1)
s = s.replace("class PriyaService : Service() {" + chr(10) + "    private var live: GeminiLive? = null" + chr(10), "class PriyaService : Service() {" + chr(10) + "    private var live: GeminiLive? = null" + chr(10) + "    private var cpuWakeLock: PowerManager.WakeLock? = null" + chr(10), 1)
s = s.replace('''        if (live == null) {
            live = GeminiLive(applicationContext).also { PriyaState.live = it; it.start() }
            PriyaState.running.value = true
        }
''','''        if (live == null) {
            // Keep audio/network processing alive while the screen is off; capped to 6 hours.
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NoyonAI:VoiceListening").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
            live = GeminiLive(applicationContext).also { PriyaState.live = it; it.start() }
            PriyaState.running.value = true
        }
''', 1)
s = s.replace('''    override fun onDestroy() {
        live?.stop(); live = null; PriyaState.live = null
''','''    override fun onDestroy() {
        live?.stop(); live = null; PriyaState.live = null
        cpuWakeLock?.let { if (it.isHeld) it.release() }; cpuWakeLock = null
''', 1)
p.write_text(s)

# Add the requested developer profile photo to the extracted Project 5 build copy.
main_file = root / "MainActivity.kt"
main = main_file.read_text()
main = main.replace("import androidx.compose.foundation.layout.weight\\n", "")
imports = [
    "import android.graphics.BitmapFactory",
    "import java.net.URL",
    "import androidx.compose.foundation.shape.CircleShape",
    "import androidx.compose.ui.graphics.asImageBitmap",
    "import androidx.compose.ui.text.font.FontWeight",
    "import kotlinx.coroutines.Dispatchers",
    "import kotlinx.coroutines.withContext",
    "import android.content.Intent",
    "import android.net.Uri",
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.clickable",
    "import androidx.compose.foundation.layout.Arrangement",
    "import androidx.compose.foundation.layout.Column",
    "import androidx.compose.foundation.layout.Row",
    "import androidx.compose.foundation.layout.Spacer",
    "import androidx.compose.foundation.layout.fillMaxSize",
    "import androidx.compose.foundation.layout.fillMaxWidth",
    "import androidx.compose.foundation.layout.height",
    "import androidx.compose.foundation.layout.padding",
    "import androidx.compose.foundation.shape.RoundedCornerShape",
    "import androidx.compose.ui.graphics.Brush",
    "import androidx.compose.ui.zIndex",
    "import androidx.compose.material3.Card",
    "import androidx.compose.material3.Button",
    "import androidx.compose.material3.OutlinedButton",
    "import androidx.compose.material3.CardDefaults",
    "import androidx.compose.material3.OutlinedTextField",
]
package_line = "package com.priya.ai"
if package_line not in main:
    raise SystemExit("Expected Kotlin package declaration not found; refusing unsafe profile patch.")
for imp in imports:
    if imp not in main:
        main = main.replace(package_line, package_line + chr(10) + imp, 1)
home_anchor = "val w = maxWidth; val h = maxHeight"
if home_anchor not in main:
    raise SystemExit("Expected Project 5 home layout anchor not found; refusing unsafe profile patch.")
main = main.replace(home_anchor, home_anchor + chr(10) + "                var myraPage by remember { mutableStateOf(\"Home\") }" + chr(10) + "                MyraProfileAvatar(Modifier.offset(x = w * 0.05f, y = h * 0.035f).size(w * 0.13f))" + chr(10) + "                MyraShellOverlay(page = myraPage, onNavigate = { myraPage = it })", 1)
avatar = '''
@Composable
private fun MyraProfileAvatar(modifier: Modifier = Modifier) {
    val photoUrl = "https://i.ibb.co/5gzzzNDD/1000012900.jpg"
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(photoUrl).openConnection()
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Developer profile photo",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF263D68)), contentAlignment = Alignment.Center) {
                Text("N", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
'''
if "private fun MyraProfileAvatar(" not in main:
    main += avatar

shell = r'''
@Composable
private fun MyraShellOverlay(page: String, onNavigate: (String) -> Unit) {
    if (page == "Voice" || page == "Chat") {
        Box(Modifier.fillMaxSize().zIndex(100f)) {
            Button(
                onClick = { onNavigate("Home") },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) { Text("← MYRA Home") }
        }
        return
    }
    val ink = Color(0xFFF3ECFF)
    val blue = Color(0xFFD35BFF)
    val panel = Color(0xFF100D22)
    Box(Modifier.fillMaxSize().background(Color(0xFF05050D)).zIndex(100f)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MyraProfileAvatar(Modifier.size(58.dp))
                Column(Modifier.weight(1f)) {
                    Text("MYRA AI", color = ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Your personal AI assistant", color = Color(0xFF6C7892), fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onNavigate("Settings") }) { Text("⚙") }
            }
            when (page) {
                "Home" -> {
                    Spacer(Modifier.weight(0.4f))
                    Text("Hello, Noyon 👋", color = ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("What can I help you with today?", color = Color(0xFF6C7892), fontSize = 14.sp)
                    Spacer(Modifier.height(30.dp))
                    Box(Modifier.size(208.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF9CCBFF), blue, Color(0xFF2336A8)))).clickable { onNavigate("Voice") }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(160.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                            Text("MYRA", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("TAP TO TALK", color = blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MyraActionCard("Chat / Voice", "Talk with MYRA", Modifier.weight(1f)) { onNavigate("Chat") }
                        MyraActionCard("History", "Saved assistant memory", Modifier.weight(1f)) { onNavigate("History") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                "Settings" -> {
                    Text("Settings", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Text("Personalize your MYRA experience", color = Color(0xFF6C7892), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp))
                    MyraSettingRow("Developer", "Profile, introduction and channel") { onNavigate("Developer") }
                    MyraSettingRow("API Settings", "Gemini provider and API key") { onNavigate("API") }
                    MyraSettingRow("Chat / Voice", "Open the original assistant experience") { onNavigate("Chat") }
                    MyraSettingRow("History", "View saved assistant memory") { onNavigate("History") }
                    MyraSettingRow("Home Screen", "Return to the MYRA home screen") { onNavigate("Home") }
                    Spacer(Modifier.weight(1f))
                }
                "History" -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Text("History", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Text("Saved assistant memory", color = Color(0xFF6C7892), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp))
                    val savedHistory = Store.recent(context)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text(savedHistory.ifBlank { "No saved history yet. Start talking to MYRA to build your memory." }, color = ink, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { onNavigate("Settings") }, modifier = Modifier.fillMaxWidth()) { Text("Back to Settings") }
                }
                "API" -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var apiKey by remember { mutableStateOf("") }
                    var model by remember { mutableStateOf("gemini-2.5-flash") }
                    Text("API Settings", color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Text("Provider: Google Gemini", color = Color(0xFF6C7892), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp))
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Gemini API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Get a key from Google AI Studio. Keep your key private.", color = Color(0xFF6C7892), fontSize = 12.sp)
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) } }, modifier = Modifier.fillMaxWidth()) { Text("Get API Key") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        Store.setApiKey(context, apiKey.trim())
                        Store.setModel(context, model.trim().ifBlank { "gemini-2.5-flash" })
                        Toast.makeText(context, "API settings saved", Toast.LENGTH_SHORT).show()
                        onNavigate("Settings")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save API Settings") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { onNavigate("Settings") }, modifier = Modifier.fillMaxWidth()) { Text("Back to Settings") }
                }
                "Developer" -> {
                    Spacer(Modifier.height(24.dp))
                    MyraProfileAvatar(Modifier.size(112.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("DEVELOPER", color = Color(0xFF6C7892), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Noyon", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Creator of MYRA AI", color = Color(0xFF6C7892), fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/allhackfree288"))) } }) { Text("Join Channel") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { onNavigate("Settings") }, modifier = Modifier.fillMaxWidth()) { Text("Back to Settings") }
                }
            }
            if (page != "Developer") {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    MyraNavItem("⌂", "Home", page == "Home", blue) { onNavigate("Home") }
                    MyraNavItem("⚙", "Settings", page == "Settings", blue) { onNavigate("Settings") }
                    MyraNavItem("◉", "Chat", page == "Chat", blue) { onNavigate("Chat") }
                }
            }
        }
    }
}

@Composable
private fun MyraActionCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF17243D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color(0xFF6C7892), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MyraSettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onClick() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFF17243D), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = Color(0xFF6C7892), fontSize = 12.sp)
            }
            Text("›", color = Color(0xFF315FEA), fontSize = 26.sp)
        }
    }
}

@Composable
private fun MyraNavItem(icon: String, label: String, selected: Boolean, blue: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = if (selected) blue else Color(0xFF7D879B), fontSize = 21.sp)
        Text(label, color = if (selected) blue else Color(0xFF7D879B), fontSize = 11.sp)
    }
}
'''
if "private fun MyraShellOverlay(" not in main:
    main += shell
main_file.write_text(main)

print("Project 5 PRO patches applied: MYRA Home/Settings shell enabled.")

# MYRA incremental update build marker

# MYRA visual refresh request acknowledged.
