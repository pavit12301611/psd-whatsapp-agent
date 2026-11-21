package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.util.TreeMap;
import org.json.*;

/** Keystore-encrypted per-owner policy. Only numbers (not display names) reach the engine. */
final class CallRules {

  static String key(String owner) {
    return "calls.v1." + owner;
  }

  static JSONObject read(Store s, String owner) throws Exception {
    String k = key(owner);
    return s.get(k).isEmpty()
      ? Store.obj("enabled", false, "people", new JSONObject())
      : new JSONObject(s.secret(k));
  }

  static JSONObject runtime(Store s) {
    try {
      JSONObject saved = read(s, s.get("chat.owner"));
      JSONObject people = saved.getJSONObject("people");
      JSONArray nums = people.names();
      return Store.obj(
        "enabled",
        saved.getBoolean("enabled"),
        "priority",
        nums == null ? new JSONArray() : nums
      );
    } catch (Exception e) {
      return new JSONObject();
    } // unreadable policy must never lead to a decline
  }

  static void edit(ChatDesk a) {
    String owner = a.store.get("chat.owner");
    if (owner.isEmpty()) {
      Toast.makeText(
        a,
        "Set up your Business number first.",
        Toast.LENGTH_SHORT
      ).show();
      return;
    }
    JSONObject saved;
    TreeMap<String, String> people = new TreeMap<>();
    try {
      saved = read(a.store, owner);
      JSONObject obj = saved.getJSONObject("people");
      java.util.Iterator<String> it = obj.keys();
      while (it.hasNext()) {
        String n = it.next();
        people.put(n, obj.getString(n));
      }
    } catch (Exception e) {
      Toast.makeText(
        a,
        "Cannot read the saved priority list. No changes made.",
        Toast.LENGTH_LONG
      ).show();
      return;
    }
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(0xffffffff, 18);
    sc.addView(panel);
    Switch on = new Switch(a);
    on.setText("Decline & text while away");
    on.setChecked(saved.optBoolean("enabled"));
    panel.addView(on);
    a.gap(panel, 8);
    a.text(
      panel,
      "Only WhatsApp Business 1:1 calls while Busy, Sleeping or Driving. Priority numbers and unidentified callers pass through. Available / Pause leaves every call alone.",
      14,
      a.muted,
      false
    );
    a.gap(panel, 12);
    a.text(panel, "Priority callers · always let ring", 16, a.ink, true);
    a.text(
      panel,
      "Enter the full international number. No contacts permission needed.",
      13,
      a.muted,
      false
    );
    EditText label = new EditText(a);
    label.setHint("Name (optional)");
    label.setSingleLine(true);
    panel.addView(label);
    EditText phone = new EditText(a);
    phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
    phone.setSingleLine(true);
    phone.setHint("+91 …");
    panel.addView(phone);
    LinearLayout list = a.box(0xfff8f6fc, 8);
    Runnable draw = () -> {
      list.removeAllViews();
      if (people.isEmpty()) a.text(
        list,
        "No priority callers yet.",
        14,
        a.muted,
        false
      );
      else for (java.util.Map.Entry<String, String> p : people.entrySet()) {
        String n = p.getKey();
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        list.addView(row);
        TextView t = a.text(
          row,
          (p.getValue().isEmpty() ? "" : p.getValue() + "\n") + "+" + n,
          14,
          a.ink,
          false
        );
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        a.button(row, "Remove", () -> {
          people.remove(n);
          list.post(() -> drawListRefresh(list));
        });
      }
    };
    list.setTag(draw);
    draw.run();
    a.button(panel, "Add priority caller", () -> {
      String raw = phone.getText().toString().trim().replaceAll("[ ()-]", "");
      if (!raw.matches("\\+?[1-9][0-9]{6,14}")) {
        phone.setError("Include country code, e.g. +91…");
        return;
      }
      String n = raw.replaceFirst("^\\+", "");
      if (people.containsKey(n)) {
        phone.setError("Already on your list");
        return;
      }
      if (people.size() >= 50) {
        phone.setError("Maximum 50 priority numbers");
        return;
      }
      String name = label.getText().toString().trim();
      if (name.length() > 80) {
        label.setError("Use a shorter name");
        return;
      }
      people.put(n, name);
      phone.setText("");
      label.setText("");
      draw.run();
    });
    panel.addView(list);
    a.gap(panel, 6);
    a.text(
      panel,
      "Tap Save to apply additions, removals and the switch. This does not override Android Do Not Disturb.",
      12,
      a.muted,
      false
    );
    AlertDialog d = new AlertDialog.Builder(a)
      .setTitle("Call Shield")
      .setView(sc)
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Save", null)
      .create();
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.show();
    d.getButton(-1).setOnClickListener(v -> {
      if (phone.length() > 0 || label.length() > 0) {
        phone.setError("Tap Add priority caller or clear the unfinished entry");
        return;
      }
      try {
        String enc = Store.enc(
          Store.obj(
            "enabled",
            on.isChecked(),
            "people",
            new JSONObject(people)
          ).toString()
        );
        a.store.p.edit().putString(key(owner), enc).apply();
        if (ChatService.running) a.startService(
          new Intent(a, ChatService.class).setAction("calls")
        );
        d.dismiss();
        Toast.makeText(
          a,
          "Call Shield settings saved.",
          Toast.LENGTH_SHORT
        ).show();
      } catch (Exception e) {
        Toast.makeText(
          a,
          "Couldn't save Call Shield settings.",
          Toast.LENGTH_LONG
        ).show();
      }
    });
  }

  private static void drawListRefresh(LinearLayout l) {
    ((Runnable) l.getTag()).run();
  }
}
