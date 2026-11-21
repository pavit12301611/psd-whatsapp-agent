package app.mimi.desk;

import java.util.UUID;
import org.json.*;

/** Owner-scoped, opt-in encrypted queue. Mutations share a lock with disabling/forgetting. */
final class FollowUps {

  static final int LIMIT = 50;
  final Store store;
  final String rowsKey, enabledKey;

  FollowUps(Store store, String owner) {
    if (!owner.matches("[1-9][0-9]{6,14}")) throw new IllegalArgumentException(
      "Missing owner"
    );
    this.store = store;
    rowsKey = "followups.v1." + owner;
    enabledKey = rowsKey + ".on";
  }

  boolean enabled() {
    return store.p.getBoolean(enabledKey, false);
  }

  void enable() {
    synchronized (FollowUps.class) {
      store.p.edit().putBoolean(enabledKey, true).apply();
    }
  }

  void forget() {
    synchronized (FollowUps.class) {
      store.p.edit().remove(enabledKey).remove(rowsKey).apply();
    }
  }

  JSONArray read() throws Exception {
    synchronized (FollowUps.class) {
      return store.get(rowsKey).isEmpty()
        ? new JSONArray()
        : new JSONArray(store.secret(rowsKey));
    }
  }

  private void save(JSONArray list) throws Exception {
    store.p.edit().putString(rowsKey, Store.enc(list.toString())).apply();
  }

  void add(JSONObject item) throws Exception {
    synchronized (FollowUps.class) {
      if (!enabled() || item == null) return;
      String kind = item.optString("kind"),
        sender = item.optString("sender"),
        body = item.optString("text");
      if (
        !kind.matches("callback|urgent") ||
        sender.isEmpty() ||
        sender.length() > 80 ||
        body.isEmpty() ||
        body.length() > 600
      ) return;
      JSONArray list = read();
      while (list.length() >= LIMIT) list.remove(0);
      list.put(
        Store.obj(
          "id",
          UUID.randomUUID().toString(),
          "kind",
          kind,
          "sender",
          sender,
          "text",
          body,
          "time",
          System.currentTimeMillis(),
          "done",
          false
        )
      );
      save(list);
    }
  }

  void change(String id, boolean done, boolean remove) throws Exception {
    synchronized (FollowUps.class) {
      if (!enabled()) return;
      JSONArray list = read();
      for (int i = 0; i < list.length(); i++) {
        JSONObject row = list.getJSONObject(i);
        if (id.equals(row.getString("id"))) {
          if (remove) list.remove(i);
          else row.put("done", done);
          save(list);
          return;
        }
      }
    }
  }
}
