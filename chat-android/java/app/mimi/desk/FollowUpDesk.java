package app.mimi.desk;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

final class FollowUpDesk {

  final ChatDesk a;
  final FollowUps queue;
  final String[] filters = {
    "Pending",
    "Urgent · pending",
    "Callbacks · pending",
    "Done",
  };
  int filter;

  FollowUpDesk(ChatDesk a, String owner) {
    this.a = a;
    queue = new FollowUps(a.store, owner);
  }

  void error() {
    Toast.makeText(
      a,
      "Could not read or save the private list. Your WhatsApp notes are separate.",
      Toast.LENGTH_LONG
    ).show();
  }

  void secure(AlertDialog d) {
    d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    d.show();
  }

  void show() {
    if (!queue.enabled()) {
      secure(
        new AlertDialog.Builder(a)
          .setTitle("Private follow-ups")
          .setMessage(
            "Keep the latest 50 callback and urgent requests here? Sender, time and up to 600 characters per request are encrypted on this phone. Regular chats are not copied. Only future requests are included. Without this list, tagged notes still go to your WhatsApp self-chat."
          )
          .setNegativeButton("Not now", null)
          .setPositiveButton("Enable list", (d, k) -> {
            queue.enable();
            show();
          })
          .create()
      );
      return;
    }
    LinearLayout l = a.box(0xfff8f6fc, 16);
    a.text(
      l,
      "Only requests explicitly tagged by the sender.",
      14,
      a.muted,
      false
    );
    Spinner select = new Spinner(a);
    select.setAdapter(
      new ArrayAdapter<String>(
        a,
        android.R.layout.simple_spinner_dropdown_item,
        filters
      )
    );
    select.setSelection(filter);
    l.addView(select);
    ListView list = new ListView(a);
    l.addView(list, new LinearLayout.LayoutParams(-1, a.dp(256)));
    ArrayList<JSONObject> visible = new ArrayList<>();
    Runnable refresh = () -> {
      visible.clear();
      ArrayList<String> labels = new ArrayList<>();
      try {
        JSONArray rows = queue.read();
        for (int i = rows.length() - 1; i >= 0; i--) {
          JSONObject r = rows.getJSONObject(i);
          boolean done = r.getBoolean("done");
          String kind = r.getString("kind");
          if (
            (filter == 3) != done ||
            (filter == 1 && !kind.equals("urgent")) ||
            (filter == 2 && !kind.equals("callback"))
          ) continue;
          visible.add(r);
          String body = r.getString("text").replaceAll("\\s+", " ");
          labels.add(
            kind.toUpperCase(Locale.ROOT) +
              " · " +
              r.getString("sender") +
              "\n" +
              (body.length() > 70 ? body.substring(0, 70) + "…" : body)
          );
        }
      } catch (Exception e) {
        error();
      }
      if (labels.isEmpty()) labels.add(
        "No " + filters[filter].toLowerCase(Locale.ROOT) + " requests."
      );
      list.setAdapter(
        new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, labels)
      );
    };
    refresh.run();
    select.setOnItemSelectedListener(
      new AdapterView.OnItemSelectedListener() {
        public void onNothingSelected(AdapterView<?> p) {}

        public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
          filter = n;
          refresh.run();
        }
      }
    );
    AlertDialog desk = new AlertDialog.Builder(a)
      .setTitle("Follow-up inbox")
      .setView(l)
      .setPositiveButton("Close", null)
      .setNeutralButton("Turn off…", (d, k) -> disable())
      .setNegativeButton("Refresh", null)
      .create();
    secure(desk);
    desk.getButton(-2).setOnClickListener(v -> refresh.run());
    list.setOnItemClickListener((p, v, i, id) -> {
      if (i >= visible.size()) return;
      JSONObject row = visible.get(i);
      desk.dismiss();
      detail(row);
    });
  }

  void disable() {
    secure(
      new AlertDialog.Builder(a)
        .setTitle("Turn off and erase local follow-ups?")
        .setMessage(
          "This deletes the list for this account. WhatsApp messages will not be deleted."
        )
        .setPositiveButton("Erase & turn off", (d, k) -> queue.forget())
        .setNegativeButton("Cancel", (d, k) -> show())
        .create()
    );
  }

  void detail(JSONObject row) {
    String sender = row.optString("sender"),
      id = row.optString("id");
    boolean done = row.optBoolean("done");
    String date = DateFormat.getDateTimeInstance(
      DateFormat.MEDIUM,
      DateFormat.SHORT
    ).format(new Date(row.optLong("time")));
    LinearLayout l = a.box(0xffffffff, 16);
    a.text(l, sender + " · " + date, 13, a.muted, true);
    a.gap(l, 8);
    a.text(l, row.optString("text"), 16, a.ink, false);
    // A private WhatsApp LID must not be guessed to be a phone number.
    if (sender.matches("\\+[1-9][0-9]{6,14}")) a.button(
      l,
      "Open WhatsApp chat",
      () -> {
        try {
          a.startActivity(
            new Intent(
              Intent.ACTION_VIEW,
              Uri.parse("https://wa.me/" + sender.substring(1))
            ).setPackage("com.whatsapp.w4b")
          );
        } catch (ActivityNotFoundException e) {
          Toast.makeText(
            a,
            "WhatsApp Business could not open this chat.",
            Toast.LENGTH_SHORT
          ).show();
        }
      }
    );
    ScrollView sc = new ScrollView(a);
    sc.addView(l);
    secure(
      new AlertDialog.Builder(a)
        .setTitle(
          row.optString("kind").equals("urgent")
            ? "Urgent note"
            : "Callback request"
        )
        .setView(sc)
        .setNegativeButton("Back", (d, k) -> show())
        .setPositiveButton(done ? "Reopen" : "Mark done", (d, k) -> {
          try {
            queue.change(id, !done, false);
          } catch (Exception e) {
            error();
          }
          show();
        })
        .setNeutralButton("Delete…", (d, k) ->
          secure(
            new AlertDialog.Builder(a)
              .setMessage("Delete this local request?")
              .setNegativeButton("Cancel", (x, y) -> show())
              .setPositiveButton("Delete", (x, y) -> {
                try {
                  queue.change(id, false, true);
                } catch (Exception e) {
                  error();
                }
                show();
              })
              .create()
          )
        )
        .create()
    );
  }
}
