package app.mimi.desk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/**
 * One readable JSON file with every number Mimi was told about, the weekly routine and the
 * saved settings. Works with no WhatsApp link at all. The file never holds AI keys, the app
 * lock PIN or the WhatsApp session credentials.
 */
final class Backup {

  static final String MAGIC = "mimi-pocket-backup";
  static final int FORMAT = 1, SAVE = 41, OPEN = 42, LIMIT = 512 * 1024;

  private Backup() {}

  // ---------------------------------------------------------------- export

  static JSONObject build(Store s, String pkg) throws JSONException {
    SharedPreferences p = s.p;
    String owner = s.get("chat.owner");
    JSONObject doc = Store.obj(
      "app", MAGIC,
      "format", FORMAT,
      "package", pkg,
      "exported", System.currentTimeMillis()
    );
    doc.put(
      "profile",
      Store.obj(
        "owner", owner,
        "scope", p.getString("chat.scope", "all-direct"),
        "provider", p.getString("chat.provider", "test"),
        "language", p.getString("chat.language", "en"),
        "direct", p.getBoolean("ai.direct", true),
        "triage", p.getBoolean("triage.ai", true)
      )
    );
    Routine routine = Routine.get(p);
    doc.put(
      "routine",
      Store.obj(
        "enabled", routine.enabled(),
        "blocks", routine.blocks(),
        "skip", routine.skip(),
        "blockSkips", routine.blockSkips()
      )
    );
    doc.put("routinePaused", Routine.paused(p));
    doc.put(
      "numbers",
      Store.obj(
        "priority", Vip.list(p),
        "ignore", Ignore.list(p),
        "family", Family.list(p)
      )
    );
    doc.put("familyLocation", p.getBoolean(Family.SHARE, false));
    doc.put("callShield", Store.obj("enabled", false, "people", new JSONObject()));
    try {
      JSONObject saved = CallRules.read(s, owner);
      JSONObject people = saved.optJSONObject("people");
      doc.put(
        "callShield",
        Store.obj(
          "enabled", saved.optBoolean("enabled"),
          "people", people == null ? new JSONObject() : people
        )
      );
    } catch (JSONException e) {
      throw e;
    } catch (Exception ignored) {} // unreadable policy is simply left out
    try {
      JSONObject links = new JSONObject(p.getString(Links.KEY, "{}"));
      doc.put("privacyLinks", links);
    } catch (Exception ignored) {}
    doc.put("drafts", Drafts.list(p));
    if (owner.matches("[1-9][0-9]{6,14}")) {
      try {
        FollowUps followUps = new FollowUps(s, owner);
        if (followUps.enabled()) doc.put("followUps", followUps.read());
      } catch (Exception ignored) {}
    }
    try {
      JSONObject insights = new JSONObject(p.getString(TimeLog.KEY, ""));
      doc.put("insights", insights); // kept for your records; never restored
    } catch (Exception ignored) {}
    return doc;
  }

  /** "12 numbers · 9 routine blocks · 3 drafts" — used by the tile and the toasts. */
  static String summary(JSONObject doc) {
    if (doc == null) return "No saved data";
    int numbers = numbers(doc.optJSONObject("numbers")) + people(doc.optJSONObject("callShield"));
    JSONObject routine = doc.optJSONObject("routine");
    int blocks = routine == null || routine.optJSONArray("blocks") == null
      ? 0
      : routine.optJSONArray("blocks").length();
    JSONArray drafts = doc.optJSONArray("drafts");
    StringBuilder line = new StringBuilder();
    line.append(numbers).append(numbers == 1 ? " number" : " numbers");
    line.append(" · ").append(blocks).append(blocks == 1 ? " block" : " blocks");
    if (drafts != null && drafts.length() > 0)
      line.append(" · ").append(drafts.length()).append(drafts.length() == 1 ? " draft" : " drafts");
    return line.toString();
  }

  static int numbers(Store s) {
    return numbersStore(s.p);
  }

  private static int numbersStore(SharedPreferences p) {
    return Vip.numbers(p).size() + Ignore.numbers(p).size() + Family.numbers(p).size();
  }

  private static int numbers(JSONObject lists) {
    if (lists == null) return 0;
    return count(lists.optJSONArray("priority")) +
      count(lists.optJSONArray("ignore")) +
      count(lists.optJSONArray("family"));
  }

  private static int people(JSONObject shield) {
    return shield == null ? 0 : shield.optJSONObject("people") == null
      ? 0
      : shield.optJSONObject("people").length();
  }

  private static int count(JSONArray a) {
    return a == null ? 0 : a.length();
  }

  /** Human line for a file: "Saved 20 Nov 2026 · 12 numbers · 9 blocks". */
  static String describe(JSONObject doc, String currentPackage) {
    long at = doc.optLong("exported", 0);
    String when = at > 0
      ? new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(new Date(at))
      : "unknown date";
    String pkg = doc.optString("package", "");
    String extra = pkg.isEmpty() || pkg.equals(currentPackage)
      ? ""
      : "\nSaved by another Mimi install (" + pkg + ").";
    return "Saved " + when + " · " + summary(doc) + extra;
  }

  // ------------------------------------------------------------------ file

  static void desk(ChatDesk a) {
    String onPhone;
    try {
      onPhone = "On this phone: " + summary(build(a.store, a.getPackageName()));
    } catch (Exception e) {
      onPhone = "Saved data could not be read right now.";
    }
    new PocketDialog(a, PocketDialog.Room.BACKUP)
      .setTitle("Backup & restore")
      .setMessage(
        onPhone +
        "\n\nOne file holds every number you saved — priority, ignore, family, call list — plus the weekly routine, drafts, follow-ups and settings. It works with WhatsApp disconnected and never contains your AI keys or your app-lock PIN.\n\nRestoring replaces those lists and the routine on this phone; AI keys and the lock stay as they are."
      )
      .setNegativeButton("Close", null)
      .setNeutralButton("Save file", (d, k) -> save(a))
      .setPositiveButton("Restore", (d, k) -> open(a))
      .show();
  }

  static void save(ChatDesk a) {
    String name = "mimi-backup-" +
      new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) +
      ".json";
    try {
      a.startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
          .addCategory(Intent.CATEGORY_OPENABLE)
          .setType("application/json")
          .putExtra(Intent.EXTRA_TITLE, name),
        SAVE
      );
    } catch (Exception e) {
      Toast.makeText(a, "No file picker on this phone.", Toast.LENGTH_LONG).show();
    }
  }

  static void open(ChatDesk a) {
    try {
      a.startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
          .addCategory(Intent.CATEGORY_OPENABLE)
          .setType("*/*"),
        OPEN
      );
    } catch (Exception e) {
      Toast.makeText(a, "No file picker on this phone.", Toast.LENGTH_LONG).show();
    }
  }

  static void result(ChatDesk a, int request, int result, Intent data) {
    if (result != android.app.Activity.RESULT_OK || data == null) return;
    Uri uri = data.getData();
    if (uri == null) return;
    if (request == SAVE) write(a, uri);
    else if (request == OPEN) read(a, uri);
  }

  private static void write(ChatDesk a, Uri uri) {
    try {
      JSONObject doc = build(a.store, a.getPackageName());
      byte[] bytes = doc.toString(2).getBytes("UTF-8");
      OutputStream out = a.getContentResolver().openOutputStream(uri, "wt");
      if (out == null) throw new IOException("no stream");
      try {
        out.write(bytes);
        out.flush();
      } finally {
        out.close();
      }
      Toast.makeText(a, "Backup saved · " + summary(doc), Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      Toast.makeText(a, "Could not save the backup file.", Toast.LENGTH_LONG).show();
    }
  }

  private static void read(ChatDesk a, Uri uri) {
    try {
      InputStream in = a.getContentResolver().openInputStream(uri);
      if (in == null) throw new IOException("no stream");
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read, total = 0;
      try {
        while ((read = in.read(chunk)) != -1) {
          total += read;
          if (total > LIMIT) throw new IllegalArgumentException(
            "That file is too big to be a Mimi backup."
          );
          buffer.write(chunk, 0, read);
        }
      } finally {
        in.close();
      }
      JSONObject doc;
      try {
        doc = new JSONObject(new String(buffer.toByteArray(), "UTF-8"));
      } catch (JSONException e) {
        throw new IllegalArgumentException("That file is not a Mimi backup.");
      }
      confirm(a, doc);
    } catch (IllegalArgumentException e) {
      Toast.makeText(a, e.getMessage(), Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      Toast.makeText(a, "That file could not be opened.", Toast.LENGTH_LONG).show();
    }
  }

  private static void confirm(ChatDesk a, JSONObject doc) {
    if (!MAGIC.equals(doc.optString("app"))) {
      Toast.makeText(a, "That file is not a Mimi backup.", Toast.LENGTH_LONG).show();
      return;
    }
    if (doc.optInt("format", 0) < 1 || doc.optInt("format", 0) > FORMAT) {
      Toast.makeText(
        a,
        "That backup was made by a newer Mimi. Update the app first.",
        Toast.LENGTH_LONG
      ).show();
      return;
    }
    new PocketDialog(a, PocketDialog.Room.BACKUP)
      .setTitle("Restore this file?")
      .setMessage(
        describe(doc, a.getPackageName()) +
        "\n\nThe saved lists, routine and account settings on this phone will be replaced. Your AI keys, app lock and WhatsApp session are left alone. Restore works offline; press Connect WhatsApp afterwards."
      )
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Restore", (d, k) -> {
        try {
          String done = apply(a.store, doc);
          if (ChatService.running) a.startService(
            new Intent(a, ChatService.class).setAction("stop")
          );
          ChatService.status = "Backup restored. " + done;
          Toast.makeText(a, "Restored · " + done, Toast.LENGTH_LONG).show();
          a.refreshSoon();
        } catch (IllegalArgumentException e) {
          Toast.makeText(a, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
          Toast.makeText(a, "Could not restore this backup.", Toast.LENGTH_LONG).show();
        }
      })
      .show();
  }

  // ----------------------------------------------------------------- apply

  /** Validates the whole document, then replaces the saved data. Returns a short report. */
  static String apply(Store s, JSONObject doc) throws Exception {
    if (doc == null || !MAGIC.equals(doc.optString("app")))
      throw new IllegalArgumentException("That file is not a Mimi backup.");
    if (doc.optInt("format", 0) < 1 || doc.optInt("format", 0) > FORMAT)
      throw new IllegalArgumentException("That backup needs a newer Mimi.");

    JSONObject profile = doc.optJSONObject("profile");
    String owner = profile == null ? "" : digits(profile.optString("owner"));
    if (owner == null) {
      if (!profile.optString("owner", "").isEmpty())
        throw new IllegalArgumentException("The saved WhatsApp number in this file is invalid.");
      owner = "";
    }
    String scope = profile == null ? null : profile.optString("scope", "all-direct");
    if (scope != null && !"all-direct".equals(scope)) {
      scope = digits(scope);
      if (scope == null) throw new IllegalArgumentException("The tester number in this file is invalid.");
    }
    String provider = profile == null ? null : profile.optString("provider", "test");
    if (provider != null && !Arrays.asList("test", "gemini", "groq").contains(provider))
      throw new IllegalArgumentException("The AI provider in this file is unknown.");
    String language = profile == null ? "en" : profile.optString("language", "en");
    if (!Arrays.asList("en", "hi").contains(language)) language = "en";

    JSONObject lists = doc.optJSONObject("numbers");
    JSONArray priority = people(lists == null ? null : lists.optJSONArray("priority"), 40, "priority");
    JSONArray ignore = people(lists == null ? null : lists.optJSONArray("ignore"), 100, "ignore");
    JSONArray family = people(lists == null ? null : lists.optJSONArray("family"), 30, "family");
    JSONObject shield = doc.optJSONObject("callShield");
    TreeMap<String, String> callers = callers(shield == null ? null : shield.optJSONObject("people"));
    JSONArray drafts = drafts(doc.optJSONArray("drafts"));
    JSONArray followUps = followUps(doc.optJSONArray("followUps"));
    JSONObject links = doc.optJSONObject("privacyLinks");
    JSONObject routine = doc.optJSONObject("routine");
    JSONArray blocks = routine == null ? null : routine.optJSONArray("blocks");
    JSONArray skip = routine == null ? null : routine.optJSONArray("skip");
    JSONArray blockSkips = routine == null ? null : routine.optJSONArray("blockSkips");
    if (blocks != null && blocks.length() > 200)
      throw new IllegalArgumentException("That routine has too many blocks.");

    // Everything above is validated: now the writes begin.
    SharedPreferences p = s.p;
    // Keys stay on the old phone, so a provider whose key is missing here falls back
    // to message-taking; the report says so instead of failing to start the engine.
    boolean keyGap = !owner.isEmpty() &&
      provider != null &&
      !"test".equals(provider) &&
      !p.contains(provider);
    if (profile != null && !owner.isEmpty()) {
      p.edit()
        .putString("chat.owner", owner)
        .putString("chat.scope", "all-direct".equals(scope) ? "all-direct" : scope)
        .putString("chat.provider", keyGap ? "test" : provider)
        .putString("chat.language", language)
        .putBoolean("ai.direct", profile.optBoolean("direct", true))
        .putBoolean("triage.ai", profile.optBoolean("triage", true))
        .apply();
    }

    // Numbers: priority first, then the ignore list, so the "never both" rule still wins.
    p.edit().remove(Vip.KEY).remove(Ignore.KEY).remove(Family.KEY).apply();
    int skipped = 0;
    for (int i = 0; i < priority.length(); i++) {
      JSONObject o = priority.getJSONObject(i);
      try {
        Vip.add(p, o.getString("n"), o.optString("name"));
      } catch (IllegalArgumentException e) {
        skipped++;
      }
    }
    for (int i = 0; i < ignore.length(); i++) {
      JSONObject o = ignore.getJSONObject(i);
      try {
        Ignore.add(p, o.getString("n"), o.optString("name"));
      } catch (IllegalArgumentException e) {
        skipped++;
      }
    }
    for (int i = 0; i < family.length(); i++) {
      JSONObject o = family.getJSONObject(i);
      try {
        Family.add(p, o.getString("n"), o.optString("name"));
      } catch (IllegalArgumentException e) {
        skipped++;
      }
    }
    if (doc.has("familyLocation"))
      Family.share(p, doc.optBoolean("familyLocation") && family.length() > 0);

    // Keystore-backed sections are re-encrypted with this phone's key; an unavailable
    // keystore must never abort the lists that are already restored.
    if (shield != null && !owner.isEmpty()) try {
      p.edit()
        .putString(
          CallRules.key(owner),
          Store.enc(
            Store.obj("enabled", shield.optBoolean("enabled"), "people", callersObject(callers)).toString()
          )
        )
        .apply();
    } catch (Exception e) {
      skipped += callers.size();
    }

    if (links != null) {
      for (Iterator<String> it = links.keys(); it.hasNext();) {
        String lid = it.next();
        Links.add(p, lid, links.optString(lid));
      }
    }

    if (doc.has("drafts")) p.edit().putString(Drafts.KEY, drafts.toString()).apply();
    if (followUps != null && !owner.isEmpty()) try {
      new FollowUps(s, owner).restore(followUps);
    } catch (Exception e) {
      skipped += followUps.length();
    }

    p.edit().putString(Routine.KEY, Routine.fresh().toString()).apply();
    Routine target = Routine.get(p);
    int lost = 0;
    if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
      JSONObject block = blocks.optJSONObject(i);
      if (block == null) continue;
      try {
        target.put(new JSONObject(block.toString()));
      } catch (IllegalArgumentException e) {
        lost++;
      }
    }
    if (routine != null) {
      target.doc.put("enabled", routine.optBoolean("enabled", true));
      if (skip != null) {
        JSONArray clean = new JSONArray();
        for (int i = 0; i < skip.length() && clean.length() < 60; i++) {
          String date = skip.optString(i);
          if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) clean.put(date);
        }
        target.doc.put("skip", clean);
      }
      if (blockSkips != null) {
        JSONArray clean = new JSONArray();
        for (int i = 0; i < blockSkips.length() && clean.length() < 120; i++) {
          JSONObject row = blockSkips.optJSONObject(i);
          if (row == null) continue;
          String id = row.optString("id"), date = row.optString("date");
          if (id.isEmpty() || id.length() > 40 || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) continue;
          clean.put(Store.obj("id", id, "date", date));
        }
        target.doc.put("blockSkips", clean);
      }
      target.save(p);
    }
    Routine.pause(p, doc.optBoolean("routinePaused", false));

    StringBuilder done = new StringBuilder();
    int numbers = priority.length() + ignore.length() + family.length();
    done.append(numbers).append(numbers == 1 ? " number" : " numbers");
    done.append(" · ").append(target.blocks().length()).append(
      target.blocks().length() == 1 ? " block" : " blocks"
    );
    if (drafts.length() > 0) done.append(" · ").append(drafts.length()).append(" drafts");
    if (skipped + lost > 0) done.append(" · ").append(skipped + lost).append(" skipped");
    if (keyGap) done.append(" · add your AI key again");
    return done.toString();
  }

  private static JSONObject callersObject(TreeMap<String, String> people) throws JSONException {
    JSONObject out = new JSONObject();
    for (Map.Entry<String, String> entry : people.entrySet())
      out.put(entry.getKey(), entry.getValue());
    return out;
  }

  private static TreeMap<String, String> callers(JSONObject people) {
    TreeMap<String, String> out = new TreeMap<String, String>();
    if (people == null) return out;
    for (Iterator<String> it = people.keys(); it.hasNext() && out.size() < 50;) {
      String key = it.next();
      String n = digits(key);
      if (n == null) continue;
      out.put(n, text(people.optString(key), 40));
    }
    return out;
  }

  private static JSONArray people(JSONArray source, int max, String label) throws JSONException {
    JSONArray out = new JSONArray();
    if (source == null) return out;
    if (source.length() > max)
      throw new IllegalArgumentException("Too many " + label + " numbers in this file.");
    for (int i = 0; i < source.length(); i++) {
      JSONObject row = source.optJSONObject(i);
      if (row == null) continue;
      String n = digits(row.optString("n"));
      if (n == null) continue;
      out.put(Store.obj("n", n, "name", text(row.optString("name"), 40)));
    }
    return out;
  }

  private static JSONArray drafts(JSONArray source) throws JSONException {
    JSONArray out = new JSONArray();
    if (source == null) return out;
    long now = System.currentTimeMillis();
    for (int i = 0; i < source.length() && out.length() < 60; i++) {
      JSONObject row = source.optJSONObject(i);
      if (row == null) continue;
      String id = text(row.optString("id"), 80);
      String jid = text(row.optString("jid"), 60);
      if (id.isEmpty() || jid.isEmpty()) continue;
      long at = row.optLong("at", now);
      out.put(
        Store.obj(
          "id", id,
          "jid", jid,
          "sender", text(row.optString("sender"), 80),
          "text", text(row.optString("text"), 600),
          "category", text(row.optString("category", "other"), 20),
          "urgent", row.optBoolean("urgent", false),
          "draft", text(row.optString("draft"), 400),
          "at", at > now + 86400000L || at < 0 ? now : at
        )
      );
    }
    return out;
  }

  private static JSONArray followUps(JSONArray source) throws JSONException {
    if (source == null) return null;
    JSONArray out = new JSONArray();
    for (int i = 0; i < source.length() && out.length() < 50; i++) {
      JSONObject row = source.optJSONObject(i);
      if (row == null) continue;
      String kind = row.optString("kind"), body = text(row.optString("text"), 600);
      String sender = text(row.optString("sender"), 80);
      if (!kind.matches("callback|urgent") || sender.isEmpty() || body.isEmpty()) continue;
      long time = row.optLong("time", System.currentTimeMillis());
      out.put(
        Store.obj(
          "id", text(row.optString("id"), 64).isEmpty()
            ? UUID.randomUUID().toString()
            : text(row.optString("id"), 64),
          "kind", kind,
          "sender", sender,
          "text", body,
          "time", time < 0 ? System.currentTimeMillis() : time,
          "done", row.optBoolean("done", false)
        )
      );
    }
    return out;
  }

  /** Digits only, or null when it cannot be a phone number. */
  private static String digits(String value) {
    String n = (value == null ? "" : value).replaceAll("[ ()+-]", "");
    return n.matches("[1-9][0-9]{6,14}") ? n : null;
  }

  /** One clean line, no control characters, bounded length. */
  private static String text(String value, int max) {
    String clean = (value == null ? "" : value)
      .replaceAll("[\\u0000-\\u001f\\u007f]+", " ")
      .replaceAll("\\s+", " ")
      .trim();
    return clean.length() > max ? clean.substring(0, max) : clean;
  }
}
