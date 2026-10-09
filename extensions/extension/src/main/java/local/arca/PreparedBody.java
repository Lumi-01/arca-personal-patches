package local.arca;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import N7.u;
import V.Z0;
import V.w0;
import V.w1;
import Ya.n;

/** Finite HTML work owned by the original Compose remember group. No prefetch. */
public final class PreparedBody implements Z0 {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
            0, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    runnable.run();
                }, "ArcaBodyHtml");
                thread.setDaemon(true);
                return thread;
            });
    private static final u EMPTY = new u("", Collections.emptyList());
    private final w0 result = w1.h(EMPTY, w1.p());
    private final boolean dark;
    private String html;
    private List votes;
    private FutureTask<u> task;
    private boolean active;

    private PreparedBody(String html, List votes, boolean dark) {
        this.html = html;
        this.votes = votes == null ? null : new ArrayList(votes);
        this.dark = dark;
    }

    public static Object prepare(String html, List votes, boolean dark) {
        if (!MorphePrefs.reuseBodyView()) return n.a.j(html, votes, dark);
        // No document to transform while the API response is still absent.
        // Preserve vote handling if an unusual empty article includes vote data.
        if ((html == null || html.isEmpty()) && (votes == null || votes.isEmpty())) return EMPTY;
        return new PreparedBody(html, votes, dark);
    }

    /** Read while the original remember group is open so Compose observes completion. */
    public static u value(Object prepared) {
        return prepared instanceof PreparedBody
                ? (u) ((PreparedBody) prepared).result.getValue() : (u) prepared;
    }

    @Override public void d() { // onRemembered: don't start speculative/abandoned work.
        if (active || task != null) return;
        active = true;
        final String source = html;
        final List options = votes;
        task = new FutureTask<u>(() -> n.a.j(source, options, dark)) {
            @Override protected void done() {
                if (isCancelled()) return;
                final u output;
                try {
                    output = get();
                } catch (Exception failure) {
                    // Same raw-HTML fallback used by the original formatter.
                    MAIN.post(() -> finish(new u(source == null ? "" : source, Collections.emptyList())));
                    return;
                }
                MAIN.post(() -> finish(output));
            }
        };
        html = null;
        votes = null;
        WORKER.execute(task);
    }

    private void finish(u output) {
        if (active && task != null && !task.isCancelled()) {
            result.setValue(output);
            task = null; // Drop completed task captures as soon as the UI receives its result.
        }
    }

    private void cancel() {
        active = false;
        if (task != null) {
            task.cancel(true);
            WORKER.remove(task);
        }
        html = null;
        votes = null;
    }

    @Override public void b() { cancel(); }
    @Override public void c() { cancel(); }
}
