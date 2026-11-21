# Mimi Chat Desk · standalone Android APK

`releases/Mimi-Chat-Desk.apk` is the chat-first ARM64 build for the Galaxy A53 5G. It updates the earlier Mimi package (version code 4, same signer). The chat launcher **replaces** the old notification/invite workflow; no caller page, Termux, shell commands, notification-reading permission or server needed. This does not answer voice calls.

## On the phone
1. Install the chat APK. If you ran the older Termux experiment, stop that process and log out its old linked device first.
2. Tap **Connect WhatsApp**. Enter your Business number with country code. For a first test, enter one friend's number in the optional tester field. **Blank tester = all incoming direct text chats** while a mode is active. Nothing is sent merely by connecting or changing a setting.
3. Choose **Message-taking · no key** to start. Gemini/Groq are optional for conversational follow-ups (sender must opt in). Tap **Save & connect**. Android may request permission to show the agent's running notification; this is not access to other apps' messages.
4. On the first connection enter the displayed private code in **WhatsApp Business → Linked devices → Link a device → Link with phone number instead**. Never share it. Return to Mimi and wait for **Linked · Paused**. A saved session can be reused on later connections.
5. Tap **Busy**, **Sleeping** or **Driving**. Ask someone to send a **new** personal text. The agent sends a note to your WhatsApp self-chat, then replies with `Mimi — Pavit's own AI bot: Pavit is …`. Conversations, opt-in and follow-ups are separate per sender. No contact list import, broadcast or history replay. Groups, status updates, calls and outgoing messages are ignored. A sender can use `/stop` or `/start`.
6. **Available / Pause** stops replies but keeps the link open. **Stop chat engine** terminates the process. Reconnects start paused. Android can suspend/kill the app; this is not an always-on guarantee. A message already handed to WhatsApp cannot be recalled by pausing.

## Sleep timer & Reply Studio
- Tap **Sleeping** and pick a nap (20 min), 1 / 6 / 8 hours, 1–1440 custom minutes, a local wake-up time, or manual pause. A past wake-up time means the next day. Cancelling the picker keeps your current mode unchanged. No exact-alarm permission needed.
- The countdown is shown on the desk and foreground notification. When time expires the engine pauses without sending a wake-up message. Switching mode, disconnecting or stopping clears the timer. The engine checks the deadline before sending even if Android delayed the timer callback. Use **Pause replies** in the notification to end a mode early without unlinking.
- **Reply Studio → Preview greeting & language** previews each mode offline without messaging anybody. Save English or Hinglish; the selection takes effect the next time you activate an away mode. AI consent/controls remain separate. WhatsApp displays the name on the owner's account; the Mimi signature is inside the message, not a contact rename.
- The session counter counts notes successfully sent to Pavit’s self-chat, not messages he has read. No new transcript is stored for this counter.

## Privacy and support
An unofficial Baileys client runs in the app's private storage. Test with a spare number: protocol changes, disconnects or account restrictions are possible. The APK bundles the Android ARM64 Bun runtime and locked JS dependencies; it never installs executable code from the network at runtime. A foreground service exchanges commands with its child over private pipes. Credentials are not put in URLs, logs or a public endpoint. The agent runs on the phone; optional AI inference runs at the chosen provider. Session files are sensitive. Stop, log the device out in WhatsApp, then **Forget local session** to clear local credentials. In case of a stale/expired pairing, repeat that procedure and reconnect. No OTP, pairing code or API key is needed in a support message.

## Build and validation
Use JDK 17, Android SDK platform/build-tools 34, Node 22+, npm, tar, unzip/zip, openssl and Python 3. `tools/build-chat-apk.sh` verifies the pinned Android Bun archive, installs dependencies from `chat/package-lock.json`, runs routing tests, builds the APK and verifies its signature. Set `MIMI_KEYSTORE` / `MIMI_STORE_PASS` to sign an update with your own key. `tools/build-apk.sh` is the **legacy voice APK** build, not the chat APK.

Test on-device: connect → confirm paused → no response to a new DM → choose Sleeping → new DM gets status greeting → a second sender gets an independent greeting → group text gets none → pause → no response. Test screen locked and after a network reconnect. The packaged runner's JS startup was smoke-tested with the matching desktop Bun version, not on an A53 or a live WhatsApp account.
