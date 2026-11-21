package app.mimi.desk;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;

public class Desk extends Activity {

  WebView w;
  Store s;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    s = new Store(this);
    getWindow().setStatusBarColor(0xff4d387d);
    w = new WebView(this);
    w.getSettings().setJavaScriptEnabled(true);
    w.getSettings().setAllowContentAccess(false);
    w.getSettings().setBlockNetworkLoads(true);
    w.getSettings().setAllowUniversalAccessFromFileURLs(false);
    w.setWebViewClient(
      new WebViewClient() {
        public boolean shouldOverrideUrlLoading(
          WebView v,
          WebResourceRequest r
        ) {
          return true;
        }
      }
    );
    w.addJavascriptInterface(new Bridge(), "MimiDesk");
    setContentView(w);
    w.loadUrl("file:///android_asset/index.html");
  }

  void refresh() {
    if (w != null) w.evaluateJavascript(
      "window.refreshDesk&&refreshDesk()",
      null
    );
  }

  public void onResume() {
    super.onResume();
    refresh();
  }

  public void onWindowFocusChanged(boolean b) {
    super.onWindowFocusChanged(b);
    if (b) refresh();
  }

  boolean access() {
    return getSystemService(
      NotificationManager.class
    ).isNotificationListenerAccessGranted(
      new ComponentName(this, Listener.class)
    );
  }

  boolean contacts() {
    return (
      checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
      PackageManager.PERMISSION_GRANTED
    );
  }

  void say(String t) {
    Toast.makeText(this, t, Toast.LENGTH_LONG).show();
  }

  class Bridge {

    @JavascriptInterface
    public String state() {
      try {
        return Store.obj(
          "mode",
          s.mode(),
          "enabled",
          s.enabled(),
          "access",
          access(),
          "bound", Listener.connected,
          "lastSeen", s.p.getLong("lastSeen", 0),
          "contacts",
          contacts(),
          "ready",
          s.ready(),
          "gemini",
          !s.get("gemini").isEmpty(),
          "groq",
          !s.get("groq").isEmpty(),
          "briefs",
          s.briefs(),
          "status",
          s.p.getString("status", "No invites sent yet.")
        ).toString();
      } catch (Exception e) {
        return "{}";
      }
    }

    @JavascriptInterface
    public void mode(String m) {
      if (m.matches("Available|Busy|Sleeping|Driving")) s.p
        .edit()
        .putString("mode", m)
        .apply();
    }

    @JavascriptInterface
    public void toggle() {
      if (
        !s.enabled() &&
        (!s.ready() ||
          !access() ||
          (s.mode().equals("Available") && !contacts()))
      ) {
        runOnUiThread(() ->
          new AlertDialog.Builder(Desk.this)
            .setTitle("Finish setup first")
            .setMessage(
              "Add a Gemini key, your WhatsApp number and the HTTPS caller page. Grant notification access; Available mode also needs contacts access."
            )
            .setPositiveButton("Connections", (d, k) ->
              w.evaluateJavascript("go(2)", null)
            )
            .setNegativeButton("Cancel", null)
            .show()
        );
        return;
      }
      s.p.edit().putBoolean("enabled", !s.enabled()).apply();
    }

    @JavascriptInterface
    public void action(String a, String arg) {
      runOnUiThread(() -> {
        switch (a) {
          case "access":
            startActivity(
              new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            );
            break;
          case "contacts":
            requestPermissions(
              new String[] { Manifest.permission.READ_CONTACTS },
              1
            );
            break;
          case "setup":
            setup();
            break;
          case "clear":
            new AlertDialog.Builder(Desk.this)
              .setMessage("Delete all local briefs?")
              .setNegativeButton("Cancel", null)
              .setPositiveButton("Delete", (d, k) -> {
                s.p.edit().remove("briefs").apply();
                refresh();
              })
              .show();
            break;
          case "polish":
            refine(arg);
        }
      });
    }
  }

  void refine(String id) {
    JSONArray notes = s.briefs();
    for (int i = 0; i < notes.length(); i++) {
      final JSONObject b = notes.optJSONObject(i);
      if (b == null || !id.equals(b.optString("time"))) continue;
      new AlertDialog.Builder(this)
        .setMessage("Send this brief to Groq for a clearer summary?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Send", (d, k) ->
          new Thread(() -> {
            try {
              String out = s.polish(b.getString("text"));
              runOnUiThread(() ->
                new AlertDialog.Builder(this)
                  .setTitle("Refined brief")
                  .setMessage(out)
                  .setPositiveButton("Done", null)
                  .show()
              );
            } catch (Exception e) {
              runOnUiThread(() ->
                say("Groq request failed. Check key, network and quota.")
              );
            }
          }).start()
        )
        .show();
      break;
    }
  }

  EditText field(LinearLayout l, String hint, String value, boolean pass) {
    TextView t = new TextView(this);
    t.setText(hint);
    l.addView(t);
    EditText e = new EditText(this);
    e.setSingleLine();
    e.setInputType(pass ? 129 : 17);
    e.setText(value);
    if (pass) e.setHint("Leave blank to keep existing");
    l.addView(e);
    return e;
  }

  void setup() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    l.setPadding(42, 12, 42, 12);
    scroll.addView(l);
    EditText g = field(l, "Gemini API key", "", true),
      q = field(l, "Groq API key (optional)", "", true),
      u = field(l, "Your trusted HTTPS caller page", s.get("page"), false),
      n = field(
        l,
        "Your WhatsApp number (+country code)",
        s.get("owner"),
        false
      ),
      m = field(
        l,
        "Gemini Live model ID",
        s.p.getString("model", Store.DEFAULT_MODEL),
        false
      );
    AlertDialog d = new AlertDialog.Builder(this)
      .setTitle("Connect Mimi")
      .setView(scroll)
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
      .setNeutralButton("Forget keys", (x, y) -> {
        s.p
          .edit()
          .remove("gemini")
          .remove("groq")
          .putBoolean("enabled", false)
          .apply();
        refresh();
      })
      .create();
    d.show();
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.getButton(-1).setOnClickListener(v -> {
      try {
        String page = u.getText().toString().trim(),
          num = n.getText().toString().trim().replaceFirst("^\\+", ""),
          model = m.getText().toString().trim();
        Uri uri = Uri.parse(page);
        if (
          !"https".equals(uri.getScheme()) ||
          uri.getHost() == null ||
          uri.getEncodedUserInfo() != null ||
          uri.getFragment() != null ||
          uri.getQuery() != null
        ) {
          u.setError("Use your own HTTPS page, without query or fragment");
          return;
        }
        if (!num.matches("[1-9][0-9]{6,14}")) {
          n.setError("Include country code");
          return;
        }
        if (!model.matches("[a-zA-Z0-9._-]{2,100}")) {
          m.setError("Enter a model ID");
          return;
        }
        SharedPreferences.Editor e = s.p.edit();
        for (Object[] pair : new Object[][] {
          { "gemini", g },
          { "groq", q },
        }) {
          String val = ((EditText) pair[1]).getText().toString().trim();
          if (!val.isEmpty()) e.putString((String) pair[0], Store.enc(val));
        }
        e.putString("page", page)
          .putString("owner", num)
          .putString("model", model)
          .apply();
        d.dismiss();
        refresh();
      } catch (Exception ex) {
        say("Could not securely save settings.");
      }
    });
  }
}
