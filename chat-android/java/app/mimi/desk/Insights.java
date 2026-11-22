package app.mimi.desk;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.*;

/** "Where did my week go?" — built only from this phone's own block log. */
final class Insights {

  static String label(Routine r, String id) {
    for (int i = 0; i < r.blocks().length(); i++) {
      org.json.JSONObject b = r.blocks().optJSONObject(i);
      if (b != null && id.equals(b.optString("id"))) return b.optString("name");
    }
    return "Available";
  }

  static String duration(long seconds) {
    long m = seconds / 60;
    if (m < 1) return "<1m";
    if (m < 60) return m + "m";
    return (m / 60) + "h " + String.format(Locale.US, "%02d", m % 60) + "m";
  }

  private static long total(LinkedHashMap<String, long[]> day) {
    long s = 0;
    for (long[] v : day.values()) s += v[0];
    return s;
  }

  static void show(final ChatDesk a) {
    final long now = System.currentTimeMillis();
    final Store s = a.store;
    final Routine routine = Routine.get(s.p);
    ScrollView scroll = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    scroll.addView(panel);

    Calendar cal = Calendar.getInstance();
    String today = Routine.date(cal);
    final LinkedHashMap<String, long[]> live = TimeLog.day(s.p, today, now);
    a.text(panel, "TODAY", 11, a.purple, true);
    if (live.isEmpty()) a.text(panel, "Nothing logged yet. Turn the routine on to start tracking.", 13, a.muted, false);
    long max = 1;
    for (long[] v : live.values()) max = Math.max(max, v[0]);
    List<Map.Entry<String, long[]>> sorted = new ArrayList<Map.Entry<String, long[]>>(live.entrySet());
    Collections.sort(sorted, (x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
    for (Map.Entry<String, long[]> e : sorted) {
      row(a, panel, label(routine, e.getKey()), duration(e.getValue()[0]), e.getValue()[0], max);
    }
    a.gap(panel, 12);

    List<String> days = TimeLog.dates(7, now);
    long week = 0;
    Map<String, Long> perBlock = new LinkedHashMap<String, Long>();
    Map<String, Long> perDay = new LinkedHashMap<String, Long>();
    long dayMax = 1;
    for (String d : days) {
      LinkedHashMap<String, long[]> t = TimeLog.day(s.p, d, now);
      long sum = total(t);
      perDay.put(d, sum);
      dayMax = Math.max(dayMax, sum);
      week += sum;
      for (Map.Entry<String, long[]> e : t.entrySet()) {
        Long had = perBlock.get(e.getKey());
        perBlock.put(e.getKey(), (had == null ? 0 : had) + e.getValue()[0]);
      }
    }
    a.text(panel, "THIS WEEK · " + duration(week), 11, a.purple, true);
    for (Map.Entry<String, Long> e : perDay.entrySet()) {
      String day = e.getKey().substring(5);
      row(a, panel, day, duration(e.getValue()), e.getValue(), dayMax);
    }
    a.gap(panel, 12);
    a.text(panel, "BY BLOCK", 11, a.purple, true);
    if (perBlock.isEmpty()) a.text(panel, "No blocks logged yet.", 13, a.muted, false);
    List<Map.Entry<String, Long>> blocks = new ArrayList<Map.Entry<String, Long>>(perBlock.entrySet());
    Collections.sort(blocks, (x, y) -> Long.compare(y.getValue(), x.getValue()));
    for (Map.Entry<String, Long> e : blocks) a.text(panel, label(routine, e.getKey()) + " · " + duration(e.getValue()), 14, a.ink, false);
    a.gap(panel, 10);
    a.text(panel, "Counted only while Mimi's chat agent is running on this phone. Export is a plain text file you choose where to send.", 12, a.muted, false);

    AlertDialog d = new PocketDialog(a, PocketDialog.Room.INSIGHTS)
      .setTitle("Time insights")
      .setView(scroll)
      .setNegativeButton("Close", null)
      .setNeutralButton("Export CSV", null)
      .create();
    d.show();
    d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("text/csv");
      send.putExtra(Intent.EXTRA_SUBJECT, "Mimi time tracking");
      send.putExtra(Intent.EXTRA_TEXT, csv(s, routine, now));
      a.startActivity(Intent.createChooser(send, "Export Mimi time log"));
    });
  }

  static String csv(Store s, Routine routine, long now) {
    StringBuilder out = new StringBuilder("date,block,minutes\n");
    for (String d : TimeLog.dates(28, now)) {
      LinkedHashMap<String, long[]> t = TimeLog.day(s.p, d, now);
      for (Map.Entry<String, long[]> e : t.entrySet())
        out.append(d).append(',').append('"').append(label(routine, e.getKey()).replace("\"", "")).append('"').append(',').append(e.getValue()[0] / 60).append('\n');
    }
    return out.toString();
  }

  private static void row(ChatDesk a, LinearLayout panel, String name, String value, long seconds, long max) {
    LinearLayout line = new LinearLayout(a);
    line.setOrientation(LinearLayout.VERTICAL);
    line.setPadding(0, a.dp(4), 0, a.dp(4));
    LinearLayout top = new LinearLayout(a);
    TextView left = new TextView(a);
    left.setText(name);
    left.setTextSize(14);
    left.setTextColor(a.ink);
    TextView right = new TextView(a);
    right.setText(value);
    right.setTextSize(13);
    right.setTextColor(a.muted);
    top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(right);
    line.addView(top);
    ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(1000);
    bar.setProgress((int) Math.max(seconds == 0 ? 0 : 12, Math.min(1000, seconds * 1000 / Math.max(1, max))));
    bar.getProgressDrawable().setColorFilter(a.purple, android.graphics.PorterDuff.Mode.SRC_IN);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(8));
    lp.topMargin = a.dp(4);
    line.addView(bar, lp);
    panel.addView(line);
  }
}
