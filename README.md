# CallAgent — Android AI Call Handler

An Android app that automatically answers calls on your behalf using Claude AI
when you decline or don't pick up.

---

## How it works

1. A call comes in to your phone
2. You either decline it or don't answer within your set timeout
3. CallAgent auto-answers the call silently
4. The AI greets the caller, listens, and has a full conversation
5. The call log + summary appear in the app

---

## Setup (Android Studio)

1. Clone / unzip this project
2. Open in **Android Studio Hedgehog or newer**
3. In `app/src/main/res/values/secrets.xml`, add your Anthropic API key
4. Run on your Android device (API 29+ / Android 10+)
5. On first launch:
   - Tap **Set as Default Phone App** and confirm
   - Grant microphone permission
   - Configure your agent name, greeting, and tone
   - Toggle the agent ON

---

## Permissions required

- `READ_PHONE_STATE` — detect incoming calls
- `ANSWER_PHONE_CALLS` — answer calls programmatically
- `RECORD_AUDIO` — listen to the caller
- `INTERNET` — reach Claude API
- `FOREGROUND_SERVICE` — keep running during calls

---

## Tech stack

- Language: **Kotlin**
- Min SDK: **29** (Android 10)
- AI: **Claude claude-sonnet-4-20250514** via Anthropic API
- TTS: Android `TextToSpeech`
- STT: Android `SpeechRecognizer`
- HTTP: **OkHttp**
- Storage: **SharedPreferences** (upgrade to Room for production)
