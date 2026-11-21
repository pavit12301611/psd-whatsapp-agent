package app.mimi.desk;

import android.content.SharedPreferences;

/** Desired availability is owner-scoped and independent of socket/service lifetime. */
final class Availability {

  final String mode, reason, language;
  final long until;
  final boolean share;

  Availability(String m, String why, long end, boolean disclose, String lang) {
    mode = m;
    reason = why;
    until = end;
    share = disclose;
    language = lang;
  }

  String publicReason() {
    return share ? reason : "";
  }

  static final Availability OFF = new Availability("off", "", 0, false, "en");

  static String prefix(String owner) {
    return "availability." + owner + ".";
  }

  static synchronized void forget(SharedPreferences p, String owner) {
    String k = prefix(owner);
    p.edit()
      .remove(k + "mode")
      .remove(k + "end")
      .remove(k + "reason")
      .remove(k + "share")
      .remove(k + "language")
      .apply();
  }

  static synchronized Availability get(
    SharedPreferences p,
    String owner,
    long now
  ) {
    String k = prefix(owner),
      m = p.getString(k + "mode", "off");
    long end = p.getLong(k + "end", 0);
    if (!m.matches("busy|sleeping|driving")) return OFF;
    if (m.equals("sleeping") && end != 0 && end <= now) {
      forget(p, owner);
      return OFF;
    }
    boolean busy = m.equals("busy");
    return new Availability(
      m,
      busy ? p.getString(k + "reason", "") : "",
      m.equals("sleeping") ? end : 0,
      busy && p.getBoolean(k + "share", false),
      p.getString(k + "language", "en")
    );
  }

  static synchronized void set(
    SharedPreferences p,
    String owner,
    String m,
    long duration,
    String reason,
    boolean share,
    long now
  ) {
    if (
      !owner.matches("[1-9][0-9]{6,14}") ||
      !m.matches("busy|sleeping|driving|off")
    ) throw new IllegalArgumentException();
    if (
      duration < 0 || duration > 25L * 3600000
    ) throw new IllegalArgumentException();
    String k = prefix(owner);
    String clean = m.equals("busy")
      ? reason.replaceAll("[\\p{Cntrl}\\s]+", " ").trim()
      : "";
    if (clean.length() > 120) clean = clean.substring(0, 120);
    p.edit()
      .putString(k + "mode", m)
      .putString(k + "reason", clean)
      .putBoolean(k + "share", share && !clean.isEmpty())
      .putLong(
        k + "end",
        m.equals("sleeping") && duration > 0 ? now + duration : 0
      )
      .putString(k + "language", p.getString("chat.language", "en"))
      .apply();
  }
}
