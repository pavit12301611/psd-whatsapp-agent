package app.mimi.desk;

final class Policy {

  static boolean invite(boolean enabled, String mode, boolean knownUnsaved) {
    return (
      enabled &&
      (knownUnsaved ||
        "Busy".equals(mode) ||
        "Sleeping".equals(mode) ||
        "Driving".equals(mode))
    );
  }

  static boolean fresh(long time, long now) {
    return time <= now + 5000 && time >= now - 120000;
  }

  static boolean cooled(long last, long now) {
    return last == 0 || now - last >= 6 * 3600000L;
  }
}
