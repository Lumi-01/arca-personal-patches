package local.arca;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** One paused, empty article view, owned only by the foreground Activity. */
public final class BodyWebViewCache implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    private static final BodyWebViewCache INSTANCE = new BodyWebViewCache();
    private static boolean initialized;
    private static Activity foreground;
    private static WebView cached;

    public static void init(Context context) {
        Context app = context.getApplicationContext();
        if (initialized || !(app instanceof Application)) return;
        initialized = true;
        ((Application) app).registerActivityLifecycleCallbacks(INSTANCE);
        app.registerComponentCallbacks(INSTANCE);
    }

    public static WebView take(Context context) {
        WebView view = cached;
        if (view == null) return null;
        cached = null;
        if (MorphePrefs.reuseBodyView() && view.getContext() == context &&
                owner(context) == foreground && view.getParent() == null) {
            return view;
        }
        view.destroy();
        return null;
    }

    /** The patched article view pauses through the original lifecycle helper first. */
    public static void release(WebView view) {
        Activity activity = owner(view.getContext());
        if (!MorphePrefs.reuseBodyView() || activity == null || activity != foreground ||
                activity.isFinishing() || activity.isDestroyed()) {
            view.destroy();
            return;
        }
        if (cached == view) return;
        clear();
        // Drop all old callbacks/content before storing the view. Do not clear
        // cookies or the shared HTTP cache, and do not retain an Article model.
        view.stopLoading();
        view.setWebViewClient(Build.VERSION.SDK_INT >= 26 ? new EmptyClient26() : new WebViewClient());
        view.setWebChromeClient(null);
        view.setDownloadListener(null);
        view.removeJavascriptInterface("webViewTunnel");
        view.clearFocus();
        view.scrollTo(0, 0);
        view.loadUrl("about:blank");
        cached = view;
    }

    public static void clear() {
        WebView view = cached;
        cached = null;
        if (view != null) view.destroy();
    }

    /** A terminated idle renderer must not crash the app through the default client. */
    private static final class EmptyClient26 extends WebViewClient {
        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (cached == view) cached = null;
            view.destroy();
            return true;
        }
    }

    private static Activity owner(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return null;
    }

    @Override public void onActivityResumed(Activity activity) { foreground = activity; }
    @Override public void onActivityPaused(Activity activity) {
        if (activity == foreground) {
            foreground = null;
            clear();
        }
    }
    @Override public void onActivityDestroyed(Activity activity) {
        if (cached != null && owner(cached.getContext()) == activity) clear();
        if (foreground == activity) foreground = null;
    }
    @Override public void onConfigurationChanged(Configuration config) { clear(); }
    @Override public void onLowMemory() { clear(); }
    @Override public void onTrimMemory(int level) {
        // Also release for foreground running-low/moderate memory pressure.
        if (level >= TRIM_MEMORY_RUNNING_MODERATE) clear();
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    private BodyWebViewCache() {}
}
