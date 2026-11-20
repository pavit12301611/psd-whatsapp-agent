# Mimi · phone-powered voice desk

A sideloadable Android prototype for Samsung A53 5G and other Android 9+ phones. It offers **a live AI voice conversation via a link**, not interception or auto-answer of WhatsApp calls. No Meta Cloud API, root, Shizuku, account linking, public phone IP or always-on application server. **The phone must be online to send an invite.** Hosting a few public static files over HTTPS is still required. Google's servers process the live audio; this is not an offline model. Provider availability, terms, quotas and charges apply.

## Install and connect

1. Download `releases/Mimi-Voice-Desk.apk` to the phone, open it, and allow installation from that source if you trust the APK. The package is signed locally for sideload testing, not a Play release. Do not disable Play Protect. If Android restricts notification access for sideloaded apps, review the device's restricted-settings warning before deciding whether to grant it.
2. Publish **only the contents of `caller/`** (or extract `releases/Mimi-Caller-Page.zip`) to an HTTPS static host you control. For example, upload them to a static site with `index.html` at its root. No build command, database, function or key is needed on the host. Open the URL without an invite: it should say **Open the private link from your WhatsApp chat** with Start disabled. Do not use localhost, a GitHub source-code URL, or someone else's page. That page is trusted with short-lived bearer tokens and caller audio.
3. In Mimi → **Connections → Provider keys & invite page**, enter that exact URL (include a trailing `/` for a directory), your WhatsApp Business number with country code, and your Gemini Developer API key with Live API access. A configurable Live audio model is provided; if access changes, enter one available to your project. Optional Groq key enables **Refine with Groq** on a selected returned brief, **not** live audio fallback. Permanent keys stay AES-GCM encrypted with Android Keystore; never enter them on the caller page or send them in chat.
4. Enable **Notification access** for Mimi. Android grants broad notification visibility; Mimi filters to `com.whatsapp.w4b` before processing. Allow contacts lookup for Available mode (no address book upload). Enable WhatsApp Business chat notifications with reply actions. Select a mode and **Enable invites**. Paused by default. If Samsung puts Mimi to sleep, review app battery/background settings and repeat the locked-screen test. This integration depends on notification metadata and cannot guarantee delivery.

## How the rule works

| Incoming event | Available | Busy / Sleeping / Driving |
|---|---|---|
| Recent direct message with an exposed international number absent from Contacts | Invite | Invite |
| Saved contact or identity cannot be checked | Skip | Invite if eligible direct message |
| Ring only, group, stale message, summary, hidden or no reply action | Skip | Skip |
| Invites paused | Skip | Skip |

A stranger is **not** guessed from a display name. Only a `tel:` URI or full international phone number from the notification is checked. Android/work profile boundaries, OEM differences, private notifications, currently open chats and WhatsApp versions can reduce coverage. One invite per conversation per 6 hours; maximum 20 mint attempts per local day. No automatic retries. Lock-screen privacy is not bypassed. There is no continuously running web server or polling bot on the phone.

## The call and brief

On an eligible notification Android invokes WhatsApp's reply action. Mimi mints a model-constrained Gemini token **on the phone**: one session, four minutes to start, ten minutes to expiry. The link carries this token in its fragment, not in HTTP requests to the static host. A recipient could forward the link before using it; treat it as private. Never enable third-party analytics on the caller page.

The recipient opens it in a microphone-capable browser, reads the AI disclosure and taps **Agree & start voice call**. Browser PCM streams directly to Gemini over TLS; model audio plays in the browser. **Finish & make brief** stops the mic and requests a spoken/transcribed summary. If interrupted, available caller transcript text can be reviewed instead. The caller can edit the note and must tap **Send brief on WhatsApp** and confirm Send. Nothing is silently delivered. The owner gets a normal WhatsApp text; while enabled, the app also captures matching recent invite chat notifications in its local inbox (most recent 40). These notes are caller-submitted, not verified transcripts. No raw audio is stored by Mimi. Clear the inbox or forget keys in Connections. Clearing app data removes local state.

## Build & check

Linux, JDK 17, Android SDK platform 34 and build-tools 34.0.0, `zip`. No Gradle or third-party Android runtime dependencies.

```bash
export ANDROID_HOME=/path/to/sdk
./tools/build-apk.sh
mkdir -p build/tests
javac -d build/tests app/src/main/java/app/mimi/desk/Policy.java tests/PolicyTest.java
java -cp build/tests app.mimi.desk.PolicyTest
node --test tests/caller.test.cjs
python3 tools/preview.py   # localhost:8080 = demo dashboard, /call/ = caller
```

Build verifies the APK signature. Override `BUILD_TOOLS` and `ANDROID_JAR` for alternate SDK paths. By default a local test signing key is created **outside the repo**. Keep your own signing key for updates using `MIMI_KEYSTORE` and `MIMI_STORE_PASS`; a different signer requires uninstalling first (erases settings). Static previews never use real keys or listen to notifications. Licenses for bundled font/icons in `licenses/`.

## Before relying on it

No A53 or provider credentials were available for end-to-end validation. On your phone, test with a consenting second person: pause → no reply; Available → saved number skipped / unsaved number invited; Busy → saved contact invited; group/ring-only → no reply. Use a fresh conversation or wait for cooldown. Test with screen locked, mic denied, expired link, failed network, repeated messages, muted chat, Android restart, and both ends speaking. Review the returned brief against what was actually said. Provider HTTP errors appear in Connections without logging credentials. This prototype is not for emergencies or guaranteed overnight coverage.
