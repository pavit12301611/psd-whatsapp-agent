package app.mimi.desk;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Messages Mimi sorted while you were away, each with a reply she drafted. */
final class Drafts {

  static final String KEY = "drafts.v1";

  static JSONArray list(SharedPreferences p) {
    try {
      return new JSONArray(p.getString(KEY, "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static int count(SharedPreferences p) {
    return list(p).length();
  }

  static synchronized void add(SharedPreferences p, JSONObject item) {
    if (item == null) return;
    try {
      String id = item.optString("id");
      if (id.isEmpty()) return;
      JSONArray out = new JSONArray();
      JSONArray old = list(p);
      for (int i = 0; i < old.length(); i++) {
        JSONObject o = old.optJSONObject(i);
        if (o != null && !id.equals(o.optString("id")) && !item.optString("jid").equals(o.optString("jid")))
          out.put(o);
      }
      out.put(
        Store.obj(
          "id", id,
          "jid", item.optString("jid"),
          "sender", item.optString("sender"),
          "text", item.optString("text"),
          "category", item.optString("category", "other"),
          "urgent", item.optBoolean("urgent", false),
          "draft", item.optString("draft"),
          "at", System.currentTimeMillis()
        )
      );
      while (out.length() > 60) out.remove(0);
      p.edit().putString(KEY, out.toString()).apply();
    } catch (JSONException ignored) {}
  }

  static synchronized void remove(SharedPreferences p, String id) {
    JSONArray out = new JSONArray();
    JSONArray old = list(p);
    for (int i = 0; i < old.length(); i++) {
      JSONObject o = old.optJSONObject(i);
      if (o != null && !id.equals(o.optString("id"))) out.put(o);
    }
    p.edit().putString(KEY, out.toString()).apply();
  }

  static void clear(SharedPreferences p) {
    p.edit().remove(KEY).apply();
  }

  private static String chip(JSONObject o) {
    String category = o.optString("category", "other");
    String name = category.substring(0, 1).toUpperCase(Locale.US) + category.substring(1);
    return (o.optBoolean("urgent") ? "⚡ URGENT · " : "") + name;
  }

  private static AlertDialog open;

  static void desk(final ChatDesk a) {
    if (open != null) {
      open.dismiss();
      open = null;
    }
    final Store s = a.store;
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);
    a.text(panel, "Every message Mimi sorted, with a reply she would send. Edit karo, phir Send.", 13, a.muted, false);
    a.gap(panel, 8);
    JSONArray items = list(s.p);
    if (items.length() == 0) a.text(panel, "Koi draft nahi. Jab koi message aayega, uska draft yahan aayega.", 13, a.muted, false);
    for (int i = items.length() - 1; i >= 0; i--) {
      final JSONObject o = items.optJSONObject(i);
      if (o == null) continue;
      LinearLayout card = a.box(o.optBoolean("urgent") ? 0xfffdecec : 0xfffaf6ff, 12);
      TextView head = a.text(card, chip(o) + "  ·  " + o.optString("sender"), 11, a.purple, true);
      head.setLetterSpacing(.06f);
      a.text(card, o.optString("text"), 14, a.ink, false);
      final EditText reply = PocketDialog.input(a);
      reply.setText(o.optString("draft"));
      reply.setMinLines(2);
      card.addView(reply);
      LinearLayout row = new LinearLayout(a);
      row.setOrientation(LinearLayout.HORIZONTAL);
      Button send = a.button(row, "Send", () -> {});
      send.setTextColor(0xff2f7d52);
      Button drop = a.button(row, "Dismiss", () -> {});
      send.setOnClickListener(v -> {
        String text = reply.getText().toString().trim();
        if (text.isEmpty()) {
          reply.setError("Likho kuch");
          return;
        }
        if (ChatService.running) {
          a.startService(
            new Intent(a, ChatService.class)
              .setAction("draft")
              .putExtra("to", o.optString("jid"))
              .putExtra("text", text)
              .putExtra("id", o.optString("id"))
          );
          Toast.makeText(a, "Draft send ho raha hai…", Toast.LENGTH_SHORT).show();
        } else {
          Toast.makeText(a, "Pehle chat engine connect karo.", Toast.LENGTH_SHORT).show();
        }
        remove(s.p, o.optString("id"));
        desk(a);
      });
      drop.setOnClickListener(v -> {
        remove(s.p, o.optString("id"));
        desk(a);
      });
      card.addView(row);
      panel.addView(card);
      a.gap(panel, 6);
    }
    AlertDialog d = new PocketDialog(a, PocketDialog.Room.STUDIO)
      .setTitle("Reply drafts")
      .setView(sc)
      .setNegativeButton("Close", null)
      .setNeutralButton("Clear all", null)
      .create();
    open = d;
    d.show();
    d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      clear(s.p);
      d.dismiss();
    });
  }
}
