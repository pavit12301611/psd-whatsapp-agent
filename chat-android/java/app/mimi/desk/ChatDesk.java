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
  TextView badge, activity, pair, scope, timer, briefing;
  Button link, forget;
  // Only validated, encrypted edits may wait here. Never place keys in savedInstanceState.
  SharedPreferences.Editor pendingConfig;

  @Override
  public Object onRetainNonConfigurationInstance() {
    return pendingConfig;
  }

  TextView signal, reasonLine;
  String paintedMode = null;
  final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefChange =
    (prefs, key) -> refreshSoon();

  void refreshSoon() {
    if (ChatService.observer != null) {
      ui.removeCallbacks(tick);
      ui.post(tick);
    }
  }

  Availability saved() {
    return Availability.get(
      store.p,
      store.get("chat.owner"),
      System.currentTimeMillis()
    );
  }

  android.graphics.drawable.Drawable surface(int fill, int border) {
    GradientDrawable shape = bg(fill, 14);
    shape.setStroke(dp(1), border);
    return new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x24745acf),
      shape,
      null
    );
  }

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
    b.setTextSize(15);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(48));
    b.setPadding(dp(10), dp(9), dp(10), dp(9));
    b.setBackground(surface(0xfff7f5fc, 0xffddd6ea));
    b.setStateListAnimator(null);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      l.getOrientation() == LinearLayout.VERTICAL ? -1 : -2,
      -2
    );
    params.setMargins(0, dp(3), 0, dp(3));
    l.addView(b, params);
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
    pendingConfig =
      (SharedPreferences.Editor) getLastNonConfigurationInstance();
    getWindow().setStatusBarColor(0xff493176);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xfff7f6fb);
    int width = getResources().getConfiguration().screenWidthDp;
    page = box(0xfff7f6fb, width < 360 ? 14 : 22);
    android.widget.FrameLayout.LayoutParams body =
      new android.widget.FrameLayout.LayoutParams(
        width > 680 ? dp(680) : -1,
        -2,
        Gravity.TOP | Gravity.CENTER_HORIZONTAL
      );
    scroll.addView(page, body);
    setContentView(scroll);
    text(page, "MIMI   /   PERSONAL WORKSPACE", 11, purple, true);
    gap(page, 5);
    text(page, "Chat desk", 35, ink, true);
    text(page, "Mimi — Pavit's own AI bot", 15, muted, false);
    gap(page, 20);
    LinearLayout hero = box(0xff493176, 20);
    page.addView(hero);
    GradientDrawable wash = new GradientDrawable(
      GradientDrawable.Orientation.TL_BR,
      new int[] { 0xff372452, 0xff62449a }
    );
    wash.setCornerRadius(dp(21));
    hero.setBackground(wash);
    signal = text(hero, "YOUR STATUS   /   OFFLINE", 12, 0xffdef6ac, true);
    signal.setLetterSpacing(.06f);
    gap(hero, 10);
    badge = text(hero, "Paused", 27, Color.WHITE, true);
    gap(hero, 5);
    reasonLine = text(hero, "", 15, Color.WHITE, false);
    scope = text(hero, "", 14, 0xffe2d9f2, false);
    timer = text(hero, "", 15, 0xffd9fab0, true);
    gap(page, 18);
    text(page, "Availability", 22, ink, true);
    text(
      page,
      "Your selection stays on this phone, even offline. Mimi uses it when WhatsApp is linked.",
      14,
      muted,
      false
    );
    gap(page, 8);
    float font = getResources().getConfiguration().fontScale;
    int cols = width / font < 330 ? 1 : width / font >= 620 ? 4 : 2;
    for (int i = 0; i < 4; ) {
      LinearLayout row = new LinearLayout(this);
      page.addView(row);
      for (int c = 0; c < cols && i < 4; c++, i++) {
        final int idx = i;
        Button tile = (modes[i] = button(
          row,
          new String[] { "Available / Pause", "Busy", "Sleeping", "Driving" }[
            i
          ],
          () -> select(values[idx])
        ));
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
          0,
          -2,
          1
        );
        cell.setMargins(dp(3), dp(3), dp(3), dp(3));
        tile.setLayoutParams(cell);
        tile.setMinHeight(dp(54));
      }
    }
    gap(page, 18);
    LinearLayout personal = box(0xffeee9f8, 16);
    page.addView(personal);
    text(personal, "REPLY STUDIO", 11, purple, true);
    gap(personal, 6);
    briefing = text(personal, "", 15, ink, false);
    button(personal, "Preview greeting & language", this::studio);
    button(personal, "Call Shield & priority callers", () ->
      CallRules.edit(this)
    );
    button(personal, "Follow-up inbox", () -> {
      String owner = store.get("chat.owner");
      if (owner.isEmpty()) {
        Toast.makeText(
          this,
          "Set up your WhatsApp number first.",
          Toast.LENGTH_SHORT
        ).show();
        return;
      }
      new FollowUpDesk(this, owner).show();
    });
    gap(page, 14);
    LinearLayout card = box(Color.WHITE, 16);
    page.addView(card);
    text(card, "CONNECTION", 11, purple, true);
    gap(card, 6);
    activity = text(card, "", 15, ink, false);
    pair = text(card, "", 28, purple, true);
    gap(card, 8);
    link = button(card, "Connect WhatsApp", this::connectOrStop);
    button(card, "Account & AI settings", this::setup);
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
    if (store.get("chat.owner").isEmpty()) {
      Toast.makeText(
        this,
        "Set up your Business number first.",
        Toast.LENGTH_SHORT
      ).show();
      setup();
      return;
    }
    if (m.equals("busy")) {
      busyPicker();
      return;
    }
    if (m.equals("sleeping")) {
      sleepPicker();
      return;
    }
    activate(m, 0);
  }

  void activate(String m, long durationMs) {
    saveMode(m, durationMs, "", false);
  }

  void saveMode(String m, long durationMs, String why, boolean share) {
    Availability.set(
      store.p,
      store.get("chat.owner"),
      m,
      durationMs,
      why,
      share,
      System.currentTimeMillis()
    );
    if (ChatService.running) startService(
      new Intent(this, ChatService.class).setAction("sync")
    );
    refreshSoon();
  }

  void busyPicker() {
    Availability previous = saved();
    ScrollView sc = new ScrollView(this);
    LinearLayout panel = box(Color.WHITE, 18);
    sc.addView(panel);
    text(panel, "Busy reason · optional", 16, ink, true);
    EditText why = new EditText(this);
    why.setSingleLine();
    why.setHint("For example: In a meeting until 4 pm");
    why.setFilters(new android.text.InputFilter[] {
      new android.text.InputFilter.LengthFilter(120),
    });
    why.setText(previous.reason);
    panel.addView(why);
    CheckBox share = new CheckBox(this);
    share.setText("Include reason in Busy greetings and Call Shield texts");
    share.setChecked(previous.share);
    panel.addView(share);
    text(
      panel,
      "Unchecked: only you see the reason. Leave it blank for the usual Busy message.",
      13,
      muted,
      false
    );
    AlertDialog d = new AlertDialog.Builder(this)
      .setTitle("Set Busy status")
      .setView(sc)
      .setPositiveButton("Save Busy", (x, k) ->
        saveMode("busy", 0, why.getText().toString(), share.isChecked())
      )
      .setNegativeButton("Cancel", null)
      .create();
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.show();
  }

  void sleepPicker() {
    String[] choices = {
      "Power nap · 20 minutes",
      "1 hour",
      "6 hours",
      "8 hours",
      "Custom minutes…",
      "Until a wake-up time…",
      "Until I pause manually",
    };
    new AlertDialog.Builder(this)
      .setTitle("Sleep timer")
      .setItems(choices, (d, i) -> {
        if (i < 4) {
          activate("sleeping", new int[] { 20, 60, 360, 480 }[i] * 60000L);
          return;
        }
        if (i == 6) {
          activate("sleeping", 0);
          return;
        }
        if (i == 5) {
          java.util.Calendar now = java.util.Calendar.getInstance();
          new TimePickerDialog(
            this,
            (view, h, min) -> {
              java.util.Calendar at = java.util.Calendar.getInstance();
              at.set(java.util.Calendar.HOUR_OF_DAY, h);
              at.set(java.util.Calendar.MINUTE, min);
              at.set(java.util.Calendar.SECOND, 0);
              at.set(java.util.Calendar.MILLISECOND, 0);
              long t = System.currentTimeMillis();
              if (at.getTimeInMillis() <= t) at.add(
                java.util.Calendar.DAY_OF_MONTH,
                1
              );
              activate("sleeping", at.getTimeInMillis() - t);
            },
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(this)
          ).show();
          return;
        }
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 to 1440 minutes");
        input.setSingleLine();
        AlertDialog custom = new AlertDialog.Builder(this)
          .setTitle("Sleep for how long?")
          .setView(input)
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Start timer", null)
          .create();
        custom.show();
        custom.getButton(-1).setOnClickListener(v -> {
          int minutes = 0;
          try {
            minutes = Integer.parseInt(input.getText().toString().trim());
          } catch (NumberFormatException ignored) {}
          if (minutes < 1 || minutes > 1440) {
            input.setError("Choose 1–1440 minutes");
            return;
          }
          custom.dismiss();
          activate("sleeping", minutes * 60000L);
        });
      })
      .setNegativeButton("Cancel", null)
      .show();
  }

  void studio() {
    try (
      java.io.InputStream in = getAssets().open("greetings.json");
      java.util.Scanner sc = new java.util.Scanner(in, "UTF-8")
    ) {
      org.json.JSONObject greetings = new org.json.JSONObject(
        sc.useDelimiter("\\A").next()
      );
      LinearLayout panel = box(Color.WHITE, 16);
      Spinner language = new Spinner(this),
        mode = new Spinner(this);
      String[] languages = { "English", "Hinglish" },
        samples = { "Sleeping", "Busy", "Driving" };
      language.setAdapter(
        new ArrayAdapter<String>(
          this,
          android.R.layout.simple_spinner_dropdown_item,
          languages
        )
      );
      mode.setAdapter(
        new ArrayAdapter<String>(
          this,
          android.R.layout.simple_spinner_dropdown_item,
          samples
        )
      );
      text(panel, "Greeting language", 13, muted, true);
      panel.addView(language);
      text(panel, "Preview mode (does not activate replies)", 13, muted, true);
      panel.addView(mode);
      language.setSelection(
        "hi".equals(store.p.getString("chat.language", "en")) ? 1 : 0
      );
      mode.setSelection(
        ChatService.mode.equals("busy")
          ? 1
          : ChatService.mode.equals("driving")
            ? 2
            : 0
      );
      gap(panel, 10);
      TextView sample = text(panel, "", 16, ink, false);
      Runnable update = () -> {
        String lang = language.getSelectedItemPosition() == 1 ? "hi" : "en";
        Availability a = saved();
        sample.setText(
          greetings
            .optJSONObject(lang)
            .optString(
              samples[mode.getSelectedItemPosition()].toLowerCase(
                java.util.Locale.ROOT
              )
            ) +
            (mode.getSelectedItemPosition() == 1 && a.share
              ? (lang.equals("hi") ? "\nWajah: " : "\nReason: ") + a.reason
              : "")
        );
      };
      AdapterView.OnItemSelectedListener listen =
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
            update.run();
          }
        };
      language.setOnItemSelectedListener(listen);
      mode.setOnItemSelectedListener(listen);
      update.run();
      gap(panel, 10);
      text(
        panel,
        "Applies next time you select an away mode. AI opt-in instructions are added separately when enabled.",
        12,
        muted,
        false
      );
      ScrollView scroll = new ScrollView(this);
      scroll.addView(panel);
      new AlertDialog.Builder(this)
        .setTitle("Mimi’s reply preview")
        .setView(scroll)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save language", (d, k) ->
          store.p
            .edit()
            .putString(
              "chat.language",
              language.getSelectedItemPosition() == 1 ? "hi" : "en"
            )
            .apply()
        )
        .show();
    } catch (Exception e) {
      Toast.makeText(this, "Preview unavailable", Toast.LENGTH_SHORT).show();
    }
  }

  final Runnable tick = new Runnable() {
    public void run() {
      boolean live = ChatService.running,
        linked = ChatService.linked;
      Availability selected = saved();
      String m = selected.mode,
        code = ChatService.code;
      signal.setText(
        linked
          ? "YOUR STATUS   /   LINKED"
          : live
            ? "YOUR STATUS   /   CONNECTING"
            : "YOUR STATUS   /   OFFLINE"
      );
      badge.setText(m.equals("off") ? "Available · Paused" : "Pavit is " + m);
      reasonLine.setVisibility(
        selected.reason.isEmpty() ? View.GONE : View.VISIBLE
      );
      if (!selected.reason.isEmpty()) reasonLine.setText(
        (selected.share ? "Shared reason: " : "Private reason: ") +
          selected.reason
      );
      scope.setText(
        "all-direct".equals(store.p.getString("chat.scope", "all-direct"))
          ? "Audience: anyone who texts you directly."
          : "Audience: one test number only."
      );
      long until = selected.until;
      timer.setVisibility(
        until > 0 && m.equals("sleeping") ? View.VISIBLE : View.GONE
      );
      if (until > 0) {
        long secs = Math.max(
          0,
          (until - System.currentTimeMillis() + 999) / 1000
        );
        timer.setText(
          String.format(
            java.util.Locale.getDefault(),
            "Auto-pause in %d:%02d:%02d",
            secs / 3600,
            (secs / 60) % 60,
            secs % 60
          )
        );
      }
      briefing.setText(
        ChatService.notes + " notes left for Pavit this session"
      );
      activity.setText(ChatService.status);
      pair.setText(code);
      pair.setVisibility(code.isEmpty() ? View.GONE : View.VISIBLE);
      link.setText(live ? "Stop chat engine" : "Connect WhatsApp");
      // The editor remains accessible while connecting, linked, or offline.
      // Don't expose new account settings to a still-running worker for the old account.
      if (!live && pendingConfig != null) {
        SharedPreferences.Editor edits = pendingConfig;
        pendingConfig = null;
        edits.apply();
        launch();
        return;
      }
      forget.setVisibility(
        !live && !store.get("chat.owner").isEmpty() ? View.VISIBLE : View.GONE
      );
      if (!m.equals(paintedMode)) {
        paintedMode = m;
        for (int i = 0; i < 4; i++) {
          boolean active = m.equals(values[i]);
          Button tile = modes[i];
          tile.setSelected(active);
          tile.setTypeface(
            Typeface.DEFAULT,
            active ? Typeface.BOLD : Typeface.NORMAL
          );
          tile.setTextColor(active ? Color.WHITE : ink);
          tile.setBackground(
            surface(
              active ? 0xff604498 : Color.WHITE,
              active ? purple : 0xffded8e9
            )
          );
        }
      }
      ui.removeCallbacks(this);
      if (until > 0) ui.postDelayed(this, 1000);
    }
  };
  final Runnable updateDesk = () -> refreshSoon();

  protected void onResume() {
    super.onResume();
    ChatService.observer = updateDesk;
    store.p.registerOnSharedPreferenceChangeListener(prefChange);
    refreshSoon();
  }

  protected void onPause() {
    if (ChatService.observer == updateDesk) ChatService.observer = null;
    store.p.unregisterOnSharedPreferenceChangeListener(prefChange);
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
    if (pendingConfig != null) {
      Toast.makeText(
        this,
        "Applying settings. Please wait for reconnect.",
        Toast.LENGTH_SHORT
      ).show();
      return;
    }
    boolean editingLive = ChatService.running;
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
      editingLive
        ? "You can edit while connected. Saving changes briefly restarts the chat engine; Cancel keeps it running unchanged."
        : "Pairing grants a linked device account access. Never share its code.",
      13,
      muted,
      false
    );
    AlertDialog d = new AlertDialog.Builder(this)
      .setTitle("Account & AI settings")
      .setView(sc)
      .setNegativeButton("Cancel", null)
      .setPositiveButton(
        editingLive ? "Save & reconnect" : "Save & connect",
        null
      )
      .create();
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.show();
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
      String scope = t.isEmpty() ? "all-direct" : t;
      if (
        ChatService.running &&
        o.equals(store.get("chat.owner")) &&
        scope.equals(stored) &&
        p.equals(store.p.getString("chat.provider", "test")) &&
        (p.equals("test") || k.isEmpty())
      ) {
        d.dismiss();
        return; // Nothing to apply: leave the connection alone.
      }
      try {
        SharedPreferences.Editor e = store.p.edit();
        if (!p.equals("test") && !k.isEmpty()) e.putString(p, Store.enc(k));
        e.putString("chat.owner", o)
          .putString("chat.scope", scope)
          .putString("chat.provider", p);
        if (ChatService.running) {
          stopService(new Intent(this, ChatService.class));
          pendingConfig = e;
          refreshSoon(); // onDestroy also notifies us once the old engine is stopped.
        } else {
          e.apply();
          start();
        }
        d.dismiss();
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
        Availability.forget(store.p, owner);
        new FollowUps(store, owner).forget();
        store.p
          .edit()
          .remove(CallRules.key(owner))
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
