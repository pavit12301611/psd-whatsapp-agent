package app.mimi.desk;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared chrome for the native forms. Keep the platform's dialog, IME and button semantics. */
final class PocketDialog extends AlertDialog.Builder {
  enum Room {
    FOCUS("FOCUS TIME", 0xffffead4), SLEEP("DREAM DEN", 0xffeae2fc),
    STUDIO("REPLY STUDIO", 0xfff9e2ed), ACCOUNT("MY ACCOUNT", 0xffdff0f3),
    SHIELD("CALL SHIELD", 0xffdff0e4), INBOX("NOTE NOOK", 0xffffefcf),
    SCHEDULE("WEEKLY ROUTINE", 0xffe4ecfb), INSIGHTS("TIME INSIGHTS", 0xffe2f4ea),
    CONTACTS("PRIORITY CIRCLE", 0xffffe6d8), CONFIRM("PLEASE CONFIRM", 0xfffbe3e4),
    BACKUP("BACKUP & RESTORE", 0xffe7eefc);
    final String label;
    final int wash;
    Room(String label, int wash) { this.label = label; this.wash = wash; }
  }

  private final ChatDesk a;
  private final Room room;

  PocketDialog(ChatDesk a, Room room) {
    super(a);
    this.a = a;
    this.room = room;
  }

  static LinearLayout header(ChatDesk a, Room room, CharSequence title) {
    LinearLayout row = new LinearLayout(a);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10));
    GradientDrawable wash = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[] {room.wash, 0xfffffcfa});
    wash.setCornerRadius(a.dp(21));
    row.setBackground(wash);
    ImageView mimi = new ImageView(a);
    mimi.setImageResource(a.getResources().getIdentifier("pocket_bunny", "drawable", a.getPackageName()));
    mimi.setScaleX(1.45f); mimi.setScaleY(1.45f);
    mimi.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(mimi, a.dp(44), a.dp(44));
    LinearLayout labels = new LinearLayout(a);
    labels.setOrientation(LinearLayout.VERTICAL);
    labels.setPadding(a.dp(8), 0, 0, 0);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    TextView caption = a.text(labels, "MIMI  /  " + room.label, 10, a.purple, true);
    caption.setLetterSpacing(.09f);
    TextView heading = a.text(labels, title.toString(), 20, a.ink, true);
    heading.setAccessibilityHeading(true);
    return row;
  }

  @Override public PocketDialog setTitle(CharSequence title) {
    setCustomTitle(header(a, room, title));
    return this;
  }

  @Override public AlertDialog create() {
    AlertDialog d = super.create();
    decorate(a, d, room);
    return d;
  }

  static void decorate(ChatDesk a, AlertDialog d, Room room) {
    Window w = d.getWindow();
    w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    d.setOnShowListener(ignored -> {
      int available = a.getWindow().getDecorView().getWidth();
      if (available > a.dp(40)) w.setLayout(Math.min(a.dp(492), available - a.dp(28)), -2);
      w.getDecorView().setClipToOutline(true);
      for (int role : new int[]{DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL}) {
        Button b = d.getButton(role);
        if (b == null || b.getVisibility() != View.VISIBLE) continue;
        boolean primary = role == DialogInterface.BUTTON_POSITIVE;
        int tone = room == Room.CONFIRM ? 0xff974355 : a.purple;
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : tone);
        b.setBackgroundTintList(null);
        b.setBackground(a.surface(primary ? tone : 0xfffaf5fc, primary ? tone : 0xffdfd3e6));
        b.setMinHeight(a.dp(48)); b.setPadding(a.dp(10), a.dp(5), a.dp(10), a.dp(5));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      }
    });
  }

  static ArrayAdapter<String> rows(ChatDesk a, Room room, java.util.List<String> labels) {
    return new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, labels) {
      @Override public View getView(int pos, View recycled, ViewGroup parent) {
        LinearLayout wrap = (LinearLayout) recycled;
        if (wrap == null) {
          wrap = new LinearLayout(a);
          wrap.setPadding(a.dp(6), a.dp(3), a.dp(6), a.dp(3));
          TextView t = a.text(wrap, "", 15, a.ink, false);
          t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
          t.setGravity(Gravity.CENTER_VERTICAL);
          t.setMinHeight(a.dp(48));
          t.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8));
          t.setBackground(a.bg(room.wash, 12));
        }
        ((TextView) wrap.getChildAt(0)).setText(getItem(pos));
        return wrap;
      }
    };
  }

  static EditText input(ChatDesk a) {
    EditText e = new EditText(a);
    e.setTextSize(16); e.setTextColor(a.ink); e.setHintTextColor(a.muted);
    e.setMinHeight(a.dp(48));
    GradientDrawable normal = a.bg(0xfffaf7fc, 12), focus = a.bg(Color.WHITE, 12);
    normal.setStroke(a.dp(1), 0xffd4c7df); focus.setStroke(a.dp(2), a.purple);
    android.graphics.drawable.StateListDrawable outline = new android.graphics.drawable.StateListDrawable();
    outline.addState(new int[]{android.R.attr.state_focused}, focus);
    outline.addState(new int[]{}, normal);
    e.setBackgroundTintList(null); e.setBackground(outline);
    e.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
    return e;
  }

  static Spinner menu(ChatDesk a) {
    Spinner s = new Spinner(a, Spinner.MODE_DROPDOWN);
    s.setMinimumHeight(a.dp(48));
    s.setBackgroundTintList(ColorStateList.valueOf(a.purple));
    GradientDrawable menu = a.bg(0xfffffafd, 14);
    menu.setStroke(a.dp(1), 0xffd4c7df);
    s.setPopupBackgroundDrawable(menu);
    return s;
  }
}
