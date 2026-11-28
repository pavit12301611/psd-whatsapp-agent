package app.mimi.desk;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Weekly routine: owner-authored blocks, holiday skips and one-off overrides. */
final class Routine {

  static final String KEY = "routine.v1", PAUSED = "routine.paused";
  static final String[] KIND_KEYS = { "school", "study", "coaching", "work", "sleep", "driving", "custom" };
  static final String[] KIND_NAMES = { "School", "Study", "Coaching", "Work", "Sleep", "Driving", "Custom" };
  static final String[] DAY_NAMES = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };

  final JSONObject doc;

  private Routine(JSONObject d) {
    doc = d;
  }

  static JSONObject fresh() {
    try {
      return Store.obj("enabled", true, "blocks", new JSONArray(), "skip", new JSONArray());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static synchronized Routine get(SharedPreferences p) {
    try {
      String raw = p.getString(KEY, "");
      return new Routine(raw.isEmpty() ? fresh() : new JSONObject(raw));
    } catch (Exception e) {
      return new Routine(fresh());
    }
  }

  synchronized void save(SharedPreferences p) {
    p.edit().putString(KEY, doc.toString()).apply();
  }

  boolean enabled() {
    return doc.optBoolean("enabled", true);
  }

  static boolean paused(SharedPreferences p) {
    return p.getBoolean(PAUSED, false);
  }

  static void pause(SharedPreferences p, boolean value) {
    p.edit().putBoolean(PAUSED, value).apply();
  }

  JSONArray blocks() {
    JSONArray a = doc.optJSONArray("blocks");
    return a == null ? new JSONArray() : a;
  }

  JSONArray skip() {
    JSONArray a = doc.optJSONArray("skip");
    return a == null ? new JSONArray() : a;
  }

  boolean skipped(String date) {
    for (int i = 0; i < skip().length(); i++) if (date.equals(skip().optString(i))) return true;
    return false;
  }

  boolean overridden(String date) {
    for (int i = 0; i < blocks().length(); i++)
      if (date.equals(blocks().optJSONObject(i).optString("date", ""))) return true;
    return false;
  }

  void skipToday(boolean value) {
    String date = date(Calendar.getInstance());
    JSONArray out = new JSONArray();
    for (int i = 0; i < skip().length(); i++) {
      String d = skip().optString(i);
      if (!d.equals(date)) out.put(d);
    }
    if (value) out.put(date);
    try {
      doc.put("skip", out);
    } catch (JSONException ignored) {}
  }

  static String mode(String kind) {
    return "sleep".equals(kind) ? "sleeping" : "driving".equals(kind) ? "driving" : "busy";
  }

  static int minutes(String hhmm) {
    String[] p = hhmm.split(":");
    return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
  }

  static boolean validTime(String hhmm) {
    return hhmm != null && hhmm.matches("([01][0-9]|2[0-3]):[0-5][0-9]");
  }

  static String fmt(String hhmm) {
    int m = minutes(hhmm), h = (m / 60 + 11) % 12 + 1;
    return h + ":" + String.format(Locale.US, "%02d", m % 60) + (m / 60 < 12 ? " am" : " pm");
  }

  static String date(Calendar day) {
    return String.format(Locale.US, "%04d-%02d-%02d", day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
  }

  static int weekday(Calendar day) {
    int d = day.get(Calendar.DAY_OF_WEEK);
    return d == Calendar.SUNDAY ? 7 : d - 1;
  }

  static String repeat(JSONObject b) {
    String date = b.optString("date", "");
    if (!date.isEmpty()) return "Once · " + date.substring(5);
    JSONArray days = b.optJSONArray("days");
    if (days == null || days.length() == 7) return "Every day";
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < days.length(); i++) {
      int d = days.optInt(i);
      if (d < 1 || d > 7) continue;
      if (s.length() > 0) s.append(" · ");
      s.append(DAY_NAMES[d - 1]);
    }
    return s.toString();
  }

  static String kindName(String kind) {
    for (int i = 0; i < KIND_KEYS.length; i++) if (KIND_KEYS[i].equals(kind)) return KIND_NAMES[i];
    return "Custom";
  }

  static final class Slot {

    final String id, name, kind, mode, reason, startText, endText;
    final long start, end;

    Slot(String id, String name, String kind, String reason, String startText, String endText, long start, long end) {
      this.id = id;
      this.name = name;
      this.kind = kind;
      this.mode = Routine.mode(kind);
      this.reason = reason;
      this.startText = startText;
      this.endText = endText;
      this.start = start;
      this.end = end;
    }

    String reasonText() {
      return reason.isEmpty() ? name + " · back at " + endText : reason;
    }
  }

  static void midnight(Calendar c) {
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
  }

  private static long at(Calendar day, String hhmm) {
    Calendar c = (Calendar) day.clone();
    int m = minutes(hhmm);
    c.set(Calendar.HOUR_OF_DAY, m / 60);
    c.set(Calendar.MINUTE, m % 60);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c.getTimeInMillis();
  }

  JSONArray blockSkips() {
    JSONArray a = doc.optJSONArray("blockSkips");
    return a == null ? new JSONArray() : a;
  }

  boolean blockSkipped(String id, String date) {
    if (id == null || id.isEmpty()) return false;
    JSONArray a = blockSkips();
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.optJSONObject(i);
      if (o != null && id.equals(o.optString("id")) && date.equals(o.optString("date"))) return true;
    }
    return false;
  }

  /** Off for one block on one day only; the weekly plan is untouched. */
  synchronized void skipBlock(String id, String date, boolean off) {
    if (id == null || id.isEmpty()) return;
    JSONArray out = new JSONArray();
    JSONArray old = blockSkips();
    for (int i = 0; i < old.length(); i++) {
      JSONObject o = old.optJSONObject(i);
      if (o == null) continue;
      if (id.equals(o.optString("id")) && date.equals(o.optString("date"))) continue;
      out.put(o);
    }
    try {
      if (off) {
        out.put(Store.obj("id", id, "date", date));
        while (out.length() > 120) out.remove(0);
      }
      doc.put("blockSkips", out);
    } catch (JSONException ignored) {}
  }

  /** Today's blocks including ones switched off for today only. */
  List<Slot> todayAll(long now) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(now);
    midnight(day);
    return build(day, false);
  }

  List<Slot> slots(Calendar day) {
    return build(day, true);
  }

  /** Blocks that occur on that calendar day; overnight windows end the next morning. */
  private List<Slot> build(Calendar day, boolean honourSkips) {
    List<Slot> out = new ArrayList<Slot>();
    String date = date(day);
    if (skipped(date) && !overridden(date)) return out;
    int wd = weekday(day);
    boolean override = overridden(date);
    for (int i = 0; i < blocks().length(); i++) {
      JSONObject b = blocks().optJSONObject(i);
      if (b == null) continue;
      String own = b.optString("date", "");
      boolean match = own.isEmpty() ? !override && hasDay(b, wd) : own.equals(date);
      if (!match) continue;
      String id = b.optString("id"), name = b.optString("name"), start = b.optString("start"), end = b.optString("end");
      if (id.isEmpty() || name.isEmpty() || !validTime(start) || !validTime(end) || start.equals(end)) continue;
      if (honourSkips && blockSkipped(id, date)) continue;
      long from = at(day, start), to = at(day, end);
      if (to <= from) to += 86400000L;
      out.add(new Slot(id, name, b.optString("kind", "custom"), b.optString("reason", ""), fmt(start), fmt(end), from, to));
    }
    Collections.sort(out, new Comparator<Slot>() {
      public int compare(Slot a, Slot b) {
        return Long.compare(a.start, b.start);
      }
    });
    return out;
  }

  private static boolean hasDay(JSONObject b, int wd) {
    JSONArray days = b.optJSONArray("days");
    if (days == null) return false;
    for (int i = 0; i < days.length(); i++) if (days.optInt(i) == wd) return true;
    return false;
  }

  Slot active(long now) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(now);
    midnight(day);
    Calendar before = (Calendar) day.clone();
    before.add(Calendar.DAY_OF_MONTH, -1);
    Slot found = null;
    for (List<Slot> list : Arrays.asList(slots(day), slots(before)))
      for (Slot s : list) if (s.start <= now && now < s.end && (found == null || s.start > found.start)) found = s;
    return found;
  }

  Slot next(long now) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(now);
    midnight(day);
    for (int i = 0; i < 8; i++) {
      for (Slot s : slots(day)) if (s.start > now) return s;
      day.add(Calendar.DAY_OF_MONTH, 1);
    }
    return null;
  }

  List<Slot> today(long now) {
    Calendar day = Calendar.getInstance();
    day.setTimeInMillis(now);
    midnight(day);
    return slots(day);
  }

  /** Compact plan line for the web desk: "School 8:00 am–2:00 pm · Study…" */
  String planLine(long now) {
    StringBuilder s = new StringBuilder();
    if (skipped(date(Calendar.getInstance()))) return "Holiday · routine skipped";
    for (Slot slot : today(now)) {
      if (s.length() > 0) s.append("  ·  ");
      s.append(slot.name).append(" ").append(slot.startText).append("–").append(slot.endText);
    }
    return s.length() == 0 ? "No blocks today" : s.toString();
  }

  String statusLine(long now, boolean paused) {
    if (paused) return "Routine paused";
    Slot live = active(now);
    if (live != null) return "Now: " + live.name + " until " + live.endText;
    Slot soon = next(now);
    return soon == null ? "Free all week" : "Next: " + soon.name + " at " + soon.startText;
  }

  static void validate(JSONObject b, JSONArray others) throws JSONException {
    String name = b.optString("name").replaceAll("[\\u0000-\\u001f\\s]+", " ").trim();
    if (name.isEmpty() || name.length() > 32) throw new IllegalArgumentException("Give the block a name (32 letters max).");
    String start = b.optString("start"), end = b.optString("end");
    if (!validTime(start) || !validTime(end)) throw new IllegalArgumentException("Pick a start and end time.");
    if (start.equals(end)) throw new IllegalArgumentException("Start and end are the same time.");
    String date = b.optString("date", "");
    if (!date.isEmpty() && !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Invalid date.");
    JSONArray days = b.optJSONArray("days");
    if (date.isEmpty() && (days == null || days.length() == 0)) throw new IllegalArgumentException("Choose at least one day.");
    int s = minutes(start), e = minutes(end);
    List<int[]> mine = new ArrayList<int[]>();
    if (e > s) mine.add(new int[] { s, e }); else {
      mine.add(new int[] { s, 1440 });
      mine.add(new int[] { 0, e });
    }
    for (int i = 0; i < others.length(); i++) {
      JSONObject o = others.optJSONObject(i);
      if (o == null || o.optString("id").equals(b.optString("id"))) continue;
      if (!sharesDay(b, o)) continue;
      int os = minutes(o.optString("start")), oe = minutes(o.optString("end"));
      List<int[]> them = new ArrayList<int[]>();
      if (oe > os) them.add(new int[] { os, oe }); else {
        them.add(new int[] { os, 1440 });
        them.add(new int[] { 0, oe });
      }
      for (int[] x : mine) for (int[] y : them)
        if (x[0] < y[1] && y[0] < x[1])
          throw new IllegalArgumentException("Overlaps with " + o.optString("name") + ". Pick another time.");
    }
  }

  private static boolean sharesDay(JSONObject a, JSONObject b) {
    String da = a.optString("date", ""), db = b.optString("date", "");
    if (!da.isEmpty() || !db.isEmpty()) return !da.isEmpty() && da.equals(db);
    JSONArray x = a.optJSONArray("days"), y = b.optJSONArray("days");
    if (x == null || y == null) return false;
    for (int i = 0; i < x.length(); i++) for (int j = 0; j < y.length(); j++) if (x.optInt(i) == y.optInt(j)) return true;
    return false;
  }

  /** Insert or replace by id; returns the stored block. */
  synchronized JSONObject put(JSONObject block) {
    try {
      validate(block, blocks());
      String id = block.optString("id");
      if (id.isEmpty()) block.put("id", "r" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(blocks().length(), 36));
      JSONArray out = new JSONArray();
      boolean replaced = false;
      for (int i = 0; i < blocks().length(); i++) {
        JSONObject b = blocks().optJSONObject(i);
        if (b != null && b.optString("id").equals(block.optString("id"))) {
          out.put(block);
          replaced = true;
        } else out.put(b);
      }
      if (!replaced) out.put(block);
      doc.put("blocks", out);
      return block;
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  synchronized void remove(String id) {
    JSONArray out = new JSONArray();
    for (int i = 0; i < blocks().length(); i++) {
      JSONObject b = blocks().optJSONObject(i);
      if (b != null && !b.optString("id").equals(id)) out.put(b);
    }
    try {
      doc.put("blocks", out);
    } catch (JSONException ignored) {}
  }

  /** Ask Mimi: answers are derived from this phone's routine only. */
  String answer(String question, String language, long now) {
    String q = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
    if (q.isEmpty()) return null;
    boolean hi = "hi".equals(language);
    Slot live = active(now);
    Slot soon = next(now);
    String status = live != null
      ? (hi ? "Abhi " + live.name + " chal raha hai, " + live.endText + " tak." : "You are in " + live.name + " until " + live.endText + ".")
      : (hi ? "Abhi koi block nahi — Mimi off hai." : "No block right now — Mimi is off.");
    String upcomingText = soon == null
      ? (hi ? "Aage koi block nahi." : "Nothing else scheduled.")
      : (hi ? "Aage: " + soon.name + ", " + soon.startText : "Coming up: " + soon.name + " at " + soon.startText);
    boolean tomorrow = q.matches(".*(kal|tomorrow).*");
    boolean when = q.matches(".*(abhi|now|free|khali|status|next|agle|aage|kab|when|upcoming).*");
    boolean sleep = q.matches(".*(neend|sleep|sona).*");
    boolean plan = q.matches(".*(aaj|today|plan|schedule|routine|time|timetable).*");
    if (sleep) {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < blocks().length(); i++) {
        JSONObject b = blocks().optJSONObject(i);
        if (b == null || !"sleep".equals(b.optString("kind"))) continue;
        if (s.length() > 0) s.append("\n");
        s.append(b.optString("name")).append(" · ").append(fmt(b.optString("start"))).append("–").append(fmt(b.optString("end")));
      }
      return s.length() == 0 ? (hi ? "Sleep block set nahi hai." : "No sleep block yet.") : s.toString();
    }
    if (when) return status + "\n" + upcomingText;
    if (tomorrow || plan) {
      Calendar day = Calendar.getInstance();
      day.setTimeInMillis(now + (tomorrow ? 86400000L : 0));
      List<Slot> list = slots(day);
      StringBuilder s = new StringBuilder(hi ? (tomorrow ? "Kal ka plan" : "Aaj ka plan") : (tomorrow ? "Tomorrow" : "Today"));
      if (list.isEmpty()) s.append("\n").append(hi ? "• Koi block nahi — poora free." : "• No blocks — free all day.");
      for (Slot slot : list) s.append("\n").append("• ").append(slot.startText).append("–").append(slot.endText).append("  ").append(slot.name);
      s.append("\n\n").append(upcomingText);
      return s.toString();
    }
    return null;
  }
}
