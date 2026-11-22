package app.mimi.desk;

import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Numbers that always reach you: Mimi never auto-replies to them, she pings you instead. */
final class Vip {

  static final String KEY = "vip.v1";

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
      String n = a.optJSONObject(i) == null ? "" : a.optJSONObject(i).optString("n");
      if (n.matches("[1-9][0-9]{6,14}")) out.add(n);
    }
    return out;
  }

  static boolean has(SharedPreferences p, String number) {
    return numbers(p).contains(number);
  }

  static synchronized void add(SharedPreferences p, String number, String name) {
    String n = number.replaceAll("[ ()+-]", ""), clean = name.replaceAll("[\\u0000-\\u001f\\s]+", " ").trim();
    if (!n.matches("[1-9][0-9]{6,14}")) throw new IllegalArgumentException("Include the country code, e.g. 9198…");
    if (clean.length() > 40) throw new IllegalArgumentException("Name is too long.");
    try {
      JSONArray out = new JSONArray();
      JSONArray old = list(p);
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.optJSONObject(i);
        if (o != null && !n.equals(o.optString("n"))) out.put(o);
      }
      out.put(Store.obj("n", n, "name", clean.isEmpty() ? "Priority contact" : clean));
      if (out.length() > 40) throw new IllegalArgumentException("Keep the list under 40 numbers.");
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

  static String label(SharedPreferences p, String number) {
    JSONArray a = list(p);
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && number.equals(o.optString("n"))) return o.optString("name", "Priority contact");
    }
    return "";
  }

  static void forget(SharedPreferences p) {
    p.edit().remove(KEY).apply();
  }

  private static AlertDialog open;

  private static void clear() {
    if (open != null) open.dismiss();
    open = null;
  }

  /** UI: who bypasses Mimi entirely and pings you instead. */
  static void edit(final ChatDesk a) {
    clear();
    final Store s = a.store;
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);
    a.text(panel, "These people skip Mimi’s auto-reply. You get a notification instead, even mid-block.", 13, a.muted, false);
    a.gap(panel, 8);
    JSONArray list = list(s.p);
    if (list.length() == 0) a.text(panel, "No priority contacts yet.", 13, a.muted, false);
    for (int i = 0; i < list.length(); i++) {
      final JSONObject o = list.optJSONObject(i);
      if (o == null) continue;
      LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      TextView label = new TextView(a);
      label.setText(o.optString("name") + "\n" + o.optString("n"));
      label.setTextSize(14);
      label.setTextColor(a.ink);
      row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      Button del = a.button(row, "Remove", () -> {});
      del.setTextSize(13);
      final String n = o.optString("n");
      del.setOnClickListener(v -> {
        remove(s.p, n);
        reopen(a);
      });
      panel.addView(row);
    }
    a.gap(panel, 12);
    a.text(panel, "Add a contact", 13, a.muted, true);
    final EditText name = PocketDialog.input(a);
    name.setHint("Name (e.g. Mummy)");
    name.setSingleLine();
    panel.addView(name);
    final EditText number = PocketDialog.input(a);
    number.setHint("+91 98…");
    number.setSingleLine();
    panel.addView(number);
    a.gap(panel, 8);
    a.text(panel, "Numbers only. Groups and calls are handled separately by Call Shield.", 12, a.muted, false);

    AlertDialog d = new PocketDialog(a, PocketDialog.Room.CONTACTS)
      .setTitle("Priority circle")
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
