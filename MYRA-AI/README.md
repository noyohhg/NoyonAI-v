# MYRA AI — Project 6

An isolated MYRA AI web demo and Android WebView app project. Existing NoyonAI ZIP files and the pre-existing Project 5 workflow are left unchanged.

## Features included
- Responsive light blue/white interface and animated AI orb with pause/resume control
- Gemini text chat with API/model settings and official key link
- Voice input using Android's native speech recognizer in the APK; browser SpeechRecognition where supported
- Voice output through Android text-to-speech and browser speech synthesis where supported
- Image Studio using Gemini image-output endpoint (availability depends on API/model access)
- Local chat history and editable local memory
- Developer profile fields and configurable Telegram channel link
- Language, appearance and voice preferences

## Build the Android APK
1. Open the GitHub repository's **Actions** tab.
2. Select **Build MYRA AI APK**.
3. Press **Run workflow** and wait for the job to finish.
4. Download the **MYRA-AI-debug-apk** artifact from the successful run and extract the APK.

Workflow: https://github.com/noyohhg/NoyonAI-v/actions/workflows/myra-android.yml

## Important
- This Android package is a WebView shell around the bundled interface, not a fully native AI engine.
- Add your Gemini key in API Settings. The demo stores the key locally on the device/browser. For a public production release, use a secure backend instead of shipping user API keys to a browser client.
- Image generation requires API access to an image-output-capable Gemini model; model availability may vary by account and region.
- Chat history and memory are local to the app's WebView storage; they are not cloud-synced.
- Developer photo upload and background wake-word listening are not implemented in this first APK scaffold.
