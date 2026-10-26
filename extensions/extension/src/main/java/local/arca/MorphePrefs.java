package local.arca;

import android.content.Context;
import android.content.SharedPreferences;

/** Cache runtime switches so requests and view updates do not read preferences from disk. */
public final class MorphePrefs {
    private static final String FILE = "morphe_personal_settings";
    private static final String ADS = "block_app_ads";
    private static final String BODY = "reuse_body_view";
    private static final String TEXT = "text_layout_compat";
    private static volatile SharedPreferences preferences;
    private static volatile boolean blockAds = true;
    private static volatile boolean reuseBodyView = true;
    private static volatile boolean textLayoutCompat = true;

    private MorphePrefs() { }

    public static synchronized void init(Context context) {
        if (preferences != null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        blockAds = prefs.getBoolean(ADS, true);
        reuseBodyView = prefs.getBoolean(BODY, true);
        textLayoutCompat = prefs.getBoolean(TEXT, true);
        preferences = prefs;
    }

    public static boolean blockAds() { return blockAds; }
    public static boolean reuseBodyView() { return reuseBodyView; }
    public static boolean textLayoutCompat() { return textLayoutCompat; }

    public static boolean configuredTextLayoutCompat() {
        SharedPreferences prefs = preferences;
        return prefs == null ? textLayoutCompat : prefs.getBoolean(TEXT, true);
    }

    public static void setTextLayoutCompat(boolean enabled) {
        // Text caches outlive individual rows. Change their policy only on a fresh process.
        SharedPreferences prefs = preferences;
        if (prefs != null) prefs.edit().putBoolean(TEXT, enabled).apply();
    }

    public static void setReuseBodyView(boolean enabled) {
        reuseBodyView = enabled;
        if (!enabled) BodyWebViewCache.clear();
        SharedPreferences prefs = preferences;
        if (prefs != null) prefs.edit().putBoolean(BODY, enabled).apply();
    }

    public static void setBlockAds(boolean enabled) {
        blockAds = enabled;
        SharedPreferences prefs = preferences;
        if (prefs != null) prefs.edit().putBoolean(ADS, enabled).apply();
    }

}
