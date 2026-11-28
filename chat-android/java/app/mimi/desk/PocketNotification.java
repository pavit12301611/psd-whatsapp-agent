package app.mimi.desk;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

/** One little bunny card for every notification Mimi posts. */
final class PocketNotification {

  static final int PURPLE = 0xff604498, BLUE = 0xff4c6fb1, INDIGO = 0xff6a5aa8,
    ROSE = 0xffc65b86, RED = 0xffc0392b, MINT = 0xff3f8f6b;

  private static int id(Context c, String name, String type) {
    return c.getResources().getIdentifier(name, type, c.getPackageName());
  }

  static Notification build(
    Context c,
    String channel,
    String title,
    String text,
    String foot,
    long countdownAt,
    int accent,
    PendingIntent open,
    boolean ongoing,
    List<Notification.Action> actions
  ) {
    RemoteViews view = new RemoteViews(c.getPackageName(), id(c, "pocket_notification", "layout"));
    view.setTextViewText(id(c, "pocket_title", "id"), title);
    view.setTextViewText(id(c, "pocket_text", "id"), text == null ? "" : text);
    int footId = id(c, "pocket_foot", "id");
    if (foot == null || foot.isEmpty()) view.setViewVisibility(footId, View.GONE);
    else view.setTextViewText(footId, foot);
    int clock = id(c, "pocket_clock", "id");
    if (countdownAt > 0) {
      view.setViewVisibility(clock, View.VISIBLE);
      view.setChronometer(clock, countdownAt, null, true);
      if (Build.VERSION.SDK_INT >= 24) view.setChronometerCountDown(clock, true);
    } else view.setViewVisibility(clock, View.GONE);

    Notification.Builder builder = newNotification(c, channel);
    builder
      .setSmallIcon(id(c, "pocket_note", "drawable"))
      .setContentTitle(title)
      .setContentText(text)
      .setColor(accent)
      .setStyle(new Notification.BigTextStyle().bigText(text == null ? "" : text))
      .setCustomContentView(view)
      .setCustomBigContentView(view)
      .setContentIntent(open)
      .setAutoCancel(!ongoing)
      .setOngoing(ongoing)
      .setCategory(ongoing ? Notification.CATEGORY_SERVICE : Notification.CATEGORY_MESSAGE);
    if (countdownAt > 0) builder
      .setWhen(countdownAt)
      .setUsesChronometer(true)
      .setShowWhen(true);
    if (Build.VERSION.SDK_INT >= 24) builder.setChronometerCountDown(countdownAt > 0);
    if (actions != null) for (Notification.Action action : actions) builder.addAction(action);
    return builder.build();
  }

  private static Notification.Builder newNotification(Context c, String channel) {
    return new Notification.Builder(c, channel);
  }

  static Notification.Action action(CharSequence label, PendingIntent intent) {
    return new Notification.Action.Builder(null, label, intent).build();
  }
}
