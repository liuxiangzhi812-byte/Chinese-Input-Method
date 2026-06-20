package com.mercury.chinesepinyinime;

import android.content.Context;
import android.content.SharedPreferences;

public final class KeyboardLayoutPreferences {
    private static final String PREFS_NAME = "chinese_pinyin_ime_prefs";
    private static final String KEY_LAYOUT_MODE = "keyboard_layout_mode";
    private static final String VALUE_T9 = "9";
    private static final String VALUE_QWERTY = "26";

    private KeyboardLayoutPreferences() {
    }

    public static boolean isT9Enabled(Context context) {
        return VALUE_T9.equals(prefs(context).getString(KEY_LAYOUT_MODE, VALUE_QWERTY));
    }

    public static void setT9Enabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putString(KEY_LAYOUT_MODE, enabled ? VALUE_T9 : VALUE_QWERTY)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
