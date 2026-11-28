package app.mimi.desk;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Weekly timetable, holiday skip and the local "Ask Mimi" answer sheet. */
final class RoutineDesk {

  private static AlertDialog open;

  private static void clear() {
    if (open != null) open.dismiss();
    open = null;
  }

  static void show(final ChatDesk a) {
    clear();
    final Store s = a.store;
    final long now = System.currentTimeMillis();
    final Routine routine = Routine.get(s.p);
    final boolean paused = Routine.paused(s.p);
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);

    a.text(panel, routine.statusLine(now, paused), 15, a.ink, true);
    a.text(panel, routine.planLine(now), 13, a.muted, false);
    a.gap(panel, 10);

    CheckBox auto = new CheckBox(a);
    auto.setText("Follow this routine automatically");
    auto.setTextColor(a.ink);
    auto.setChecked(routine.enabled());
    auto.setOnCheckedChangeListener((v, on) -> {
      try {
        routine.doc.put("enabled", on);
        routine.save(s.p);
        nudge(a);
      } catch (JSONException ignored) {}
    });
    panel.addView(auto);

    if (paused) {
      Button resume = a.button(panel, "Resume routine now", () -> {
        Routine.pause(s.p, false);
        nudge(a);
        show(a);
      });
      resume.setTextColor(a.purple);
    }

    boolean holiday = routine.skipped(Routine.date(Calendar.getInstance()));
    a.button(panel, holiday ? "Remove today’s day off" : "Mark today as a day off", () -> {
      routine.skipToday(!holiday);
      routine.save(s.p);
      nudge(a);
      show(a);
    });
    a.gap(panel, 12);

    a.text(panel, "TODAY · tap to switch one block off for today only", 11, a.purple, true);
    String todayKey = Routine.date(Calendar.getInstance());
    List<Routine.Slot> today = routine.todayAll(now);
    if (today.isEmpty()) a.text(panel, "Nothing scheduled — Mimi stays off unless you pick a mode.", 13, a.muted, false);
    for (final Routine.Slot slot : today) {
      final boolean off = routine.blockSkipped(slot.id, todayKey);
      a.button(
        panel,
        (off ? "✕ " : "• ") + slot.startText + "–" + slot.endText + "  ·  " + slot.name + (off ? "  (off today)" : ""),
        () -> {
          routine.skipBlock(slot.id, todayKey, !off);
          routine.save(s.p);
          nudge(a);
          show(a);
        }
      );
    }
    a.gap(panel, 12);
    a.text(panel, "BLOCKS", 11, a.purple, true);
    if (routine.blocks().length() == 0)
      a.text(panel, "Add school, study, sleep or any block. Times are local to this phone.", 13, a.muted, false);
    for (int i = 0; i < routine.blocks().length(); i++) {
      final JSONObject b = routine.blocks().optJSONObject(i);
      if (b == null) continue;
      Button row = a.button(panel, b.optString("name") + "  ·  " + Routine.fmt(b.optString("start")) + "–" + Routine.fmt(b.optString("end")) + "\n" + Routine.repeat(b) + "  ·  " + Routine.kindName(b.optString("kind")), () -> {
        editor(a, b);
      });
      row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }
    a.gap(panel, 8);
    a.button(panel, "＋ Add a block", () -> editor(a, null));
    a.text(panel, "One-tap starters", 13, a.muted, true);
    a.button(panel, "🏫 School · Mon–Sat 8 am–2 pm", () -> preset(a, "School", "school", "08:00", "14:00", "School mein hoon, 2 baje ke baad free", new int[] { 1, 2, 3, 4, 5, 6 }));
    a.button(panel, "📚 Study · daily 7–9 pm", () -> preset(a, "Study", "study", "19:00", "21:00", "Padh raha hoon, thodi der mein reply", new int[] { 1, 2, 3, 4, 5, 6, 7 }));
    a.button(panel, "😴 Sleep · daily 11 pm–7 am", () -> preset(a, "Sleep", "sleep", "23:00", "07:00", "So raha hoon", new int[] { 1, 2, 3, 4, 5, 6, 7 }));
    a.button(panel, "Ask Mimi about my time", () -> ask(a, routine));
    a.button(panel, "Time insights & export", () -> Insights.show(a));

    open = new PocketDialog(a, PocketDialog.Room.SCHEDULE)
      .setTitle("My weekly routine")
      .setView(sc)
      .setNegativeButton("Close", null)
      .show();
  }

  private static void preset(ChatDesk a, String name, String kind, String start, String end, String reason, int[] days) {
    Store s = a.store;
    Routine routine = Routine.get(s.p);
    try {
      JSONArray list = new JSONArray();
      for (int d : days) list.put(d);
      routine.put(Store.obj("id", "", "name", name, "kind", kind, "days", list, "date", "",
        "start", start, "end", end, "reason", reason, "language", "hi"));
      routine.save(s.p);
      Routine.pause(s.p, false);
      nudge(a);
      Toast.makeText(a, name + " added. Mimi follows it automatically.", Toast.LENGTH_SHORT).show();
      show(a);
    } catch (IllegalArgumentException e) {
      Toast.makeText(a, e.getMessage(), Toast.LENGTH_LONG).show();
    } catch (org.json.JSONException e) {
      Toast.makeText(a, "Could not add this block.", Toast.LENGTH_SHORT).show();
    }
  }

  private static void nudge(ChatDesk a) {
    if (ChatService.running) a.startService(new Intent(a, ChatService.class).setAction("routine"));
  }

  static void ask(final ChatDesk a, final Routine routine) {
    clear();
    LinearLayout panel = a.box(Color.WHITE, 14);
    a.text(panel, "Try “aaj kya plan hai”, “abhi free hoon?”, “next kab” or “neend”.", 13, a.muted, false);
    final EditText q = PocketDialog.input(a);
    q.setHint("Ask about your schedule");
    panel.addView(q);
    a.gap(panel, 8);
    final TextView answer = a.text(panel, "", 15, a.ink, false);
    answer.setBackground(a.bg(0xffe4ecfb, 14));
    answer.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
    AlertDialog d = new PocketDialog(a, PocketDialog.Room.SCHEDULE)
      .setTitle("Ask Mimi")
      .setView(panel)
      .setNegativeButton("Close", null)
      .setPositiveButton("Ask", null)
      .create();
    d.setOnDismissListener(x -> show(a));
    open = d;
    d.show();
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String text = q.getText().toString();
      if (text.trim().isEmpty()) {
        q.setError("Type a question");
        return;
      }
      String reply = routine.answer(text, "en", System.currentTimeMillis());
      if (reply == null) reply = "I can only answer schedule questions on this phone: aaj ka plan, next block, sleep timings, or “am I free now”.";
      answer.setText(reply);
      q.setText("");
    });
  }

  private static void editor(final ChatDesk a, final JSONObject existing) {
    clear();
    final Store s = a.store;
    final Routine routine = Routine.get(s.p);
    final boolean fresh = existing == null;
    final String[] start = { fresh ? "09:00" : existing.optString("start", "09:00") };
    final String[] end = { fresh ? "11:00" : existing.optString("end", "11:00") };
    final String[] id = { fresh ? "" : existing.optString("id") };
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);

    a.text(panel, "Block name", 13, a.muted, true);
    final EditText name = PocketDialog.input(a);
    name.setSingleLine();
    name.setText(fresh ? "" : existing.optString("name"));
    panel.addView(name);

    a.text(panel, "What kind of time is this?", 13, a.muted, true);
    final Spinner kind = PocketDialog.menu(a);
    kind.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item, Arrays.asList(Routine.KIND_NAMES)));
    panel.addView(kind);
    String currentKind = fresh ? "study" : existing.optString("kind", "custom");
    kind.setSelection(Math.max(0, Arrays.asList(Routine.KIND_KEYS).indexOf(currentKind)));

    a.text(panel, "Days", 13, a.muted, true);
    LinearLayout chips = new LinearLayout(a);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    final CheckBox[] days = new CheckBox[7];
    JSONArray had = fresh ? null : existing.optJSONArray("days");
    for (int i = 0; i < 7; i++) {
      CheckBox c = new CheckBox(a);
      c.setText(Routine.DAY_NAMES[i].substring(0, 1));
      c.setTextSize(12);
      c.setTextColor(a.ink);
      c.setPadding(0, 0, 0, 0);
      boolean on = had != null && had.length() == 7;
      if (had != null) for (int j = 0; j < had.length(); j++) if (had.optInt(j) == i + 1) on = true;
      c.setChecked(on);
      days[i] = c;
      chips.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }
    panel.addView(chips);

    final String[] once = { fresh ? "" : existing.optString("date", "") };
    a.text(panel, "One-off date (optional, YYYY-MM-DD — replaces the weekly plan that day)", 13, a.muted, true);
    final EditText dateField = PocketDialog.input(a);
    dateField.setSingleLine();
    dateField.setHint("2026-11-21");
    dateField.setText(once[0]);
    panel.addView(dateField);

    LinearLayout times = new LinearLayout(a);
    times.setOrientation(LinearLayout.HORIZONTAL);
    final Button from = a.button(times, "From " + Routine.fmt(start[0]), () -> {});
    final Button to = a.button(times, "To " + Routine.fmt(end[0]), () -> {});
    from.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    to.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    from.setOnClickListener(v -> pick(a, start, () -> from.setText("From " + Routine.fmt(start[0]))));
    to.setOnClickListener(v -> pick(a, end, () -> to.setText("To " + Routine.fmt(end[0]))));
    panel.addView(times);

    a.text(panel, "What should Mimi tell people? (optional)", 13, a.muted, true);
    final EditText reason = PocketDialog.input(a);
    reason.setHint("School mein hoon, 2 baje ke baad free");
    reason.setText(fresh ? "" : existing.optString("reason"));
    panel.addView(reason);

    final CheckBox offToday = new CheckBox(a), offTomorrow = new CheckBox(a);
    if (!fresh) {
      Calendar cal = Calendar.getInstance();
      final String todayKey = Routine.date(cal);
      cal.add(Calendar.DAY_OF_MONTH, 1);
      final String tomorrowKey = Routine.date(cal);
      offToday.setText("Off today only");
      offToday.setTextColor(a.ink);
      offToday.setChecked(routine.blockSkipped(id[0], todayKey));
      offTomorrow.setText("Off tomorrow only");
      offTomorrow.setTextColor(a.ink);
      offTomorrow.setChecked(routine.blockSkipped(id[0], tomorrowKey));
      panel.addView(offToday);
      panel.addView(offTomorrow);
      a.text(panel, "The weekly plan stays as it is; only that day skips this block.", 12, a.muted, false);
      final String t1 = todayKey, t2 = tomorrowKey;
      offToday.setOnCheckedChangeListener((v, on) -> {
        Routine r = Routine.get(a.store.p);
        r.skipBlock(id[0], t1, on);
        r.save(a.store.p);
        nudge(a);
      });
      offTomorrow.setOnCheckedChangeListener((v, on) -> {
        Routine r = Routine.get(a.store.p);
        r.skipBlock(id[0], t2, on);
        r.save(a.store.p);
        nudge(a);
      });
    }

    a.text(panel, "Greeting language", 13, a.muted, true);
    final Spinner lang = PocketDialog.menu(a);
    lang.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item, Arrays.asList("English", "Hinglish")));
    lang.setSelection(fresh || "en".equals(existing.optString("language", "en")) ? 0 : 1);
    panel.addView(lang);
    a.gap(panel, 6);

    final AlertDialog d = new PocketDialog(a, PocketDialog.Room.SCHEDULE)
      .setTitle(fresh ? "New routine block" : "Edit block")
      .setView(sc)
      .setNegativeButton("Cancel", (v, k) -> show(a))
      .setPositiveButton("Save", null)
      .create();
    if (!fresh) d.setButton(AlertDialog.BUTTON_NEUTRAL, "Delete", (android.content.DialogInterface.OnClickListener) null);
    d.show();
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      try {
        JSONObject b = new JSONObject();
        b.put("id", id[0]);
        b.put("name", name.getText().toString().trim());
        b.put("kind", Routine.KIND_KEYS[kind.getSelectedItemPosition()]);
        JSONArray list = new JSONArray();
        for (int i = 0; i < 7; i++) if (days[i].isChecked()) list.put(i + 1);
        b.put("days", list);
        b.put("date", dateField.getText().toString().trim());
        b.put("start", start[0]);
        b.put("end", end[0]);
        b.put("reason", reason.getText().toString().trim());
        b.put("language", lang.getSelectedItemPosition() == 1 ? "hi" : "en");
        routine.put(b);
        routine.save(s.p);
        Routine.pause(s.p, false);
        nudge(a);
        d.dismiss();
        show(a);
      } catch (IllegalArgumentException e) {
        name.setError(e.getMessage());
        Toast.makeText(a, e.getMessage(), Toast.LENGTH_SHORT).show();
      } catch (JSONException e) {
        Toast.makeText(a, "Could not save this block.", Toast.LENGTH_SHORT).show();
      }
    });
    if (!fresh) d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
      new PocketDialog(a, PocketDialog.Room.CONFIRM)
        .setTitle("Delete this block?")
        .setMessage("Only this phone's routine changes. Nothing is sent to WhatsApp.")
        .setNegativeButton("Keep", null)
        .setPositiveButton("Delete", (x, k) -> {
          routine.remove(id[0]);
          routine.save(s.p);
          nudge(a);
          d.dismiss();
          show(a);
        })
        .show());
  }

  private static void pick(final ChatDesk a, final String[] holder, final Runnable after) {
    int m = Routine.minutes(holder[0]);
    TimePickerDialog clock = new TimePickerDialog(a, (v, h, min) -> {
      holder[0] = String.format(Locale.US, "%02d:%02d", h, min);
      after.run();
    }, m / 60, m % 60, false);
    clock.setCustomTitle(PocketDialog.header(a, PocketDialog.Room.SCHEDULE, "Pick a time"));
    PocketDialog.decorate(a, clock, PocketDialog.Room.SCHEDULE);
    clock.show();
  }
}
