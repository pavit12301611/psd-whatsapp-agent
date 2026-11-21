# Mimi Pocket Desk

Drag `Mimi-Pocket-Desk.svg` into Figma for an editable 412-wide mobile reference frame. It is a local handoff, not a published Figma file. The four `chat-android/pocket/art/island-*.svg` assets are editable vector illustrations for the mood variants. Run `python3 tools/pocket-art.py` then `python3 tools/pocket-handoff.py` to regenerate.

Source palette: **Pavit’s team library** · Iris/100 (`#5D5FEF`, focus ring) and Fuschia/100 (`#EF5DA8`, Mimi’s heart). Soft lilac surrounds the pet; each mode has both an icon and a text label. A checkmark marks the saved mode. A separate Offline / Connecting / Linked badge reports the engine state, not the pet mood.

The responsive, interactive implementation is shared by the Android launcher and the browser preview (`python3 -m http.server --directory chat-android/pocket`). In a browser, mode changes are illustrative and other actions explain that they require the app. In the APK, Android owns the settings, timer, notes, call rules and WhatsApp connection; the home page can’t read credentials or make network requests.
