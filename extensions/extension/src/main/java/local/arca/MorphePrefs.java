package local.arca;

import android.content.Context;
import android.content.SharedPreferences;

/** Cache runtime switches so requests and view updates do not read preferences from disk. */
public final class MorphePrefs {
    private static final String FILE = "morphe_personal_settings";
    private static final String ADS = "block_app_ads";
    private static final String BODY = "reuse_body_view";
    private static volatile SharedPreferences preferences;
    private static volatile boolean blockAds = true;
    private static volatile boolean reuseBodyView = true;

    private MorphePrefs() { }

    public static synchronized void init(Context context) {
        if (preferences != null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        blockAds = prefs.getBoolean(ADS, true);
        reuseBodyView = prefs.getBoolean(BODY, true);
        preferences = prefs;
    }

    public static boolean blockAds() { return blockAds; }
    public static boolean reuseBodyView() { return reuseBodyView; }

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
