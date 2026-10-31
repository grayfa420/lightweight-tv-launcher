package com.cleanhome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Which apps the home screen shows, in order. Starts as the built-in defaults until edited. */
final class AppList {
    private static final String PREFS = "home";
    private static final String KEY = "apps";

    private AppList() {
    }

    static List<String> load(Context ctx, String[] defaults) {
        String saved = prefs(ctx).getString(KEY, null);
        if (saved == null) return new ArrayList<>(Arrays.asList(defaults));
        List<String> list = new ArrayList<>();
        if (!saved.isEmpty()) list.addAll(Arrays.asList(saved.split(",")));
        return list;
    }

    /** Adds the app at the end, or removes it if already shown. Returns whether it's now shown. */
    static boolean toggle(Context ctx, String[] defaults, String pkg) {
        List<String> list = load(ctx, defaults);
        boolean shown = !list.remove(pkg);
        if (shown) list.add(pkg);
        prefs(ctx).edit().putString(KEY, TextUtils.join(",", list)).apply();
        return shown;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
