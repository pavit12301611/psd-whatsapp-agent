package app.mimi.desk;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Total silence: Mimi never replies to these numbers, never alerts and never lists them. */
final class Ignore {

  static final String KEY = "ignore.v1";

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

  static String label(SharedPreferences p, String number) {
    JSONArray a = list(p);
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && number.equals(o.optString("n"))) return o.optString("name", "");
    }
    return "";
  }

  static synchronized void add(SharedPreferences p, String number, String name) {
    String n = number.replaceAll("[ ()+-]", ""),
      clean = name.replaceAll("[\\u0000-\\u001f\\s]+", " ").trim();
    if (!n.matches("[1-9][0-9]{6,14}")) throw new IllegalArgumentException("Include the country code, e.g. 9198…");
    if (clean.length() > 40) throw new IllegalArgumentException("Name is too long.");
    try {
      JSONArray out = new JSONArray();
      JSONArray old = list(p);
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.optJSONObject(i);
        if (o != null && !n.equals(o.optString("n"))) out.put(o);
      }
      out.put(Store.obj("n", n, "name", clean));
      if (out.length() > 100) throw new IllegalArgumentException("Keep the ignore list under 100 numbers.");
      p.edit().putString(KEY, out.toString()).apply();
      if (Vip.has(p, n)) Vip.remove(p, n); // never both silent and priority
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
    p.edit().remove(KEY).apply();
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
    a.text(panel, "Black hole mode: no auto-reply, no notification, no digest — Mimi stays completely silent, even by mistake.", 13, a.muted, false);
    a.gap(panel, 8);
    JSONArray list = list(s.p);
    if (list.length() == 0) a.text(panel, "Nobody ignored yet.", 13, a.muted, false);
    for (int i = 0; i < list.length(); i++) {
      final JSONObject o = list.optJSONObject(i);
      if (o == null) continue;
      LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      TextView label = new TextView(a);
      String name = o.optString("name");
      label.setText((name.isEmpty() ? "Ignored number" : name) + "\n" + o.optString("n"));
      label.setTextSize(14);
      label.setTextColor(a.ink);
      row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      Button del = a.button(row, "Remove", () -> {});
      final String n = o.optString("n");
      del.setOnClickListener(v -> {
        remove(s.p, n);
        reopen(a);
      });
      panel.addView(row);
    }
    a.gap(panel, 12);
    a.text(panel, "Add a number to ignore", 13, a.muted, true);
    final EditText name = PocketDialog.input(a);
    name.setHint("Name (optional)");
    name.setSingleLine();
    panel.addView(name);
    final EditText number = PocketDialog.input(a);
    number.setHint("+91 98…");
    number.setSingleLine();
    panel.addView(number);
    a.gap(panel, 8);
    a.text(panel, "Calls from ignored numbers are left alone too; Call Shield only acts on its own list.", 12, a.muted, false);

    AlertDialog d = new PocketDialog(a, PocketDialog.Room.CONFIRM)
      .setTitle("Ignore list")
      .setView(sc)
      .setNegativeButton("Close", null)
      .setPositiveButton("Add", null)
      .create();
    open = d;
    d.show();
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      try {
        add(s.p, number.getText().toString(), name.getText().toString());
        if (ChatService.running) a.startService(new Intent(a, ChatService.class).setAction("routine"));
        d.dismiss();
        edit(a);
      } catch (IllegalArgumentException e) {
        number.setError(e.getMessage());
      }
    });
  }

  private static void reopen(ChatDesk a) {
    if (ChatService.running) a.startService(new Intent(a, ChatService.class).setAction("routine"));
    edit(a);
  }
}
