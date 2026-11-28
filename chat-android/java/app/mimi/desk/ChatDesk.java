package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.webkit.*;
import android.net.Uri;
import java.io.File;

/** Chat-first launcher; the old voice notification service isn't registered in this APK. */
public class ChatDesk extends Activity {

  Store store;
  WebView desk;
  static final String ORIGIN = "https://pocket.mimi.invalid";
  static final String HOME = ORIGIN + "/index.html", ACTION = ORIGIN + "/action/";
  // Only validated, encrypted edits may wait here. Never place keys in savedInstanceState.
  SharedPreferences.Editor pendingConfig;

  @Override
  public Object onRetainNonConfigurationInstance() {
    return pendingConfig;
  }

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
    getWindow().setStatusBarColor(0xfffffefa);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    desk = new WebView(this);
    WebSettings web = desk.getSettings();
    web.setJavaScriptEnabled(true);
    web.setAllowFileAccess(false);
    web.setAllowContentAccess(false);
    web.setBlockNetworkLoads(true);
    web.setTextZoom(Math.round(100 * getResources().getConfiguration().fontScale));
    desk.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView w, WebResourceRequest r) {
        Uri uri = r.getUrl();
        String path = uri.getPath();
        if ("https".equals(uri.getScheme()) && "pocket.mimi.invalid".equals(uri.getAuthority()) && path != null &&
            path.matches("/(art/)?[a-z0-9-]+[.](html|js|css|svg|woff2)")) {
          String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "font/woff2";
          try { return new WebResourceResponse(mime, path.endsWith(".woff2") ? null : "UTF-8", getAssets().open("pocket" + path)); }
          catch (java.io.IOException ignored) { }
        }
        return new WebResourceResponse("text/plain", "UTF-8", null);
      }
      @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) {
        String url = r.getUrl().toString();
        if (url.startsWith(HOME + "#")) return false;
        if (r.isForMainFrame() && w.getUrl() != null && w.getUrl().split("#", 2)[0].equals(HOME) && url.startsWith(ACTION))
          deskAction(url.substring(ACTION.length()));
        return true;
      }
      @Override public void onPageFinished(WebView w, String url) { refreshSoon(); }
    });
    setContentView(desk);
    desk.loadUrl(HOME);
    if (Lock.enabled(store.p)) Lock.gate(this, this::unlocked);
    if (getIntent().getBooleanExtra("openRoutine", false)) desk.postDelayed(() ->
      RoutineDesk.show(this), 700);
    if (getIntent().getBooleanExtra("openInbox", false)) desk.postDelayed(() -> {
      String owner = store.get("chat.owner");
      if (!owner.isEmpty()) new FollowUpDesk(this, owner).show();
    }, 700);
  }

  void unlocked() {
    setContentView(desk);
    refreshSoon();
  }

  long pausedAt;

  void deskAction(String action) {
    switch (action) {
      case "off": case "busy": case "sleeping": case "driving": select(action); break;
      case "studio": studio(); break;
      case "shield": CallRules.edit(this); break;
      case "settings": setup(); break;
      case "connect": connectOrStop(); break;
      case "forget": if (!ChatService.running) forget(); break;
      case "routine": RoutineDesk.show(this); break;
      case "ask": RoutineDesk.ask(this, Routine.get(store.p)); break;
      case "quick-sleep":
        quick("sleeping", 8 * 3600000L, "So raha hoon — subah baat karte hain", "😴 Mimi on night duty for 8 hours");
        break;
      case "quick-focus":
        quick("busy", 0, "Focus time — thodi der mein reply", "🎧 Focus mode on. Family can still reach you.");
        break;
      case "quick-off":
        quick("off", 0, "", "✓ Mimi is free — replies paused");
        break;
      case "stats": Insights.show(this); break;
      case "vip": Vip.edit(this); break;
      case "ignore": Ignore.edit(this); break;
      case "family": Family.edit(this); break;
      case "drafts": Drafts.desk(this); break;
      case "lock": Lock.setup(this); break;
      case "inbox":
        String owner = store.get("chat.owner");
        if (!owner.isEmpty()) new FollowUpDesk(this, owner).show();
        else Toast.makeText(this, "Set up your WhatsApp number first.", Toast.LENGTH_SHORT).show();
        break;
    }
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

  void quick(String mode, long durationMs, String why, String toast) {
    if (store.get("chat.owner").isEmpty()) {
      Toast.makeText(this, "Set up your Business number first.", Toast.LENGTH_SHORT).show();
      setup();
      return;
    }
    saveMode(mode, durationMs, why, !why.isEmpty());
    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
  }

  void activate(String m, long durationMs) {
    saveMode(m, durationMs, "", false);
  }

  void saveMode(String m, long durationMs, String why, boolean share) {
    // A manual choice is a deliberate override; the timetable waits until resumed.
    Routine.pause(store.p, true);
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
    EditText why = PocketDialog.input(this);
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
    AlertDialog d = new PocketDialog(this, PocketDialog.Room.FOCUS)
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
    new PocketDialog(this, PocketDialog.Room.SLEEP)
      .setTitle("Sleep timer")
      .setAdapter(PocketDialog.rows(this, PocketDialog.Room.SLEEP, java.util.Arrays.asList(choices)), (d, i) -> {
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
          TimePickerDialog clock = new TimePickerDialog(
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
          );
          clock.setCustomTitle(PocketDialog.header(this, PocketDialog.Room.SLEEP, "Wake-up time"));
          PocketDialog.decorate(this, clock, PocketDialog.Room.SLEEP);
          clock.show();
          return;
        }
        EditText input = PocketDialog.input(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 to 1440 minutes");
        input.setSingleLine();
        LinearLayout minutesPanel = box(Color.WHITE, 16);
        minutesPanel.addView(input);
        AlertDialog custom = new PocketDialog(this, PocketDialog.Room.SLEEP)
          .setTitle("Sleep for how long?")
          .setView(minutesPanel)
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
      Spinner language = PocketDialog.menu(this),
        mode = PocketDialog.menu(this);
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
      sample.setBackground(bg(0xfff3eaf8, 16));
      sample.setPadding(dp(14), dp(12), dp(14), dp(12));
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
      new PocketDialog(this, PocketDialog.Room.STUDIO)
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
      long until = selected.until;
      long secs = Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
      String timer = until == 0 ? "" : String.format(java.util.Locale.getDefault(),
        "Auto-pause in %d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
      boolean otherApp = false;
      try {
        getPackageManager().getPackageInfo("app.mimi.desk.pocket", 0);
        otherApp = !"app.mimi.desk.pocket".equals(getPackageName());
      } catch (Exception ignored) {}
      Routine routine = Routine.get(store.p);
      boolean paused = Routine.paused(store.p);
      long nowMs = System.currentTimeMillis(), tracked = 0;
      org.json.JSONArray slots = new org.json.JSONArray();
      for (Routine.Slot slot : routine.today(nowMs)) {
        try {
          slots.put(Store.obj("name", slot.name, "start", slot.startText, "end", slot.endText,
            "now", slot.start <= nowMs && nowMs < slot.end, "mode", slot.mode));
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
      }
      Routine.Slot next = routine.next(nowMs);
      String nextUp = "";
      if (paused) nextUp = "Routine paused — tap Resume in My routine";
      else if (next != null) {
        long mins = Math.max(0, (next.start - nowMs) / 60000);
        nextUp = mins <= 180 ? "Next · " + next.name + " in " + mins + " min" : "Next · " + next.name + " at " + next.startText;
      }
      for (long[] value : TimeLog.day(store.p, Routine.date(java.util.Calendar.getInstance()), nowMs).values()) tracked += value[0];
      try {
        String state = Store.obj("mode", m, "network", linked ? "Linked" : live ? "Connecting" : "Offline",
          "plan", routine.planLine(nowMs), "routine", routine.statusLine(nowMs, paused),
          "slots", slots, "nextUp", nextUp, "otherApp", otherApp,
          "drafts", Drafts.count(store.p), "locked", Lock.enabled(store.p),
          "tracked", tracked <= 0 ? "" : Insights.duration(tracked), "routineOn", routine.enabled(), "paused", paused,
          "reason", selected.reason.isEmpty() ? "" : (selected.share ? "Shared reason: " : "Private reason: ") + selected.reason,
          "timer", timer, "notes", ChatService.notes, "code", code, "activity", ChatService.status,
          "tester", !"all-direct".equals(store.p.getString("chat.scope", "all-direct")),
          "canForget", !live && !store.get("chat.owner").isEmpty()).toString();
        desk.evaluateJavascript("window.renderDesk && window.renderDesk(" + state + ")", null);
      } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
      // The editor remains accessible while connecting, linked, or offline.
      // Don't expose new account settings to a still-running worker for the old account.
      if (!live && pendingConfig != null) {
        SharedPreferences.Editor edits = pendingConfig;
        pendingConfig = null;
        edits.apply();
        launch();
        return;
      }
      ui.removeCallbacks(this);
      if (until > 0) ui.postDelayed(this, 1000);
    }
  };
  final Runnable updateDesk = () -> refreshSoon();

  protected void onResume() {
    super.onResume();
    if (Lock.enabled(store.p) && pausedAt > 0 && System.currentTimeMillis() - pausedAt > 60000)
      Lock.gate(this, this::unlocked);
    pausedAt = 0;
    ChatService.observer = updateDesk;
    store.p.registerOnSharedPreferenceChangeListener(prefChange);
    refreshSoon();
  }

  protected void onPause() {
    pausedAt = System.currentTimeMillis();
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

  /** Asks for a coarse/fine fix once; the service only reads the newest stored location. */
  void requestLocation(Runnable granted, Runnable denied) {
    if (Build.VERSION.SDK_INT < 23 ||
        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
      granted.run();
      return;
    }
    locationGranted = granted;
    locationDenied = denied;
    requestPermissions(new String[] {
      android.Manifest.permission.ACCESS_FINE_LOCATION,
      android.Manifest.permission.ACCESS_COARSE_LOCATION
    }, 24);
  }

  Runnable locationGranted, locationDenied;

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
    if (code == 24) {
      boolean ok = g.length > 0 && g[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
      Runnable action = ok ? locationGranted : locationDenied;
      locationGranted = null;
      locationDenied = null;
      if (action != null) action.run();
    }
  }

  void launch() {
    startForegroundService(
      new Intent(this, ChatService.class).setAction("start")
    );
  }

  EditText field(LinearLayout l, String caption, String value, boolean key) {
    text(l, caption, 13, muted, true);
    EditText e = PocketDialog.input(this);
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
    Spinner prov = PocketDialog.menu(this);
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
    AlertDialog d = new PocketDialog(this, PocketDialog.Room.ACCOUNT)
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
    new PocketDialog(this, PocketDialog.Room.CONFIRM)
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
        store.p.edit().remove(Routine.KEY).remove(Routine.PAUSED).remove(Vip.KEY).remove(Ignore.KEY).remove(Family.KEY).remove(Family.SHARE).remove(Links.KEY).remove(TimeLog.KEY).apply();
        Drafts.clear(store.p);
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
