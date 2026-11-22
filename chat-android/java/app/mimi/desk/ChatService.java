package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.zip.*;
import org.json.*;

/** A foreground service owns one private child process. No localhost HTTP bridge. */
public class ChatService extends Service {

  static volatile boolean running, linked;
  static volatile Runnable observer;
  static final Handler main = new Handler(Looper.getMainLooper());

  static void changed() {
    main.post(() -> {
      Runnable r = observer;
      if (r != null) r.run();
    });
  }

  private synchronized void syncSelection() throws Exception {
    if (!linked || closed) return;
    Store s = new Store(this);
    Availability a = Availability.get(
      s.p,
      s.get("chat.owner"),
      System.currentTimeMillis()
    );
    write(
      Store.obj(
        "type",
        "mode",
        "value",
        a.mode,
        "until",
        a.until,
        "reason",
        a.publicReason(),
        "language",
        a.language,
        "calls",
        CallRules.runtime(s)
      )
    );
  }

  private final Handler routineTick = new Handler(Looper.getMainLooper());
  private static volatile String routineBlock;
  static volatile long sleepUntil;
  static volatile int notes;
  static volatile String mode = "off",
    code = "",
    status = "Connect your own WhatsApp Business account.";
  private volatile boolean closed;
  private java.lang.Process child;
  private BufferedWriter pipe;
  private static final int ID = 71;

  public IBinder onBind(Intent i) {
    return null;
  }

  public void onCreate() {
    super.onCreate();
    NotificationManager channels = getSystemService(NotificationManager.class);
    channels.createNotificationChannel(
      new NotificationChannel(
        "mimi-chat",
        "Linked chat agent",
        NotificationManager.IMPORTANCE_LOW
      )
    );
    channels.createNotificationChannel(
      new NotificationChannel(
        "mimi-digest",
        "Missed message digest",
        NotificationManager.IMPORTANCE_DEFAULT
      )
    );
    NotificationChannel priority = new NotificationChannel(
      "mimi-priority",
      "Priority contacts",
      NotificationManager.IMPORTANCE_HIGH
    );
    priority.enableVibration(true);
    channels.createNotificationChannel(priority);
  }

  private PendingIntent openDesk() {
    return PendingIntent.getActivity(
      this,
      0,
      new Intent(this, ChatDesk.class),
      PendingIntent.FLAG_IMMUTABLE
    );
  }

  /** A block ended: hand back what arrived, once. */
  private void digest(JSONObject summary) {
    String counts = summary.optString("text", "Messages waiting");
    StringBuilder body = new StringBuilder();
    JSONArray entries = summary.optJSONArray("entries");
    if (entries != null) for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.optJSONObject(i);
      if (e == null) continue;
      if (body.length() > 0) body.append(" · ");
      body.append(e.optString("sender")).append(": ").append(e.optString("text"));
      if (body.length() > 320) break;
    }
    Notification n = new Notification.Builder(this, "mimi-digest")
      .setSmallIcon(getResources().getIdentifier("pocket_note", "drawable", getPackageName()))
      .setContentTitle("Mimi · " + counts)
      .setContentText(body.length() == 0 ? "Open Mimi to see your notes." : body.toString())
      .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
      .setAutoCancel(true)
      .setContentIntent(
        PendingIntent.getActivity(
          this,
          4,
          new Intent(this, ChatDesk.class).putExtra("openInbox", true),
          PendingIntent.FLAG_IMMUTABLE
        )
      )
      .build();
    getSystemService(NotificationManager.class).notify(72, n);
    status = counts + ". Tap the notification for the summary.";
  }

  /** A priority contact wrote; Mimi never answered on your behalf. */
  private void priority(JSONObject message) {
    String sender = message.optString("sender", "Priority contact");
    String text = message.optString("text", "");
    Notification n = new Notification.Builder(this, "mimi-priority")
      .setSmallIcon(getResources().getIdentifier("pocket_note", "drawable", getPackageName()))
      .setContentTitle((message.optBoolean("urgent") ? "Urgent · " : "Priority · ") + sender)
      .setContentText(text)
      .setStyle(new Notification.BigTextStyle().bigText(text))
      .setAutoCancel(true)
      .setContentIntent(openDesk())
      .build();
    getSystemService(NotificationManager.class).notify(73, n);
    status = (message.optBoolean("urgent") ? "Urgent message from " : "Priority message from ") + sender + ".";
  }

  private void sendRoutine() {
    try {
      Store s = new Store(this);
      write(
        Store.obj(
          "type",
          "routine",
          "vip",
          new JSONArray(Vip.numbers(s.p)),
          "digest",
          true
        )
      );
    } catch (Exception ignored) {}
  }

  /** Native owns the weekly timetable so switching never depends on the socket. */
  private void applyRoutine(long now) {
    Store s = new Store(this);
    if (s.get("chat.owner").isEmpty()) return;
    Routine routine = Routine.get(s.p);
    String id = null;
    Routine.Slot slot = null;
    if (routine.enabled() && !Routine.paused(s.p)) {
      slot = routine.active(now);
      id = slot == null ? null : slot.id;
    }
    if (id == null ? routineBlock == null : id.equals(routineBlock)) return;
    routineBlock = id;
    TimeLog.mark(s.p, id, slot == null ? "" : slot.name, now);
    try {
      if (slot == null) {
        Availability.set(s.p, s.get("chat.owner"), "off", 0, "", false, now);
      } else {
        long left = slot.mode.equals("sleeping") ? Math.max(60000, slot.end - now) : 0;
        Availability.set(s.p, s.get("chat.owner"), slot.mode, left, slot.reasonText(), true, now);
      }
      syncSelection();
      changed();
    } catch (Exception e) {
      status = "Could not apply your routine. Check the block times.";
    }
  }

  private void scheduleRoutine() {
    routineTick.removeCallbacksAndMessages(null);
    routineTick.post(new Runnable() {
      public void run() {
        if (closed) return;
        applyRoutine(System.currentTimeMillis());
        try {
          // Keeps "Next · Study in 32 min" and the live countdown current without opening the app.
          getSystemService(NotificationManager.class).notify(ID, notification());
        } catch (Exception ignored) {}
        routineTick.postDelayed(this, 30000); // 30s is precise enough for HH:MM blocks
      }
    });
  }

  /** "in 32 min", "in 1h 10m" or "at 7:00 am" — readable at a glance. */
  private static String when(Routine.Slot slot, long now) {
    long mins = (slot.start - now) / 60000;
    if (mins <= 0) return "now";
    if (mins < 60) return "in " + mins + " min";
    if (mins < 20 * 60) return "in " + (mins / 60) + "h " + (mins % 60) + "m";
    return "at " + slot.startText;
  }

  private Notification notification() {
    PendingIntent open = openDesk();
    PendingIntent setup = PendingIntent.getActivity(
      this,
      5,
      new Intent(this, ChatDesk.class).putExtra("openRoutine", true),
      PendingIntent.FLAG_IMMUTABLE
    );
    PendingIntent stop = PendingIntent.getService(
      this,
      1,
      new Intent(this, ChatService.class).setAction("stop"),
      PendingIntent.FLAG_IMMUTABLE
    );
    Store s = new Store(this);
    Routine routine = Routine.get(s.p);
    long now = System.currentTimeMillis();
    boolean paused = Routine.paused(s.p), configured = !s.get("chat.owner").isEmpty();
    Routine.Slot live = configured && routine.enabled() ? routine.active(now) : null,
      next = configured ? routine.next(now) : null;
    String nextLine = next == null
      ? (configured ? "No more blocks scheduled" : "Add your routine to plan the day")
      : "Next · " + next.name + " " + when(next, now);
    String title = !mode.equals("off") && live != null
      ? "Mimi · " + live.name
      : mode.equals("off") ? "Mimi · Replies paused" : "Mimi · " + mode;
    String text;
    if (paused && configured) text = "Routine paused · " + nextLine;
    else if (live != null && !mode.equals("off")) text = live.name + " till " + live.endText + " · " + nextLine;
    else if (connected()) text = nextLine;
    else text = "Connecting… " + nextLine;
    long countdown = 0;
    if (mode.equals("sleeping") && sleepUntil > 0) countdown = sleepUntil;
    else if (next != null && next.start > now && next.start - now < 86400000L) countdown = next.start;
    Notification.Builder builder = new Notification.Builder(this, "mimi-chat")
      .setSmallIcon(getResources().getIdentifier("pocket_note", "drawable", getPackageName()))
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(new Notification.BigTextStyle().bigText(text))
      .setContentIntent(open)
      .setOngoing(true);
    if (countdown > 0) builder
      .setWhen(countdown)
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setShowWhen(true)
      .setSubText(mode.equals("sleeping") && sleepUntil > 0 ? "wake-up" : "next block");
    if (!mode.equals("off")) {
      PendingIntent pause = PendingIntent.getService(
        this,
        2,
        new Intent(this, ChatService.class).setAction("mode").putExtra("value", "off"),
        PendingIntent.FLAG_IMMUTABLE
      );
      builder.addAction(new Notification.Action.Builder(null, "Pause replies", pause).build());
    }
    builder.addAction(new Notification.Action.Builder(null, "Routine", setup).build());
    return builder
      .addAction(new Notification.Action.Builder(null, "Stop agent", stop).build())
      .build();
  }

  private boolean connected() {
    return linked;
  }

  public int onStartCommand(Intent intent, int flags, int id) {
    String action = intent == null ? "stop" : intent.getAction();
    if ("start".equals(action) && !running) {
      running = true;
      notes = 0;
      sleepUntil = 0;
      linked = false;
      code = "";
      mode = "off";
      status = "Preparing chat engine on this phone…";
      startForeground(ID, notification());
      scheduleRoutine();
      new Thread(this::launch, "Mimi-chat").start();
    } else if ("calls".equals(action) && linked) {
      try {
        write(
          Store.obj(
            "type",
            "calls",
            "calls",
            CallRules.runtime(new Store(this))
          )
        );
      } catch (Exception e) {
        status = "Cannot update priority rules; agent stopped.";
        stopSelf();
      }
    } else if ("routine".equals(action)) {
      try {
        sendRoutine();
        Availability current = Availability.get(
          new Store(this).p,
          new Store(this).get("chat.owner"),
          System.currentTimeMillis()
        );
        routineBlock = current.mode.equals("off") ? null : current.mode;
        applyRoutine(System.currentTimeMillis());
        syncSelection();
      } catch (Exception e) {
        status = "Could not sync your routine.";
      }
    } else if ("sync".equals(action) || "mode".equals(action)) {
      try {
        // Notification Pause is also a durable user selection.
        if (
          "mode".equals(action) && "off".equals(intent.getStringExtra("value"))
        ) {
          Store s = new Store(this);
          Routine.pause(s.p, true); // an explicit pause wins over the timetable
          Availability.set(
            s.p,
            s.get("chat.owner"),
            "off",
            0,
            "",
            false,
            System.currentTimeMillis()
          );
        }
        syncSelection();
      } catch (Exception e) {
        status = "Could not apply saved selection. Agent stopped.";
        stopSelf();
      }
    } else if ("stop".equals(action)) {
      status = "Stopped. No auto-replies.";
      stopSelf();
    }
    changed();
    return START_NOT_STICKY;
  }

  private void unpack(File dir) throws IOException {
    File marker = new File(dir, ".complete");
    if (marker.isFile()) return;
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException();
    String base = dir.getCanonicalPath() + File.separator;
    try (
      ZipInputStream z = new ZipInputStream(
        new BufferedInputStream(getAssets().open("chat-runtime.zip"))
      )
    ) {
      ZipEntry e;
      byte[] buffer = new byte[32768];
      while ((e = z.getNextEntry()) != null) {
        if (closed) throw new IOException();
        File f = new File(dir, e.getName());
        if (!f.getCanonicalPath().startsWith(base)) throw new IOException();
        if (e.isDirectory()) {
          f.mkdirs();
          continue;
        }
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
          int n;
          while ((n = z.read(buffer)) != -1) out.write(buffer, 0, n);
        }
      }
    }
    marker.createNewFile();
  }

  synchronized void write(JSONObject m) throws IOException {
    if (pipe == null || closed) throw new IOException();
    pipe.write(m.toString());
    pipe.newLine();
    pipe.flush();
  }

  private void launch() {
    try {
      // Extract only JS/data to private storage. Executable stays in the signed APK's native library directory.
      File dir = new File(
        getCodeCacheDir(),
        "chat-" +
          getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime
      );
      unpack(dir);
      File bin = new File(
        getApplicationInfo().nativeLibraryDir,
        "libmimirun.so"
      );
      ProcessBuilder pb = new ProcessBuilder(
        bin.toString(),
        "run",
        "--no-install",
        "./mobile.mjs"
      ).directory(dir);
      pb.environment().put("HOME", getFilesDir().toString());
      pb.environment().put("TMPDIR", getCacheDir().toString());
      pb.environment().put("BUN_INSTALL_CACHE_DIR", getCacheDir().toString());
      final java.lang.Process p;
      synchronized (this) {
        if (closed) return;
        p = child = pb.start();
        pipe = new BufferedWriter(
          new OutputStreamWriter(p.getOutputStream(), "UTF-8")
        );
      }
      // Native/dependency output can contain sensitive session data. Never log or display raw output.
      new Thread(() -> {
        try (InputStream err = p.getErrorStream()) {
          byte[] b = new byte[2048];
          while (err.read(b) != -1) {}
        } catch (IOException ignored) {}
      }, "Mimi-stderr").start();
      Store s = new Store(this);
      FollowUps followUps = new FollowUps(s, s.get("chat.owner"));
      String provider = s.p.getString("chat.provider", "test");
      write(
        Store.obj(
          "type",
          "init",
          "owner",
          s.get("chat.owner"),
          "calls",
          CallRules.runtime(s),
          "scope",
          s.p.getString("chat.scope", "all-direct"),
          "provider",
          provider,
          "key",
          provider.equals("test") ? "" : s.secret(provider)
        )
      );
      sendRoutine();
      applyRoutine(System.currentTimeMillis());
      boolean reported = false;
      try (
        BufferedReader in = new BufferedReader(
          new InputStreamReader(p.getInputStream(), "UTF-8")
        )
      ) {
        String line;
        while (!closed && (line = in.readLine()) != null) {
          if (line.length() > 4608 || !line.startsWith("{")) continue;
          JSONObject event;
          try {
            event = new JSONObject(line);
          } catch (JSONException ignored) {
            continue;
          }
          String type = event.optString("type"),
            v = event.optString("value");
          synchronized (this) {
            if (closed) return;
            switch (type) {
              case "ready":
                status = "Connecting to WhatsApp…";
                break;
              case "code":
                if (v.matches("[A-Za-z0-9]{8}")) {
                  code = v.substring(0, 4) + "-" + v.substring(4);
                  status =
                    "Enter this private code in WhatsApp Business → Linked devices → Link a device → Link with phone number instead.";
                }
                break;
              case "linked":
                linked = true;
                mode = "off";
                sleepUntil = 0;
                code = "";
                status = v;
                syncSelection();
                break;
              case "offline":
                linked = false;
                mode = "off";
                sleepUntil = 0;
                code = "";
                status = v;
                break;
              case "mode":
                if (v.matches("off|busy|sleeping|driving")) {
                  sleepUntil = v.equals("sleeping")
                    ? event.optLong("until", 0)
                    : 0;
                  mode = v;
                  status = v.equals("off")
                    ? "Paused. No replies."
                    : "Listening for new direct texts.";
                }
                break;
              case "digest":
                JSONObject summary = event.optJSONObject("value");
                if (summary != null) digest(summary);
                break;
              case "priority":
                JSONObject priority = event.optJSONObject("value");
                if (priority != null) priority(priority);
                break;
              case "note":
                notes++;
                try {
                  followUps.add(event.optJSONObject("value"));
                } catch (Exception failed) {
                  status =
                    "WhatsApp note sent, but local follow-up storage is unavailable.";
                }
                break;
              case "status":
                status = v;
                break;
              case "error":
                reported = true;
                status = v;
                break;
              default:
                continue;
            }
            changed();
            if (
              type.equals("mode") ||
              type.equals("offline") ||
              type.equals("linked")
            ) getSystemService(NotificationManager.class).notify(
              ID,
              notification()
            );
          }
        }
      }
      int exit = p.waitFor();
      if (!closed && !reported) status =
        "Engine exited (" + exit + "). Reconnect to retry.";
    } catch (Exception e) {
      if (!closed) status =
        "Could not start chat engine. Check ARM64 support, storage and saved settings.";
    } finally {
      if (!closed) stopSelf();
    }
  }

  public synchronized void onDestroy() {
    closed = true;
    routineTick.removeCallbacksAndMessages(null);
    try {
      TimeLog.mark(new Store(this).p, null, "", System.currentTimeMillis());
    } catch (Exception ignored) {}
    routineBlock = null;
    sleepUntil = 0;
    linked = false;
    mode = "off";
    code = "";
    if (pipe != null) try {
      pipe.close();
    } catch (IOException ignored) {}
    if (child != null) child.destroyForcibly();
    running = false;
    changed();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
