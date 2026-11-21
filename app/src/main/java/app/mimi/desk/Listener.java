package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.service.notification.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class Listener extends NotificationListenerService {

  final ThreadPoolExecutor queue = new ThreadPoolExecutor(
    1,
    1,
    5,
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(16),
    new ThreadPoolExecutor.DiscardPolicy()
  );

  static volatile boolean connected;

  @Override public void onListenerConnected() { super.onListenerConnected(); connected = true; }
  @Override public void onListenerDisconnected() { connected = false; super.onListenerDisconnected(); }

  public void onDestroy() {
    connected = false;
    queue.shutdownNow();
    super.onDestroy();
  }

  public void onNotificationPosted(StatusBarNotification sb) {
    if (!"com.whatsapp.w4b".equals(sb.getPackageName())) return;
    Store store = new Store(this);
    store.p.edit().putLong("lastSeen", System.currentTimeMillis()).apply();
    if (!store.enabled()) { store.status("Received notification; invites are paused."); return; }
    Notification n = sb.getNotification();
    // Summary updates often follow the actual chat event. Do not replace its outcome.
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
    if (n.category != null && !Notification.CATEGORY_MESSAGE.equals(n.category)) {
      store.status("Not a chat message notification."); return;
    }
    queue.execute(() -> {
      Store s = new Store(this);
      try {
        handle(s, sb);
      } catch (Exception e) {
        s.status(
          "Invite not delivered. Check the reply action, Gemini key, model, connection and quota." +
            (e instanceof java.io.IOException &&
            String.valueOf(e.getMessage()).matches("Provider HTTP [0-9]+")
              ? " " + e.getMessage()
              : "")
        );
      }
    });
  }

  static String str(Object o) {
    return o == null ? "" : o.toString();
  }

  static String hash(String v) throws Exception {
    return android.util.Base64.encodeToString(
      MessageDigest.getInstance("SHA-256").digest(v.getBytes("UTF-8")),
      android.util.Base64.NO_WRAP
    );
  }

  boolean unsaved(String raw) {
    if (
      checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
      PackageManager.PERMISSION_GRANTED
    ) return false;
    String num = raw.replaceAll("[ ()-]", "");
    if (!num.matches("\\+[1-9][0-9]{6,14}")) return false;
    Uri u = Uri.withAppendedPath(
      ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
      Uri.encode(num)
    );
    try (
      Cursor c = getContentResolver().query(
        u,
        new String[] { ContactsContract.PhoneLookup._ID },
        null,
        null,
        null
      )
    ) {
      return c != null && !c.moveToFirst();
    } catch (RuntimeException e) {
      return false;
    }
  }

  void handle(Store s, StatusBarNotification sb) throws Exception {
    Notification n = sb.getNotification();
    Bundle b = n.extras;
    if (!s.enabled()) return;
    // WhatsApp / AndroidX may populate a conversation title even for a direct chat.
    // Trust the explicit group flag, falling back to the legacy title rule if absent.
    if (Policy.group(b.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION),
        b.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION),
        b.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null)) {
      s.status("Skipped group or ambiguous conversation."); return;
    }
    List<Notification.MessagingStyle.Message> msgs;
    Person self = b.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      Parcelable[] rows = b.getParcelableArray(Notification.EXTRA_MESSAGES);
      msgs = rows == null ? Collections.emptyList() : Notification.MessagingStyle.Message.getMessagesFromBundleArray(rows);
    } else {
      Notification.Style recovered = Notification.Builder.recoverBuilder(this,n).getStyle();
      msgs = recovered instanceof Notification.MessagingStyle ? ((Notification.MessagingStyle)recovered).getMessages() : Collections.emptyList();
    }
    if (msgs.isEmpty()) { s.status("No readable chat message in this notification."); return; }
    Notification.MessagingStyle.Message msg = msgs.get(msgs.size() - 1);
    Person who = msg.getSenderPerson();
    if (who == null || who.equals(self)) { s.status("Skipped outgoing or unidentified message."); return; }
    String from = str(who.getName()), title = str(b.getCharSequence(Notification.EXTRA_TITLE));
    if (title.isEmpty()) title = from;
    if (from.isEmpty()) from = title;
    if (from.isEmpty()) { s.status("Sender identity is hidden."); return; }
    long now = System.currentTimeMillis();
    if (
      !Policy.fresh(msg.getTimestamp(), now) ||
      !Policy.fresh(sb.getPostTime(), now)
    ) { s.status("Skipped old notification; send a new message."); return; }
    String uri = str(who.getUri()),
      number = uri.startsWith("tel:") ? Uri.decode(uri.substring(4)) : title;
    String id = hash(
      n.getShortcutId() != null
        ? n.getShortcutId()
        : uri.isEmpty()
          ? title
          : uri
    );
    String text = str(msg.getText());
    if (text.isEmpty()) { s.status("No text in message. Test with a plain text message."); return; }
    long sent = s.p.getLong("sent." + id, 0);
    if (text.startsWith(Store.PREFIX)) {
      if (sent == 0 || now - sent > 86400000L) return;
      JSONArray list = s.briefs();
      String uid = hash(id + ":" + msg.getTimestamp() + text);
      for (int i = 0; i < list.length(); i++) if (
        uid.equals(list.getJSONObject(i).optString("uid"))
      ) return;
      JSONArray next = new JSONArray().put(
        Store.obj(
          "uid",
          uid,
          "from",
          from,
          "time",
          msg.getTimestamp(),
          "text",
          text
            .substring(Store.PREFIX.length(), Math.min(text.length(), 4100))
            .trim()
        )
      );
      for (int i = 0; i < Math.min(list.length(), 39); i++) next.put(
        list.get(i)
      );
      s.p.edit().putString("briefs", next.toString()).apply();
      return;
    }
    String mode = s.mode();
    if (!s.ready()) { s.status("Message seen; finish provider and caller-page setup."); return; }
    if (!Policy.cooled(sent, now)) { s.status("Message seen; this chat already received an invite in the last 6 hours."); return; }
    if (!Policy.invite(s.enabled(), mode, "Available".equals(mode) && unsaved(number))) {
      s.status("Available mode: contact saved or unsaved number could not be verified. Test in Busy mode."); return;
    }
    Notification.Action action = null;
    RemoteInput input = null;
    if (n.actions != null) outer: for (Notification.Action a : n.actions) {
      if (
        a.actionIntent == null ||
        !"com.whatsapp.w4b".equals(a.actionIntent.getCreatorPackage()) ||
        a.getRemoteInputs() == null
      ) continue;
      for (RemoteInput r : a.getRemoteInputs())
        if (r.getAllowFreeFormInput()) {
          action = a;
          input = r;
          break outer;
        }
    }
    if (input == null) { s.status("Message seen; notification has no usable WhatsApp Reply action."); return; }
    // Bound token mint attempts, including unsuccessful network and PendingIntent sends.
    String day = LocalDate.now().toString();
    int count = day.equals(s.get("day")) ? s.p.getInt("count", 0) : 0;
    if (count >= 20) {
      s.status("Daily limit reached (20 invite attempts).");
      return;
    }
    if (now - s.p.getLong("attempt." + id, 0) < 120000) return; // Preserve the last provider error.
    s.p
      .edit()
      .putLong("attempt." + id, now)
      .putString("day", day)
      .putInt("count", count + 1)
      .apply();
    s.status("Message seen; requesting Gemini invite…");
    String link = s.invite(mode);
    if (!s.enabled() || !mode.equals(s.mode())) return;
    Bundle result = new Bundle();
    result.putCharSequence(
      input.getResultKey(),
      "Hi, I’m Mimi, an AI receptionist. If you’d like to leave a message by speaking with me, open this private, one-use link within 4 minutes. Please don’t forward it.\n" +
        link
    );
    Intent fill = new Intent();
    RemoteInput.addResultsToIntent(new RemoteInput[] { input }, fill, result);
    action.actionIntent.send(this, 0, fill);
    s.p
      .edit()
      .putLong("sent." + id, now)
      .apply();
    s.status(
      "Invite handed to WhatsApp · " +
        java.time.LocalTime.now().withSecond(0).withNano(0)
    );
    // Retain rate-limit identifiers only while useful.
    android.content.SharedPreferences.Editor prune = s.p.edit();
    for (Map.Entry<String, ?> entry : s.p.getAll().entrySet())
      if (
        (entry.getKey().startsWith("sent.") ||
          entry.getKey().startsWith("attempt.")) &&
        entry.getValue() instanceof Long &&
        now - (Long) entry.getValue() > 86400000L
      ) prune.remove(entry.getKey());
    prune.apply();
  }
}
