package local.arca;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Distinguishes a user-cancelled transfer from a genuine download failure. */
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

    public static void clear(String uuid) {
        if (uuid != null) CANCELLED.remove(uuid);
    }
}
