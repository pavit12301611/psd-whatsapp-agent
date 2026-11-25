package app.mimi.desk;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Remembers which privacy id (LID) belongs to which phone number so contact rules keep matching. */
final class Links {

  static final String KEY = "linkmap.v1";

  private static JSONObject read(SharedPreferences p) {
    try {
      return new JSONObject(p.getString(KEY, "{}"));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  static synchronized void add(SharedPreferences p, String lid, String number) {
    if (!lid.matches("[0-9]{7,15}") || !number.matches("[1-9][0-9]{6,14}")) return;
    JSONObject map = read(p);
    try {
      map.put(lid, number);
      if (map.length() > 500) {
        List<String> keys = new ArrayList<String>();
        for (Iterator<String> it = map.keys(); it.hasNext();) keys.add(it.next());
        for (int i = 0; i < keys.size() - 500; i++) map.remove(keys.get(i));
      }
      p.edit().putString(KEY, map.toString()).apply();
    } catch (JSONException ignored) {}
  }

  static JSONArray array(SharedPreferences p) {
    JSONObject map = read(p);
    JSONArray out = new JSONArray();
    for (Iterator<String> it = map.keys(); it.hasNext();) {
      String lid = it.next();
      out.put(new JSONArray().put(lid).put(map.optString(lid)));
    }
    return out;
  }

  static void forget(SharedPreferences p) {
    p.edit().remove(KEY).apply();
  }
}
