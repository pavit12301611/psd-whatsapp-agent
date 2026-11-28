package app.mimi.desk;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Family members: when they ask, Mimi can pin the owner's last known location. */
final class Family {

  static final String KEY = "family.v1", SHARE = "family.location";

  static JSONArray list(SharedPreferences p) {
    try {
      return new JSONArray(p.getString(KEY, "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static List<String> numbers(SharedPreferences p) {
    List<String> out = new ArrayList<String>();
    JSONArray a = list(p);
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      String n = o == null ? "" : o.optString("n");
      if (n.matches("[1-9][0-9]{6,14}")) out.add(n);
    }
    return out;
  }

  static boolean has(SharedPreferences p, String number) {
    return numbers(p).contains(number);
  }

  static boolean sharing(SharedPreferences p) {
    return p.getBoolean(SHARE, false) && !numbers(p).isEmpty();
  }

  static void share(SharedPreferences p, boolean value) {
    p.edit().putBoolean(SHARE, value).apply();
  }

  static synchronized void add(SharedPreferences p, String number, String name) {
    String n = number.replaceAll("[ ()+-]", ""),
      clean = name.replaceAll("[\\u0000-\\u001f\\s]+", " ").trim();
    if (!n.matches("[1-9][0-9]{6,14}")) throw new IllegalArgumentException("Include the country code, e.g. 9198…");
    try {
      JSONArray out = new JSONArray();
      JSONArray old = list(p);
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.optJSONObject(i);
        if (o != null && !n.equals(o.optString("n"))) out.put(o);
      }
      out.put(Store.obj("n", n, "name", clean));
      if (out.length() > 30) throw new IllegalArgumentException("Keep the family list under 30 numbers.");
      p.edit().putString(KEY, out.toString()).apply();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static synchronized void remove(SharedPreferences p, String number) {
    JSONArray out = new JSONArray();
    JSONArray old = list(p);
    for (int i = 0; i < old.length(); i++) {
      JSONObject o = old.optJSONObject(i);
      if (o != null && !number.equals(o.optString("n"))) out.put(o);
    }
    p.edit().putString(KEY, out.toString()).apply();
  }

  static void forget(SharedPreferences p) {
    p.edit().remove(KEY).remove(SHARE).apply();
  }

  private static AlertDialog open;

  private static void clear() {
    if (open != null) open.dismiss();
    open = null;
  }

  static void edit(final ChatDesk a) {
    clear();
    final Store s = a.store;
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);
    a.text(panel, "Family can ask “location bhejo” and Mimi sends your last known pin — day or night, whenever she is on duty.", 13, a.muted, false);
    a.gap(panel, 8);

    final Switch share = new Switch(a);
    share.setText("Share my location when family asks");
    share.setTextColor(a.ink);
    share.setChecked(sharing(s.p));
    share.setOnCheckedChangeListener((v, on) -> {
      if (on) {
        a.requestLocation(() -> {
          share(s.p, true);
          nudge(a);
          Toast.makeText(a, "Family location sharing on.", Toast.LENGTH_SHORT).show();
        }, () -> {
          share.setChecked(false);
          Toast.makeText(a, "Location permission needed for this feature.", Toast.LENGTH_LONG).show();
        });
      } else {
        share(s.p, false);
        nudge(a);
      }
    });
    panel.addView(share);
    a.text(panel, Locator.allowed(a) ? "Location permission: granted." : "Location permission: not granted yet.", 12, a.muted, false);
    a.gap(panel, 10);

    JSONArray list = list(s.p);
    if (list.length() == 0) a.text(panel, "No family members added.", 13, a.muted, false);
    for (int i = 0; i < list.length(); i++) {
      final JSONObject o = list.optJSONObject(i);
      if (o == null) continue;
      LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      TextView label = new TextView(a);
      label.setText(o.optString("name", "Family") + "\n" + o.optString("n"));
      label.setTextSize(14);
      label.setTextColor(a.ink);
      row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      Button del = a.button(row, "Remove", () -> {});
      final String n = o.optString("n");
      del.setOnClickListener(v -> {
        remove(s.p, n);
        if (ChatService.running) a.startService(new Intent(a, ChatService.class).setAction("routine"));
        edit(a);
      });
      panel.addView(row);
    }
    a.gap(panel, 12);
    a.text(panel, "Add a family member", 13, a.muted, true);
    final EditText name = PocketDialog.input(a);
    name.setHint("Name (e.g. Papa)");
    name.setSingleLine();
    panel.addView(name);
    final EditText number = PocketDialog.input(a);
    number.setHint("+91 98…");
    number.setSingleLine();
    panel.addView(number);
    a.gap(panel, 8);
    a.text(panel, "Only the newest fix on this phone is sent — Mimi never starts continuous tracking. Ignored numbers stay ignored even if they are family.", 12, a.muted, false);

    AlertDialog d = new PocketDialog(a, PocketDialog.Room.CONTACTS)
      .setTitle("Family & location")
      .setView(sc)
      .setNegativeButton("Close", null)
      .setPositiveButton("Add", null)
      .create();
    open = d;
    d.show();
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      try {
        add(s.p, number.getText().toString(), name.getText().toString());
        nudge(a);
        d.dismiss();
        edit(a);
      } catch (IllegalArgumentException e) {
        number.setError(e.getMessage());
      }
    });
  }

  private static void nudge(ChatDesk a) {
    if (ChatService.running) a.startService(new Intent(a, ChatService.class).setAction("routine"));
  }
}
