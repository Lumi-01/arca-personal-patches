package local.arca;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;

/** Tracks active transfers explicitly cancelled by the user. */
public final class DownloadCancelGate {
    private static final Set<String> CANCELLED =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private DownloadCancelGate() { }

    public static void mark(String uuid) {
        if (uuid != null) CANCELLED.add(uuid);
    }

    public static boolean consume(String uuid) {
        return uuid != null && CANCELLED.remove(uuid);
    }

    /** Suppress only the coroutine cancellation raised by an explicit user cancel. */
    public static Throwable suppressUserCancelled(String uuid, Throwable failure) {
        if (failure instanceof CancellationException && consume(uuid)) return null;
        return failure;
    }

    public static void clear(String uuid) {
        if (uuid != null) CANCELLED.remove(uuid);
    }
}
