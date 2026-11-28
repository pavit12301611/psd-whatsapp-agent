package app.mimi.desk;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.*;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

/** A PIN (optional fingerprint) in front of everything Mimi can read. */
final class Lock {

  static final String ENABLED = "lock.enabled", SALT = "lock.salt", HASH = "lock.hash", BIO = "lock.bio";

  static boolean enabled(SharedPreferences p) {
    return p.getBoolean(ENABLED, false) && !p.getString(HASH, "").isEmpty();
  }

  static boolean bio(SharedPreferences p) {
    return p.getBoolean(BIO, false);
  }

  private static String hash(String salt, String pin) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(Base64.decode(salt, Base64.NO_WRAP));
      return Base64.encodeToString(md.digest(("mimi:" + pin).getBytes("UTF-8")), Base64.NO_WRAP);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static void setPin(SharedPreferences p, String pin) {
    byte[] raw = new byte[16];
    new SecureRandom().nextBytes(raw);
    String salt = Base64.encodeToString(raw, Base64.NO_WRAP);
    p.edit().putString(SALT, salt).putString(HASH, hash(salt, pin)).putBoolean(ENABLED, true).apply();
  }

  static boolean verify(SharedPreferences p, String pin) {
    String salt = p.getString(SALT, "");
    if (salt.isEmpty() || pin == null) return false;
    return hash(salt, pin).equals(p.getString(HASH, ""));
  }

  static void disable(SharedPreferences p) {
    p.edit().remove(ENABLED).remove(SALT).remove(HASH).remove(BIO).apply();
  }

  private static final Runnable NOTHING = () -> {};

  /** Full-screen gate; runs `after` once the owner proves it is them. */
  static void gate(final ChatDesk a, final Runnable after) {
    final Store s = a.store;
    a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    LinearLayout root = new LinearLayout(a);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setBackgroundColor(0xfffffbf6);
    root.setPadding(a.dp(28), a.dp(28), a.dp(28), a.dp(28));

    ImageView bunny = new ImageView(a);
    bunny.setImageResource(a.getResources().getIdentifier("pocket_bunny", "drawable", a.getPackageName()));
    root.addView(bunny, a.dp(96), a.dp(96));
    TextView title = a.text(root, "Mimi is locked", 22, a.ink, true);
    title.setGravity(Gravity.CENTER);
    TextView why = a.text(root, "PIN daalo ya fingerprint se kholo.", 14, a.muted, false);
    why.setGravity(Gravity.CENTER);
    final EditText pin = PocketDialog.input(a);
    pin.setHint("PIN");
    pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    pin.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = a.dp(18);
    root.addView(pin, lp);
    Button unlock = a.button(root, "Unlock", () -> {});
    unlock.setOnClickListener(v -> {
      if (verify(s.p, pin.getText().toString())) {
        pin.setText("");
        after.run();
        return;
      }
      pin.setError("Galat PIN");
    });
    if (bio(s.p)) {
      Button finger = a.button(root, "Use fingerprint", () -> {});
      finger.setOnClickListener(v -> fingerprint(a, () -> {
        after.run();
      }));
    }
    TextView hint = a.text(root, "Bhool gaye? App uninstall ya settings se data clear karna padega — isliye PIN yaad rakhna.", 11, a.muted, false);
    hint.setGravity(Gravity.CENTER);
    a.setContentView(root);
  }

  private static void fingerprint(final ChatDesk a, final Runnable ok) {
    if (Build.VERSION.SDK_INT < 28) return;
    try {
      final Executor exec = a.getMainExecutor();
      BiometricPrompt prompt = new BiometricPrompt.Builder(a)
        .setTitle("Unlock Mimi")
        .setNegativeButton("Cancel", exec, (dialog, which) -> {})
        .build();
      prompt.authenticate(
        new CancellationSignal(),
        exec,
        new BiometricPrompt.AuthenticationCallback() {
          @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            ok.run();
          }
        }
      );
    } catch (Exception ignored) {
      Toast.makeText(a, "Fingerprint abhi available nahi hai — PIN use karo.", Toast.LENGTH_SHORT).show();
    }
  }

  static void setup(final ChatDesk a) {
    final Store s = a.store;
    final boolean on = enabled(s.p);
    ScrollView sc = new ScrollView(a);
    LinearLayout panel = a.box(Color.WHITE, 16);
    sc.addView(panel);
    a.text(panel, "App kholte hi PIN maangega — Mimi ke paas tumhare messages hain.", 13, a.muted, false);
    final EditText first = PocketDialog.input(a);
    first.setHint("New PIN (4–8 digits)");
    first.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    panel.addView(first);
    final EditText again = PocketDialog.input(a);
    again.setHint("PIN dobara");
    again.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    panel.addView(again);
    final CheckBox bio = new CheckBox(a);
    bio.setText("Fingerprint se bhi khol sakte ho");
    bio.setTextColor(a.ink);
    bio.setChecked(bio(s.p));
    panel.addView(bio);
    AlertDialog d = new PocketDialog(a, PocketDialog.Room.ACCOUNT)
      .setTitle(on ? "Change app PIN" : "Set app PIN")
      .setView(sc)
      .setNegativeButton("Cancel", null)
      .setPositiveButton(on ? "Change" : "Turn on", null)
      .create();
    if (on) d.setButton(AlertDialog.BUTTON_NEUTRAL, "Turn off", (android.content.DialogInterface.OnClickListener) null);
    d.show();
    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      String pin = first.getText().toString().trim();
      if (!pin.matches("[0-9]{4,8}")) {
        first.setError("4–8 digits");
        return;
      }
      if (!pin.equals(again.getText().toString().trim())) {
        again.setError("PIN match nahi kar raha");
        return;
      }
      setPin(s.p, pin);
      s.p.edit().putBoolean(BIO, bio.isChecked()).apply();
      Toast.makeText(a, "App lock on ho gaya.", Toast.LENGTH_SHORT).show();
      d.dismiss();
    });
    if (on) d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      disable(s.p);
      Toast.makeText(a, "App lock off.", Toast.LENGTH_SHORT).show();
      d.dismiss();
    });
  }
}
