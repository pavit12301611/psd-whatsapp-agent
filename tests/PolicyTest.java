package app.mimi.desk;

public class PolicyTest {

  public static void main(String[] a) {
    for (String mode : new String[] {
      "Available",
      "Sleeping",
      "Busy",
      "Driving",
    })
      for (boolean unknown : new boolean[] { true, false }) {
        if (Policy.invite(false, mode, unknown)) throw new AssertionError(
          "Paused"
        );
        if (
          Policy.invite(true, mode, unknown) !=
          (unknown || !mode.equals("Available"))
        ) throw new AssertionError(mode);
      }
    if (Policy.group(true, false, true) || !Policy.group(true, true, false)
      || !Policy.group(false, false, true) || Policy.group(false, false, false))
      throw new AssertionError("Conversation group classification");
    if (
      Policy.fresh(1, 200000) ||
      Policy.fresh(200000, 1) ||
      !Policy.fresh(199999, 200000)
    ) throw new AssertionError("freshness");
    if (
      Policy.cooled(10, 11) ||
      !Policy.cooled(10, 21600010) ||
      !Policy.cooled(0, 10)
    ) throw new AssertionError("cooldown");
    System.out.println("Routing, freshness and cooldown checks passed");
  }
}
