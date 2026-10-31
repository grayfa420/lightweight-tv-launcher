package com.cleanhome.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating panel listing every installed app; OK adds it to the home screen or removes it.
 * Changes are saved immediately and the home screen picks them up when it comes back.
 */
public class AppPickerActivity extends Activity {

    private static final class App {
        final String pkg;
        final CharSequence label;
        final ResolveInfo info;

        App(String pkg, CharSequence label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    private View panel;
    private View dim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        dim = new View(this);
        dim.setBackgroundColor(0xB3000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.glass(this, 22);
        bg.setColor(0xF2101420);
        box.setBackground(bg);
        box.setElevation(dp(30));
        box.setPadding(dp(28), dp(24), dp(28), dp(16));
        panel = box;

        TextView title = text(22, Ui.TEXT);
        title.setText("Aplikacije na začetnem zaslonu");
        box.addView(title);
        TextView hint = text(14, Ui.TEXT_MUTED);
        hint.setText("OK doda ali odstrani aplikacijo  ·  Nazaj zapre");
        hint.setPadding(0, dp(4), 0, dp(14));
        box.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(6), 0, dp(6));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        scroll.addView(rows);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        List<String> shown = AppList.load(this, HomeActivity.APPS);
        for (App app : installedApps()) {
            rows.addView(row(app, shown.contains(app.pkg)));
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(560), -1, Gravity.CENTER);
        lp.topMargin = dp(40);
        lp.bottomMargin = dp(40);
        root.addView(box, lp);
        setContentView(root);

        // Panel rises in on a spring while the background dims.
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(Motion.FADE_MS).start();
        panel.setAlpha(0f);
        panel.setTranslationY(dp(40));
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                .setInterpolator(Motion.SPRING).setDuration(Motion.SPRING_MS).start();
    }

    @Override
    public void onBackPressed() {
        dim.animate().alpha(0f).setDuration(200).start();
        panel.animate().alpha(0f).translationY(dp(24)).setInterpolator(Motion.EASE_OUT)
                .setDuration(200).withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    /** Every app with a TV or phone launcher entry (TV label preferred), except this launcher. */
    private List<App> installedApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && !byPkg.containsKey(pkg)) byPkg.put(pkg, ri);
            }
        }
        List<App> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : byPkg.entrySet()) {
            apps.add(new App(e.getKey(), e.getValue().loadLabel(pm), e.getValue()));
        }
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        return apps;
    }

    private View row(App app, boolean initiallyShown) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setFocusable(true);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dp(12));
        rowBg.setColor(0);
        row.setBackground(rowBg);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.info.loadIcon(getPackageManager()));
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = text(16, Ui.TEXT);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setPadding(dp(16), 0, dp(12), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        // Filled white circle with a check when shown, faint ring when not.
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check);
        int pad = dp(4);
        check.setPadding(pad, pad, pad, pad);
        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.OVAL);
        check.setBackground(checkBg);
        row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));
        boolean[] shown = {initiallyShown};
        Runnable paintCheck = () -> {
            checkBg.setColor(shown[0] ? Ui.TEXT : 0);
            checkBg.setStroke(shown[0] ? 0 : dp(2), 0x66FFFFFF);
            check.setImageTintList(ColorStateList.valueOf(Ui.DARK));
            check.setImageAlpha(shown[0] ? 255 : 0);
        };
        paintCheck.run();

        row.setOnFocusChangeListener((v, has) -> {
            rowBg.setColor(has ? 0x2EFFFFFF : 0);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setInterpolator(has ? Motion.SPRING : Motion.SETTLE)
                    .setDuration(has ? Motion.SPRING_MS : Motion.FADE_MS).start();
        });
        row.setOnClickListener(v -> {
            shown[0] = AppList.toggle(this, HomeActivity.APPS, app.pkg);
            paintCheck.run();
            check.setScaleX(0.6f);
            check.setScaleY(0.6f);
            check.animate().scaleX(1f).scaleY(1f).setInterpolator(Motion.SPRING)
                    .setDuration(Motion.SPRING_MS).start();
        });
        return row;
    }

    private TextView text(float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
