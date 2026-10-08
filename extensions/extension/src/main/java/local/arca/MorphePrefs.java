package local.arca;

import android.content.Context;
import android.content.SharedPreferences;

/** Cached runtime switches: navigation reads never touch disk on an animation frame. */
public final class MorphePrefs {
    private static final String FILE = "morphe_personal_settings";
    private static final String ADS = "block_app_ads";
    private static final String SMOOTH = "smooth_navigation";
    private static volatile SharedPreferences preferences;
    private static volatile boolean blockAds = true;
    private static volatile boolean smoothNavigation = true;

    private MorphePrefs() { }

    public static synchronized void init(Context context) {
        if (preferences != null) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        blockAds = prefs.getBoolean(ADS, true);
        smoothNavigation = prefs.getBoolean(SMOOTH, true);
        preferences = prefs;
    }

    public static boolean blockAds() { return blockAds; }
    public static boolean smoothNavigation() { return smoothNavigation; }

    public static void setBlockAds(boolean enabled) {
        blockAds = enabled;
        SharedPreferences prefs = preferences;
        if (prefs != null) prefs.edit().putBoolean(ADS, enabled).apply();
    }

    public static void setSmoothNavigation(boolean enabled) {
        smoothNavigation = enabled;
        SharedPreferences prefs = preferences;
        if (prefs != null) prefs.edit().putBoolean(SMOOTH, enabled).apply();
    }

    public static int navigationDuration() { return smoothNavigation ? 210 : 250; }
    public static int enterDistance(int fullWidth) {
        return smoothNavigation ? Math.round(fullWidth * 0.55f) : fullWidth;
    }
    public static int exitDistance(int originalDistance) {
        return smoothNavigation ? Math.round(originalDistance * 0.5f) : originalDistance;
    }
}
