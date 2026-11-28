# Mimi Chat Desk · standalone Android APK

`releases/Mimi-Chat-Desk.apk` is the chat-first ARM64 build for the Galaxy A53 5G. It updates the earlier Mimi package (version code 8, same signer). The chat launcher **replaces** the old notification/invite workflow; no caller page, Termux, shell commands, notification-reading permission or server needed. This does not answer voice calls.

## Pocket Desk redesign (0.8.0)
Install **`releases/Mimi-Pocket-Desk.apk`** for the redesigned home screen. This side-by-side build has its own Android application ID (`app.mimi.desk.pocket`), signer and private storage. It does **not** update or migrate the older Chat Desk. Keep the old app installed; stop its chat engine before linking the new desk to avoid duplicate replies. Set up the Business number, tester scope, priority rules and optional keys in the new app. To switch back, stop Pocket Desk first.

The top badge tracks the actual connection while Mimi’s pixel island reflects the **saved availability**. Busy and Sleeping only change after you confirm the native picker; the sleep deadline still ends in Pause. Tap **Account & AI** or the **P** avatar for settings. Design assets and the Figma-importable mobile frame are documented in [the handoff](../design/README.md). Browser mode is a local interactive design preview only.

## On the phone
1. Install the chat APK. If you ran the older Termux experiment, stop that process and log out its old linked device first.
2. Tap **Connect WhatsApp**. Enter your Business number with country code. For a first test, enter one friend's number in the optional tester field. **Blank tester = all incoming direct text chats** while a mode is active. Nothing is sent merely by connecting or changing a setting.
3. Choose **Message-taking · no key** to start. Gemini/Groq are optional for conversational follow-ups (sender must opt in). Tap **Save & connect**. Android may request permission to show the agent's running notification; this is not access to other apps' messages.
4. On the first connection enter the displayed private code in **WhatsApp Business → Linked devices → Link a device → Link with phone number instead**. Never share it. Return to Mimi and check the **LINKED** indicator (first setup defaults to Available / Paused). A saved session can be reused on later connections.
5. Tap **Busy**, **Sleeping** or **Driving**. Ask someone to send a **new** personal text. The agent sends a note to your WhatsApp self-chat, then replies with `Mimi — Pavit's own AI bot: Pavit is …`. Conversations, opt-in and follow-ups are separate per sender. No contact list import, broadcast or history replay. Groups, status updates and outgoing messages are ignored. Calls pass through unless Call Shield is explicitly enabled. A sender can use `/stop` or `/start`.
6. **Available / Pause** stops replies but keeps the link open. **Stop chat engine** terminates the process. Stopping does not forget your selection. The next successful connection applies your last saved mode; select Available / Pause if you want future reconnects to stay silent. Android can suspend/kill the app; this is not an always-on guarantee. A message already handed to WhatsApp cannot be recalled by pausing.

## Editing account & AI settings (0.7.1)
The **Account & AI settings** button opens the editor even while the engine is running or connecting. Opening or cancelling does not stop it. A changed number, scope, provider or key takes effect with **Save & reconnect**: Mimi stops the old worker, applies the validated settings, then connects using the selected account. Keeping the same number reuses its local session; a different number may need pairing. A blank provider-key field keeps its saved key. Offline, use **Save & connect** as before.

Device regression check: while linked, open the editor and cancel; the same session should keep running. Open it again, change the tester scope and save. After reconnecting, reopen the editor and verify the new scope, and check that an invalid number cannot be saved. Repeat the open/cancel check while connecting and while stopped.

## Offline availability & Busy reason (0.7)
After saving your Business number, the status card and highlighted tile show that account’s **desired availability**, independently of the OFFLINE / CONNECTING / LINKED indicator. Change it even with no connection. Android app-private preferences retain the latest selection across app/service/device restarts. A stopped service does not relaunch at boot; tap Connect to run it. On each link the engine reads the latest selection, resumes only unexpired modes, and handles fresh events, not an offline backlog. Forgetting the local account removes its saved availability.

**Busy → optional reason → Save Busy** (up to 120 characters). The reason stays on the device by default; tick the sharing checkbox to append it to Busy greetings and Call Shield away texts. A private reason is never passed to the chat engine or model. It is cleared when leaving Busy. Reply Studio previews a shared reason. No network is required to save changes.

## Sleep timer & Reply Studio
- Tap **Sleeping** and pick a nap (20 min), 1 / 6 / 8 hours, 1–1440 custom minutes, a local wake-up time, or manual pause. A past wake-up time means the next day. Cancelling the picker keeps your current mode unchanged. No exact-alarm permission needed.
- The countdown is shown on the desk and foreground notification. When time expires the engine pauses without sending a wake-up message. Switching mode cancels the timer. Disconnecting or stopping keeps the original wake-up deadline; reconnection never starts a fresh full timer. The engine checks the deadline before sending even if Android delayed the timer callback. Use **Pause replies** in the notification to end a mode early without unlinking.
- **Reply Studio → Preview greeting & language** previews each mode offline without messaging anybody. Save English or Hinglish; the selection takes effect the next time you activate an away mode. AI consent/controls remain separate. WhatsApp displays the name on the owner's account; the Mimi signature is inside the message, not a contact rename.
- The session counter counts notes successfully sent to Pavit’s self-chat, not messages he has read. No new transcript is stored for this counter.

## Callback and urgent follow-ups (0.5)
While an away mode is active, senders can use `/help`, `/callback after 6 pm` or `/urgent delivery at the gate`. Requests are labeled in your WhatsApp self-chat. Bare commands ask for details. No AI key needed; commands do not send content to a provider, dial a number, schedule an alarm or bypass pause, opt-out, scope or sleep settings. Urgency is sender-selected, not inferred.

**Reply Studio → Follow-up inbox → Enable list** optionally keeps up to 50 recent tagged requests **per owner**, encrypted on this device. Enable it before the test: existing WhatsApp messages are not imported. Each local entry holds the request type, sender identifier, time, up to 600 characters, and done/open state. The original handoff is still in WhatsApp if local storage is disabled or unavailable. Filter pending/callback/urgent/done; open a request to mark done, reopen, delete or open its chat when a phone number is available. Refresh to load new arrivals. Turning the list off erases its local entries; forgetting that local account erases them too. No alarm or lock-screen preview for urgent messages.

## Call Shield and priority callers (0.6)
Open **Reply Studio → Call Shield & priority callers** after saving your Business number. Add important people by international number (optional name); tap **Add priority caller** for each, turn on **Decline & text while away**, then **Save**. Remove an entry and save to stop exempting it. Unsaved edits are discarded. Up to 50 numbers are stored encrypted per owner on the phone. No contacts permission, Meta billing or AI key required.

Only live incoming one-to-one WhatsApp Business voice/video call offers from identifiable non-priority callers, within your audience scope, are eligible while an away mode is active. Mimi requests a decline and, after the request succeeds, texts the caller your status and `/callback` instructions. Repeat call IDs are ignored; repeated calls can be declined but receive at most one successful away text per caller per minute. No call audio is accessed. `/stop` silences auto-text for that chat. Priority, unidentifiable, stale, group and ordinary SIM calls are not touched. **Available / Pause**, sleep expiry, disconnection or disabling Shield prevents new declines. Priority means “Mimi does nothing”: it cannot override DND, blocked callers, network issues or WhatsApp’s own silence settings. Saving an edit cannot recall a decline already sent.

On-device acceptance test (spare account): put one friend on the priority list and leave a second consenting friend off it. With tester scope blank, select Busy. The first friend's call should ring normally and get no automatic caller message. The second friend should see a declined call and an away text. Repeat in Available: both should pass through. Actual linked-device call signaling depends on WhatsApp; it has not been verified on a physical A53 in this environment.

## Privacy and support
An unofficial Baileys client runs in the app's private storage. Test with a spare number: protocol changes, disconnects or account restrictions are possible. The APK bundles the Android ARM64 Bun runtime and locked JS dependencies; it never installs executable code from the network at runtime. A foreground service exchanges commands with its child over private pipes. Credentials are not put in URLs, logs or a public endpoint. The agent runs on the phone; optional AI inference runs at the chosen provider. Session files are sensitive. Stop, log the device out in WhatsApp, then **Forget local session** to clear local credentials. In case of a stale/expired pairing, repeat that procedure and reconnect. No OTP, pairing code or API key is needed in a support message.

## Build and validation
Use JDK 17, Android SDK platform/build-tools 34, Node 22+, npm, tar, unzip/zip, openssl and Python 3. `tools/build-chat-apk.sh` verifies the pinned Android Bun archive, installs dependencies from `chat/package-lock.json`, runs routing tests, builds the APK and verifies its signature. Set `MIMI_KEYSTORE` / `MIMI_STORE_PASS` to sign an update with your own key. `tools/build-apk.sh` is the **legacy voice APK** build, not the chat APK.

Test on-device: connect → confirm paused → no response to a new DM → choose Sleeping → new DM gets status greeting → a second sender gets an independent greeting → group text gets none → pause → no response. Test screen locked and after a network reconnect. The packaged runner's JS startup was smoke-tested with the matching desktop Bun version, not on an A53 or a live WhatsApp account.

### Pocket Desk 0.8.1 · Pet Mimi
The launcher now shows a cream bunny with a mint scarf and pink heart. Open Pocket Desk to see local, GIF-like sprite loops for each availability; **Pet Mimi** is purely visual and never sends a message or changes a mode. Reduced-motion displays use still artwork. Install over **Pocket Desk 0.8.0** (same package and signing certificate) without clearing app data. This does not upgrade the separate legacy Chat Desk.

### Pocket Desk 0.8.2 · Native pocket panels
Busy, sleep presets/custom minutes/wake-up time, Reply Studio, account setup, Call Shield and the follow-up inbox now use bunny headers, section colors and rounded controls. Destructive actions use a distinct confirmation palette. Forms and notes stay in secure native dialogs; the browser preview does not collect credentials. Android-owned permission and install prompts retain the system appearance.

### Pocket Desk 0.9.0 · Weekly routine, priority circle and time insights
- **Routine** (`Routine`: My weekly routine): blocks with a name, kind (school, study, coaching, work, sleep, driving, custom), start/end, any set of weekdays, an optional reason and greeting language. A one-off date replaces that day's weekly plan; "Mark today as a day off" skips it. Mimi's native service re-reads the timetable every 30 seconds, so blocks switch availability on their own — also while the phone is in your pocket and the app is closed.
- **Manual mode pauses the routine.** Picking Busy, Sleeping or Driving by hand (or Pause in the notification) stops the timetable until you press *Resume routine*.
- **Ask Mimi**: local, offline answers for "aaj kya plan hai", "abhi free hoon?", "next kab", "neend" and tomorrow's plan. Answers come from this phone's timetable only.
- **Priority circle** (`Vip`): up to 40 numbers that never get Mimi's auto-reply. You get a high-priority notification instead, marked urgent when the sender's words say so. Everything else still gets the away reply and lands in the digest.
- **Digest**: when a block ends, the engine reports what arrived while you were away (counts, urgent count, up to six senders) as a notification. Nothing is auto-forwarded to anyone.
- **Time insights** (`TimeLog`): per-block minutes for today, the last seven days and the last four weeks, with a plain CSV export through Android's share sheet. Logged locally only while the chat agent runs; stored in app-private preferences.
- AI remains optional: message-taking works with no key, and adding a Gemini/Groq key still unlocks conversational replies.
- Note: the 0.8.x APK was signed with a key that lived only in the build sandbox. If that key is unavailable, a 0.9.0 build carries a new certificate and therefore needs a fresh install (and one fresh WhatsApp link); it cannot update 0.8.x in place.

### Pocket Desk 0.9.1 · Schedule at a glance, bouncier desk
- **The ongoing notification now shows the plan, not just the mode.** Title carries the current block ("Mimi · School"); the body reads "School till 2:00 pm · Next · Study in 32 min" (or "at 7:00 pm" when it is further away). The notification timestamp is a **live countdown** to the next block, so time-to-next is visible without opening the app. During a sleep block it counts down to wake-up instead, labelled "wake-up". A **Routine** action opens the timetable directly, and **Pause replies** still stops everything in one tap.
- The service refreshes that notification every 30 seconds alongside the routine tick, so the "in X min" text and countdown never go stale.
- Desk motion: springy hover/press on tiles and buttons, a hop on the bunny whenever the mood changes or you pet her, hearts that float up on petting, a pop-in timeline of today's blocks with the live block breathing, and a pulsing status chip. Everything is wrapped in `prefers-reduced-motion`, so turning animations off keeps the desk still.
- One-tap shortcuts under the modes: **Sleep 8h**, **Focus now**, **I'm free** and **Ask Mimi**. Routine starters (school Mon–Sat 8–2, study daily 7–9 pm, sleep daily 11 pm–7 am) build a full week in three taps.
- **Signing key:** since sandbox `/tmp` is wiped between sessions, the Pocket Desk key now lives at `.mimi/mimi-signing.jks` in the workspace (git-ignored, never committed). Keep that file to publish future updates that install over this build.

### Mimi Pocket 2 · side-by-side install
Installs as `app.mimi.desk.pocket2` ("Mimi Pocket 2") and coexists with any earlier Pocket Desk build — nothing is uninstalled or cleared.

```
bash tools/build-chat-apk.sh --pocket-next     # → releases/Mimi-Pocket-2.apk
```

Both apps keep separate private storage, so Pocket 2 needs its own WhatsApp link (one pairing) and its own routine. Messages stay in WhatsApp; only the linked-device session and app settings are per-install. Run the chat engine in only one of them at a time so two devices never answer the same number. Future builds of Pocket 2 are ordinary in-place updates because `.mimi/mimi-signing.jks` is kept with the project.

### Pocket Desk 0.9.2 · Ignore list (total silence)
- **Ignore list** (desk tile and inside the routine card): up to 100 numbers Mimi must never touch. Her engine drops the message before any reply, note, priority alert or digest entry, so nothing is sent to that chat and nothing about it reaches you. `/start`, `/urgent` and `/callback` from those numbers are dropped too.
- Ignoring a number removes it from the priority circle, so the two lists can never contradict each other. Calls are untouched by this list — Call Shield keeps its own rules.
- The list stays on this phone (app-private preferences) and is re-sent to the chat engine whenever it changes or reconnects.

### Pocket Desk 0.9.3 · Contact rules that survive privacy ids
- WhatsApp often delivers a direct chat as a **privacy id (LID)** instead of a phone JID. Rules now match both: the raw sender id and the resolved phone number (`routing.mjs` returns `number`), so an ignored or priority number stays ignored on LID chats too.
- The engine reports each new LID → number pair; the phone stores it in `linkmap.v1` and feeds it back on every reconnect, so the same person is recognised even if WhatsApp omits the alternate id next time.
- **Fail closed:** while an ignore list exists, a privacy chat whose number cannot be resolved is **not answered at all** — Mimi would rather stay silent than message someone you asked her to ignore. Status shows "Stayed silent: a privacy chat could not be matched to a number." until the mapping is learnt.

### Pocket Desk 0.9.4 · Family location, owner-first chats, one-off block skips
- **Family & location** (`Family`): up to 30 numbers. A switch, "Share my location when family asks", gates everything; when on, a family member writing "location bhejo", "live location", "kahan ho" or "where are you" gets a WhatsApp location pin built from the **newest stored fix** on the phone (GPS/network/passive last-known, never a tracking session). "live location" asks get a pin every 30 s for six updates, then stop. Off, missing permission or no stored fix → a short honest text instead. Requires `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` plus the `location` foreground-service type; permission is requested only when the switch is turned on.
- **Owner-first chats:** when you reply to someone from your own phone during an away window, Mimi stops replying in that chat for the rest of the window (her own sends are ignored, tracked by message id). New window → fresh start.
- **AI answers directly:** with a Gemini/Groq key saved, Mimi now answers follow-ups with the model instead of waiting for "YES MIMI" (no consent prompt). Toggle with the `ai.direct` preference in Account & AI; without a key the message-taking path is unchanged.
- **One-off block skip:** tap any block in *Today* to switch it off for today only, or tick "Off today only" / "Off tomorrow only" while editing a block. The weekly pattern is untouched, and skipped days return automatically.

### Signing key, stable by design
`keys/mimi-signing.jks` is committed so **every build shares one certificate**: Android installs each new APK as a normal update, so the WhatsApp link and all settings survive. `tools/build-chat-apk.sh` picks it up automatically when `MIMI_KEYSTORE` is unset, and refuses to build an update without it.

- Alias `mimi`, store/key password `android`, certificate SHA-256 `8D:78:43:7C:60:D8:CF:65:07:4A:7D:4A:E4:A6:CB:92:BA:7B:4D:84:A3:3A:7E:D9:01:C5:6B:E8:34:83:73:BF`.
- Trade-off, stated plainly: because this repository is public, this key is public too. Anyone could sign an APK Android would treat as an update — they cannot reach your phone or its session, but do not install Mimi APKs from anywhere except this repository's own links. Making the repository private removes that exposure while keeping the same key (no reinstall needed).
- Rotating the key means a new certificate, so keep this file for the app's lifetime.

### Pocket Desk 0.9.6 · Notifications back to the standard card
- The custom RemoteViews card is gone. Two countdowns (the system chronometer plus the card's own clock) could disagree and showed a confusing time, so Mimi now uses a plain Android notification with no timer at all: title "Mimi · School", body "School till 2:00 pm · Next: Study in 32 min". The 30-second routine refresh keeps that sentence accurate, so nothing ticks oddly in the status bar.
- Actions stay Pause replies / Routine / Stop agent; digest and priority notifications are standard BigText cards again.

### Pocket Desk 0.9.5 · Desk motion (notification card reverted in 0.9.6)
- **Custom notification cards.** All three notifications now render a small RemoteViews card (`res/layout/pocket_notification.xml`): the pocket bunny in a lilac bubble, a bold title, the plan line, a footer with today's note count and link state, plus a monospace countdown for the next block. The ongoing card pulses in Mimi's colours (indigo while sleeping, blue while busy, purple otherwise), the digest card is rose, and priority contacts are mint or red when flagged urgent. Every card keeps standard title/text and a big-text style, so a launcher that refuses custom views still shows a readable notification.
- Actions are now labelled **Pause**, **Routine**, **Stop** with the same behaviour as before.
- Desk motion: mood changes fire a heart burst and a hop, the headline pops, the status chip breathes and wiggles, taps give a short haptic buzz (10–18 ms, silently skipped if the WebView has no vibrator), and the hero has a slow, soft gradient glow. All of it sits behind `prefers-reduced-motion`.
- Nothing about the message flow changed: same replies, digests, ignore rules and routine switching.

### Pocket Desk 0.9.7 · Reply drafts and an app lock
- **Reply drafts** (`Drafts`): every incoming direct message is sorted into family / friend / college / work / spam plus an urgency flag, and Mimi writes a ready-to-send reply. With a Gemini/Groq key the model does the sorting and drafting (`chat/triage.mjs` validates its JSON: known category, bounded 240-character draft); without a key a local rule set and the current block's reason produce the draft. Nothing is sent on its own — you edit and press **Send** in the drafts desk, or dismiss it. Sending marks that chat as owner-handled, so Mimi stops replying there for the rest of the window. Up to 60 drafts are kept on the phone.
- **App lock** (`Lock`): a 4–8 digit PIN (salted SHA-256, never stored in clear) plus optional fingerprint via `BiometricPrompt`. The gate covers the whole app on launch and again after a minute in the background. Forget-PIN note is shown, since the only recovery is clearing app data.
- Desk now shows `notes · drafts · locked` in the connection line, with tools for **Reply drafts** and **App lock**.
