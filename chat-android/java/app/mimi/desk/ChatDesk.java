package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;

/** Chat-first launcher; the old voice notification service isn't registered in this APK. */
public class ChatDesk extends Activity {

  Store store;
  LinearLayout page;
  TextView badge, activity, pair, scope;
  Button link, settings, forget;
  String[] values = { "off", "busy", "sleeping", "driving" };
  Button[] modes = new Button[4];
  final Handler ui = new Handler(Looper.getMainLooper());
  int ink = 0xff30273f,
    purple = 0xff604498,
    muted = 0xff6e667a;

  int dp(int v) {
    return Math.round(getResources().getDisplayMetrics().density * v);
  }

  GradientDrawable bg(int color, int radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    return g;
  }

  TextView text(LinearLayout l, String txt, int sp, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(txt);
    t.setTextSize(sp);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    l.addView(t);
    return t;
  }

  void gap(LinearLayout l, int size) {
    l.addView(new Space(this), 1, dp(size));
  }

  Button button(LinearLayout l, String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(ink);
    b.setTextSize(14);
    b.setOnClickListener(v -> action.run());
    l.addView(b);
    return b;
  }

  LinearLayout box(int color, int pad) {
    LinearLayout b = new LinearLayout(this);
    b.setOrientation(1);
    b.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
    b.setBackground(bg(color, 18));
    return b;
  }

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    store = new Store(this);
    getWindow().setStatusBarColor(0xff493176);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xfff7f6fb);
    page = box(0xfff7f6fb, 22);
    scroll.addView(page);
    setContentView(scroll);
    text(page, "MIMI   /   PERSONAL WORKSPACE", 11, purple, true);
    gap(page, 5);
    text(page, "Chat desk", 35, ink, true);
    text(page, "Pavit’s WhatsApp Business receptionist", 15, muted, false);
    gap(page, 20);
    LinearLayout hero = box(0xff493176, 20);
    page.addView(hero);
    text(hero, "LINKED DEVICE  •  PHONE ONLY", 11, 0xffd9fab0, true);
    gap(hero, 10);
    badge = text(hero, "Paused", 27, Color.WHITE, true);
    gap(hero, 5);
    scope = text(hero, "", 14, 0xffe2d9f2, false);
    gap(page, 18);
    text(page, "Availability", 22, ink, true);
    text(
      page,
      "Pick an away mode to answer new personal texts. No broadcast is sent.",
      14,
      muted,
      false
    );
    gap(page, 8);
    for (int r = 0; r < 2; r++) {
      LinearLayout row = new LinearLayout(this);
      page.addView(row);
      for (int c = 0; c < 2; c++) {
        final int i = 2 * r + c;
        Button b = (modes[i] = button(
          row,
          new String[] { "Available / Pause", "Busy", "Sleeping", "Driving" }[
            i
          ],
          () -> select(values[i])
        ));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          0,
          dp(50),
          1
        );
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        b.setLayoutParams(lp);
      }
    }
    gap(page, 18);
    LinearLayout card = box(Color.WHITE, 16);
    page.addView(card);
    text(card, "CONNECTION", 11, purple, true);
    gap(card, 6);
    activity = text(card, "", 15, ink, false);
    pair = text(card, "", 28, purple, true);
    gap(card, 8);
    link = button(card, "Connect WhatsApp", this::connectOrStop);
    settings = button(card, "Account & AI settings", this::setup);
    forget = button(card, "Forget local session", this::forget);
    gap(page, 14);
    text(
      page,
      "Unofficial linked-device client. Test with a spare account first. WhatsApp can disconnect or restrict it. You can revoke access under Linked devices.",
      12,
      muted,
      false
    );
  }

  void select(String m) {
    if (ChatService.linked) startService(
      new Intent(this, ChatService.class).setAction("mode").putExtra("value", m)
    );
  }

  final Runnable tick = new Runnable() {
    public void run() {
      boolean live = ChatService.running,
        linked = ChatService.linked;
      String m = ChatService.mode,
        code = ChatService.code;
      badge.setText(
        !live
          ? "Desk is offline"
          : !linked
            ? "Connecting…"
            : m.equals("off")
              ? "Linked · Paused"
              : "Pavit is " + m
      );
      scope.setText(
        "all-direct".equals(store.p.getString("chat.scope", "all-direct"))
          ? "Audience: anyone who texts you directly."
          : "Audience: one test number only."
      );
      activity.setText(ChatService.status);
      pair.setText(code);
      pair.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
      link.setText(live ? "Stop chat engine" : "Connect WhatsApp");
      settings.setEnabled(!live);
      forget.setVisibility(
        !live && !store.get("chat.owner").isEmpty() ? View.VISIBLE : View.GONE
      );
      for (int i = 0; i < 4; i++) {
        modes[i].setEnabled(linked);
        modes[i].setBackground(
          bg(m.equals(values[i]) ? 0xffe1d5f4 : 0xffefedf3, 10)
        );
      }
      ui.postDelayed(this, 600);
    }
  };

  protected void onResume() {
    super.onResume();
    ui.post(tick);
  }

  protected void onPause() {
    ui.removeCallbacks(tick);
    super.onPause();
  }

  void connectOrStop() {
    if (ChatService.running) {
      startService(new Intent(this, ChatService.class).setAction("stop"));
      return;
    }
    if (store.get("chat.owner").isEmpty()) {
      setup();
      return;
    }
    start();
  }

  void start() {
    if (
      Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) requestPermissions(
      new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
      23
    );
    else launch();
  }

  public void onRequestPermissionsResult(int code, String[] p, int[] g) {
    super.onRequestPermissionsResult(code, p, g);
    if (code == 23) launch();
  }

  void launch() {
    startForegroundService(
      new Intent(this, ChatService.class).setAction("start")
    );
  }

  EditText field(LinearLayout l, String caption, String value, boolean key) {
    text(l, caption, 13, muted, true);
    EditText e = new EditText(this);
    e.setSingleLine();
    e.setInputType(key ? 129 : 3);
    e.setText(value);
    l.addView(e);
    gap(l, 8);
    return e;
  }

  String digits(EditText e) {
    return e
      .getText()
      .toString()
      .trim()
      .replaceAll("[ ()-]", "")
      .replaceFirst("^\\+", "");
  }

  void setup() {
    ScrollView sc = new ScrollView(this);
    LinearLayout l = box(Color.WHITE, 16);
    sc.addView(l);
    EditText owner = field(
      l,
      "Your WhatsApp Business number (+country code)",
      store.get("chat.owner"),
      false
    );
    String stored = store.p.getString("chat.scope", "all-direct");
    EditText tester = field(
      l,
      "Optional: limit to one tester’s number",
      stored.equals("all-direct") ? "" : stored,
      false
    );
    text(
      l,
      "Leave tester blank for all incoming 1:1 texts while away. Groups, calls and your own messages are excluded.",
      13,
      muted,
      false
    );
    gap(l, 12);
    String[] names = { "Message-taking · no key", "Gemini chat", "Groq chat" },
      provs = { "test", "gemini", "groq" };
    Spinner prov = new Spinner(this);
    prov.setAdapter(
      new ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        names
      )
    );
    l.addView(prov);
    prov.setSelection(
      Math.max(
        0,
        java.util.Arrays.asList(provs).indexOf(
          store.p.getString("chat.provider", "test")
        )
      )
    );
    EditText key = field(
      l,
      "Selected AI provider key (blank keeps existing)",
      "",
      true
    );
    text(
      l,
      "Pairing grants a linked device account access. Never share its code. Linking alone does not enable replies.",
      13,
      muted,
      false
    );
    AlertDialog d = new AlertDialog.Builder(this)
      .setTitle("Connect chat desk")
      .setView(sc)
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Save & connect", null)
      .create();
    d.show();
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.getButton(-1).setOnClickListener(v -> {
      String o = digits(owner),
        t = digits(tester),
        p = provs[prov.getSelectedItemPosition()],
        k = key.getText().toString().trim();
      if (!o.matches("[1-9][0-9]{6,14}")) {
        owner.setError("Include country code");
        return;
      }
      if (!t.isEmpty() && (!t.matches("[1-9][0-9]{6,14}") || t.equals(o))) {
        tester.setError("Enter a different number or leave blank");
        return;
      }
      if (!p.equals("test") && k.isEmpty() && store.get(p).isEmpty()) {
        key.setError("Add the selected provider key");
        return;
      }
      try {
        android.content.SharedPreferences.Editor e = store.p.edit();
        if (!p.equals("test") && !k.isEmpty()) e.putString(p, Store.enc(k));
        e.putString("chat.owner", o)
          .putString("chat.scope", t.isEmpty() ? "all-direct" : t)
          .putString("chat.provider", p)
          .apply();
        d.dismiss();
        start();
      } catch (Exception ex) {
        key.setError("Could not secure provider key");
      }
    });
  }

  static boolean remove(File f) {
    if (f.isDirectory()) {
      File[] files = f.listFiles();
      if (files == null) return false;
      for (File x : files) if (!remove(x)) return false;
    }
    return !f.exists() || f.delete();
  }

  void forget() {
    new AlertDialog.Builder(this)
      .setTitle("Forget this local account?")
      .setMessage(
        "Log this device out in WhatsApp Business → Linked devices as well. This removes its local session and saved AI keys; it does not erase WhatsApp conversations."
      )
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Forget", (d, k) -> {
        String owner = store.get("chat.owner");
        if (ChatService.running || !owner.matches("[1-9][0-9]{6,14}")) return;
        if (!remove(new File(new File(getFilesDir(), "sessions"), owner))) {
          ChatService.status =
            "Could not clear session. Use Android app storage settings.";
          return;
        }
        store.p
          .edit()
          .remove("chat.owner")
          .remove("chat.scope")
          .remove("chat.provider")
          .remove("gemini")
          .remove("groq")
          .apply();
        ChatService.status = "Local account forgotten.";
      })
      .show();
  }
}
