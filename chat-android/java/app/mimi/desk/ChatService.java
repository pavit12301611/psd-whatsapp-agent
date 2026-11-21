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
    getSystemService(NotificationManager.class).createNotificationChannel(
      new NotificationChannel(
        "mimi-chat",
        "Linked chat agent",
        NotificationManager.IMPORTANCE_LOW
      )
    );
  }

  private Notification notification() {
    PendingIntent open = PendingIntent.getActivity(
      this,
      0,
      new Intent(this, ChatDesk.class),
      PendingIntent.FLAG_IMMUTABLE
    );
    PendingIntent stop = PendingIntent.getService(
      this,
      1,
      new Intent(this, ChatService.class).setAction("stop"),
      PendingIntent.FLAG_IMMUTABLE
    );
    Notification.Builder builder = new Notification.Builder(this, "mimi-chat")
      .setSmallIcon(getApplicationInfo().icon)
      .setContentTitle("Mimi · Linked chat")
      .setContentText(
        mode.equals("off")
          ? "Auto-replies paused"
          : "Answering incoming direct texts · " + mode
      )
      .setContentIntent(open)
      .setOngoing(true);
    if (!mode.equals("off")) {
      PendingIntent pause = PendingIntent.getService(
        this,
        2,
        new Intent(this, ChatService.class)
          .setAction("mode")
          .putExtra("value", "off"),
        PendingIntent.FLAG_IMMUTABLE
      );
      builder.addAction(
        new Notification.Action.Builder(null, "Pause replies", pause).build()
      );
    }
    if (sleepUntil > 0 && mode.equals("sleeping")) builder
      .setWhen(sleepUntil)
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setShowWhen(true);
    return builder
      .addAction(
        new Notification.Action.Builder(null, "Stop agent", stop).build()
      )
      .build();
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
    } else if ("mode".equals(action) && linked) {
      String m = intent.getStringExtra("value");
      if (m != null && m.matches("off|busy|sleeping|driving")) try {
        long ms = intent.getLongExtra("durationMs", 0);
        write(
          Store.obj(
            "type",
            "mode",
            "value",
            m,
            "calls",
            CallRules.runtime(new Store(this)),
            "durationMs",
            ms,
            "language",
            new Store(this).p.getString("chat.language", "en")
          )
        );
      } catch (Exception e) {
        status = "Could not change mode. Agent stopped.";
        stopSelf();
      }
    } else if ("stop".equals(action)) {
      status = "Stopped. No auto-replies.";
      stopSelf();
    }
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
    sleepUntil = 0;
    linked = false;
    mode = "off";
    code = "";
    if (pipe != null) try {
      pipe.close();
    } catch (IOException ignored) {}
    if (child != null) child.destroyForcibly();
    running = false;
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }
}
