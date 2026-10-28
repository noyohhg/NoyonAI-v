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
'''class PriyaService : Service() {
    private var live: GeminiLive? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
''', 1)
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

print("Project 5 PRO patches applied.")
