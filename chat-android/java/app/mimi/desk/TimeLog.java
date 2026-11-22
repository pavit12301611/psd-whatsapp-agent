package app.mimi.desk;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Local, per-day time in each routine block. Never leaves the phone unless you export it. */
final class TimeLog {

  static final String KEY = "track.v1";

  private static JSONObject read(SharedPreferences p) {
    try {
      String raw = p.getString(KEY, "");
      return raw.isEmpty() ? Store.obj("days", new JSONObject(), "open", JSONObject.NULL) : new JSONObject(raw);
    } catch (Exception e) {
      try {
        return Store.obj("days", new JSONObject(), "open", JSONObject.NULL);
      } catch (JSONException x) {
        throw new IllegalStateException(x);
      }
    }
  }

  private static JSONObject open(JSONObject doc) {
    JSONObject o = doc.optJSONObject("open");
    return o;
  }

  /** Close any running interval, then start counting `id` (may be null for "available"). */
  static synchronized void mark(SharedPreferences p, String id, String label, long now) {
    JSONObject doc = read(p);
    JSONObject open = open(doc);
    try {
      if (open != null) {
        String was = open.optString("id"), date = open.optString("date");
        long since = open.optLong("since", now), span = Math.max(0, Math.min(now - since, 6 * 3600000L));
        if (!was.isEmpty() && span > 0) add(doc, date, was, span / 1000);
      }
      if (id == null) doc.put("open", JSONObject.NULL);
      else doc.put("open", Store.obj("id", id, "label", label, "date", date(new Date(now)), "since", now));
      p.edit().putString(KEY, doc.toString()).apply();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static synchronized void close(SharedPreferences p, long now) {
    mark(p, null, "", now);
  }

  private static void add(JSONObject doc, String date, String id, long seconds) throws JSONException {
    JSONObject days = doc.optJSONObject("days");
    if (days == null) days = new JSONObject();
    JSONObject day = days.optJSONObject(date);
    if (day == null) day = new JSONObject();
    day.put(id, day.optLong(id, 0) + seconds);
    days.put(date, day);
    doc.put("days", days);
    // Keep roughly the last eight weeks so the file stays tiny.
    if (days.length() > 70) {
      List<String> keys = new ArrayList<String>();
      for (Iterator<String> it = days.keys(); it.hasNext();) keys.add(it.next());
      Collections.sort(keys);
      for (int i = 0; i < keys.size() - 70; i++) days.remove(keys.get(i));
    }
  }

  static String date(Date d) {
    Calendar c = Calendar.getInstance();
    c.setTime(d);
    return Routine.date(c);
  }

  /** Totals for one day, including the interval still running, keyed by id with a label map. */
  static synchronized LinkedHashMap<String, long[]> day(SharedPreferences p, String date, long now) {
    LinkedHashMap<String, long[]> out = new LinkedHashMap<String, long[]>();
    JSONObject doc = read(p);
    JSONObject day = doc.optJSONObject("days") == null ? null : doc.optJSONObject("days").optJSONObject(date);
    if (day != null) for (Iterator<String> it = day.keys(); it.hasNext();) {
      String k = it.next();
      out.put(k, new long[] { day.optLong(k, 0) });
    }
    JSONObject open = open(doc);
    if (open != null && date.equals(open.optString("date"))) {
      String id = open.optString("id");
      long live = Math.max(0, Math.min(now - open.optLong("since", now), 6 * 3600000L)) / 1000;
      long[] cur = out.get(id);
      if (cur == null) out.put(id, new long[] { live });
      else cur[0] += live;
    }
    return out;
  }

  static String range(String date, int days) {
    Calendar c = Calendar.getInstance();
    try {
      c.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
    } catch (Exception ignored) {}
    c.add(Calendar.DAY_OF_MONTH, -(days - 1));
    return Routine.date(c);
  }

  static List<String> dates(int days, long now) {
    List<String> out = new ArrayList<String>();
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(now);
    Routine.midnight(c);
    c.add(Calendar.DAY_OF_MONTH, -(days - 1));
    for (int i = 0; i < days; i++) {
      out.add(Routine.date(c));
      c.add(Calendar.DAY_OF_MONTH, 1);
    }
    return out;
  }
}
