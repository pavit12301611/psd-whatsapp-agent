package app.mimi.desk;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import java.util.List;
import org.json.*;

/** Newest fix this phone already knows about. No continuous tracking, no uploads. */
final class Locator {

  static boolean allowed(Context c) {
    return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
      || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  static JSONObject last(Context c) {
    if (!allowed(c)) return null;
    LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
    if (lm == null) return null;
    Location best = null;
    List<String> providers = lm.getProviders(true);
    for (String provider : providers) {
      try {
        Location fix = lm.getLastKnownLocation(provider);
        if (fix == null) continue;
        if (best == null || fix.getTime() > best.getTime()) best = fix;
      } catch (SecurityException ignored) {
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (best == null) return null;
    try {
      return Store.obj(
        "lat", best.getLatitude(),
        "lng", best.getLongitude(),
        "accuracy", best.hasAccuracy() ? best.getAccuracy() : 0,
        "age", Math.max(0, (System.currentTimeMillis() - best.getTime()) / 60000)
      );
    } catch (JSONException e) {
      return null;
    }
  }
}
