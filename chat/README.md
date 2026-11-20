# Mimi · linked-device chat pilot (Termux)

This is **not a new APK**. Run on the A53 using Termux. No public server, caller page, WhatsApp notification access, Meta API key or calling features. The older voice-desk APK is independent: **pause invites there** before this test. This uses an **unofficial** WhatsApp Web client (Baileys); pairing can change or fail, and your account may be restricted. Prefer a spare WhatsApp Business test number. Don't use this for bulk messages. Termux and the phone’s network must stay running; Samsung background limits can disconnect it.

1. Install Termux from its official F-Droid listing or Termux GitHub release. In Termux (private home directory), run:

   ```bash
   pkg update && pkg install git nodejs-lts
   cd ~
   git clone --single-branch -b arena/8151a4ee-psd-whatsapp-agent https://github.com/pavit12301611/psd-whatsapp-agent.git mimi-desk
   cd mimi-desk/chat
   bash start.sh
   ```

2. Enter **your** Business number and **one consenting friend's** test number including country codes. Pick `busy`, `sleeping` or `driving`, then option **1** first. No AI key needed to greet and take notes. Restart the script to change mode. Choices 2/3 use a hidden Gemini/Groq key for optional follow-up replies; the key isn't saved. Override the default model if needed with `MIMI_GEMINI_MODEL` or `MIMI_GROQ_MODEL`.
3. Use the private pairing code displayed **only in your own Termux** in WhatsApp Business → Linked devices → Link a device → Link with phone number instead (or tap its link prompt). Never send the code to another person. Once Termux says **LINKED**, ask the selected friend to send a fresh direct text—not a self-chat, group, voice note or call.
4. Check **Message yourself** in WhatsApp: there should be a `Mimi inbox` note from that tester. Only after the self-chat send succeeds does Mimi tell the tester she left their message. This does **not** mean Pavit has seen it or been alerted by Android. The first reply includes the selected status; later notes get short acknowledgments. Optional AI follow-ups require the friend to reply `YES MIMI` first. Only later text and a short rolling context are submitted to the selected provider. `/stop` pauses the conversation; `/start` reopens it. The pilot ignores old messages and chats other than the selected tester; it does not announce your status to every contact.
5. Ctrl+C stops replies. On the next run, `cd ~/mimi-desk/chat && bash start.sh` resumes the saved session. **To revoke:** log the device out under WhatsApp Business → Linked devices. If pairing is stale, stop the script, revoke the old device and delete only its number-specific session directory under `~/.mimi-linked-chat/` before trying again. Session files grant account access: never upload them, move them into shared storage or paste them into support chat. The owner’s notes and replies remain in WhatsApp.

Test locally without linking an account: `npm ci --omit=optional && npm test`. No actual A53 / WhatsApp pairing or provider account was available in the development sandbox; confirm with the one-contact test before relying on it.
