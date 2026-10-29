package com.noyon.myraai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val safe = org.json.JSONObject.quote(text)
            web.evaluateJavascript("window.onMyraNativeSpeech && window.onMyraNativeSpeech($safe)", null)
        }
    }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech() else web.evaluateJavascript("window.onMyraNativeSpeech && window.onMyraNativeSpeech('')", null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                loader.shouldInterceptRequest(request.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.resources)
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
        web.addJavascriptInterface(NativeBridge(), "MyraNative")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    private fun launchSpeech() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to MYRA")
            }
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            web.evaluateJavascript("window.onMyraNativeSpeech && window.onMyraNativeSpeech('')", null)
        }
    }

    inner class NativeBridge {
        @JavascriptInterface fun startVoiceInput() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchSpeech()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        @JavascriptInterface fun speak(text: String) {
            runOnUiThread { tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "myra-response") }
        }
    }
    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); web.destroy()
        super.onDestroy()
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
