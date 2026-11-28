package app.mimi.desk;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** The backup file must carry every saved number and the routine, and restore them safely. */
public class BackupTest {

  static SharedPreferences preferences(Map<String, Object> data) {
    return (SharedPreferences) Proxy.newProxyInstance(
      BackupTest.class.getClassLoader(),
      new Class[] { SharedPreferences.class },
      (p, m, args) -> {
        if (m.getName().equals("edit")) return Proxy.newProxyInstance(
          BackupTest.class.getClassLoader(),
          new Class[] { SharedPreferences.Editor.class },
          (ed, op, kv) -> {
            if (op.getName().startsWith("put")) data.put((String) kv[0], kv[1]);
            else if (op.getName().equals("remove")) data.remove(kv[0]);
            else if (op.getName().equals("apply")) return null;
            else throw new AssertionError(op);
            return ed;
          }
        );
        if (m.getName().equals("contains")) return data.containsKey(args[0]);
        if (m.getName().startsWith("get")) return data.getOrDefault(args[0], args[1]);
        throw new AssertionError(m);
      }
    );
  }

  static JSONObject block(String name, String start, String end) throws Exception {
    JSONArray days = new JSONArray();
    for (int i = 1; i <= 6; i++) days.put(i);
    return Store.obj(
      "id", "",
      "name", name,
      "kind", "school",
      "days", days,
      "date", "",
      "start", start,
      "end", end,
      "reason", ""
    );
  }

  public static void main(String[] args) throws Exception {
    String owner = "919000000010";

    // ---- a phone with saved numbers, a routine and a draft
    Map<String, Object> disk = new HashMap<>();
    SharedPreferences p = preferences(disk);
    Store store = new Store(p);
    p.edit()
      .putString("chat.owner", owner)
      .putString("chat.scope", "all-direct")
      .putString("chat.provider", "test")
      .putString("chat.language", "hi")
      .putBoolean("ai.direct", true)
      .putBoolean("triage.ai", false)
      .apply();
    Vip.add(p, "919000000001", "Aai");
    Ignore.add(p, "919000000002", "Spam");
    Family.add(p, "919000000003", "Bhai");
    Routine routine = Routine.get(p);
    routine.put(block("School", "08:00", "14:00"));
    routine.put(block("Study", "19:00", "21:00"));
    routine.save(p);
    Drafts.add(
      p,
      Store.obj(
        "id", "m1",
        "jid", "919000000001@s.whatsapp.net",
        "sender", "Aai",
        "text", "Kab aa raha hai?",
        "category", "family",
        "urgent", true,
        "draft", "Main 2 baje free hoon."
      )
    );

    JSONObject doc = Backup.build(store, "app.mimi.desk.pocket2");
    assert doc.getString("app").equals(Backup.MAGIC);
    assert doc.getInt("format") == 1;
    assert doc.getJSONObject("profile").getString("owner").equals(owner);
    assert doc.getJSONObject("profile").getString("language").equals("hi");
    assert doc.getJSONObject("profile").getBoolean("triage") == false;
    assert doc.getJSONObject("numbers").getJSONArray("priority").length() == 1;
    assert doc.getJSONObject("numbers").getJSONArray("ignore").length() == 1;
    assert doc.getJSONObject("numbers").getJSONArray("family").length() == 1;
    assert doc.getJSONObject("routine").getJSONArray("blocks").length() == 2;
    assert doc.getJSONArray("drafts").length() == 1;
    // A backup is shareable: it must never contain keys or the lock.
    String raw = doc.toString();
    assert !raw.contains("gemini") && !raw.contains("groq") && !raw.contains("lock.");
    assert Backup.summary(doc).equals("3 numbers · 2 blocks · 1 draft");

    // ---- a fresh install restores the whole file, with no WhatsApp link present
    Map<String, Object> fresh = new HashMap<>();
    Store blank = new Store(preferences(fresh));
    String report = Backup.apply(blank, doc);
    SharedPreferences q = preferences(fresh);
    if (!report.startsWith("3 numbers · 2 blocks")) throw new AssertionError(report);
    assert q.getString("chat.owner", "").equals(owner);
    assert q.getString("chat.scope", "").equals("all-direct");
    assert q.getString("chat.language", "").equals("hi");
    assert q.getBoolean("triage.ai", true) == false;
    assert Vip.numbers(q).contains("919000000001");
    assert Ignore.numbers(q).contains("919000000002");
    assert Family.numbers(q).contains("919000000003");
    assert Vip.label(q, "919000000001").equals("Aai");
    assert Routine.get(q).blocks().length() == 2;
    assert Routine.get(q).enabled();
    assert Drafts.count(q) == 1;
    assert Drafts.list(q).getJSONObject(0).getString("sender").equals("Aai");

    // ---- a number in both lists keeps the app's own rule: silence wins
    Map<String, Object> both = new HashMap<>();
    Store clash = new Store(preferences(both));
    JSONObject mixed = new JSONObject(doc.toString());
    JSONObject numbers = mixed.getJSONObject("numbers");
    numbers.getJSONArray("ignore").put(Store.obj("n", "919000000001", "name", "Aai"));
    Backup.apply(clash, mixed);
    SharedPreferences r = preferences(both);
    assert !Vip.has(r, "919000000001") : "an ignored number must not stay priority";
    assert Ignore.numbers(r).contains("919000000001");

    // ---- bad input changes nothing
    Map<String, Object> untouched = new HashMap<>();
    Store guarded = new Store(preferences(untouched));
    JSONObject foreign = new JSONObject(doc.toString()).put("app", "something-else");
    boolean rejected = false;
    try {
      Backup.apply(guarded, foreign);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    assert rejected && untouched.isEmpty() : "a foreign file must be refused";
    JSONObject newer = new JSONObject(doc.toString()).put("format", 99);
    rejected = false;
    try {
      Backup.apply(guarded, newer);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    assert rejected && untouched.isEmpty() : "a newer format must be refused";

    // ---- junk entries are dropped instead of breaking the restore
    Map<String, Object> lenient = new HashMap<>();
    Store forgiving = new Store(preferences(lenient));
    JSONObject dirty = new JSONObject(doc.toString());
    dirty.getJSONObject("numbers").getJSONArray("ignore").put(
      Store.obj("n", "12", "name", "not a number")
    );
    dirty.getJSONObject("routine").getJSONArray("blocks").put(Store.obj("name", "", "start", "x"));
    Backup.apply(forgiving, dirty);
    SharedPreferences l = preferences(lenient);
    assert Ignore.numbers(l).size() == 1 : "a junk number must be skipped";
    assert Routine.get(l).blocks().length() == 2 : "a junk block must be skipped";

    // ---- a Gemini provider without a key on the new phone falls back to message-taking
    Map<String, Object> keyless = new HashMap<>();
    Store noKey = new Store(preferences(keyless));
    JSONObject withModel = new JSONObject(doc.toString());
    withModel.getJSONObject("profile").put("provider", "gemini");
    String gap = Backup.apply(noKey, withModel);
    assert preferences(keyless).getString("chat.provider", "").equals("test") : "no key, no provider";
    assert gap.contains("add your AI key") : gap;

    System.out.println("Backup export/import checks passed");
  }
}
