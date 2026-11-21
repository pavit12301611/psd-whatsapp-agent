package app.mimi.desk;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Exercise the storage boundary with fresh preference handles over the same backing map. */
public class AvailabilityTest {

  static SharedPreferences preferences(Map<String, Object> data) {
    return (SharedPreferences) Proxy.newProxyInstance(
      AvailabilityTest.class.getClassLoader(),
      new Class[] { SharedPreferences.class },
      (p, m, args) -> {
        if (m.getName().equals("edit")) return Proxy.newProxyInstance(
          AvailabilityTest.class.getClassLoader(),
          new Class[] { SharedPreferences.Editor.class },
          (ed, op, kv) -> {
            if (op.getName().startsWith("put")) data.put((String) kv[0], kv[1]);
            else if (op.getName().equals("remove")) data.remove(kv[0]);
            else if (op.getName().equals("apply")) return null;
            else throw new AssertionError(op);
            return ed;
          }
        );
        if (m.getName().startsWith("get")) return data.getOrDefault(
          args[0],
          args[1]
        );
        throw new AssertionError(m);
      }
    );
  }

  public static void main(String[] args) {
    Map<String, Object> disk = new HashMap<>();
    String a = "919000000001",
      b = "919000000002";
    SharedPreferences p = preferences(disk);
    Availability.set(p, a, "busy", 0, " Meeting\n at noon  ", false, 1000);
    Availability selected = Availability.get(preferences(disk), a, 2000);
    assert selected.mode.equals("busy") &&
      selected.reason.equals("Meeting at noon") &&
      selected.publicReason().isEmpty();
    assert Availability.get(p, b, 2000).mode.equals("off");
    Availability.set(p, a, "busy", 0, "Meeting", true, 2000);
    assert Availability.get(p, a, 3000).publicReason().equals("Meeting");
    Availability.set(p, a, "sleeping", 60000, "", false, 5000);
    assert Availability.get(preferences(disk), a, 30000).until == 65000;
    assert Availability.get(p, a, 65000).mode.equals("off");
    assert Availability.get(p, a, 64000).mode.equals("off");
    Availability.set(p, a, "driving", 0, "", false, 70000);
    assert Availability.get(p, a, 80000).mode.equals("driving");
    Availability.set(p, a, "off", 0, "", false, 80000);
    assert Availability.get(preferences(disk), a, 90000).mode.equals("off");
    Availability.forget(p, a);
    assert disk.isEmpty();
    System.out.println("Availability storage checks passed");
  }
}
