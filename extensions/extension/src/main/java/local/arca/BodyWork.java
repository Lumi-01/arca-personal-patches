package local.arca;

/** Cooperative cancellation, scoped to the existing article formatter worker only. */
public final class BodyWork {
    private static volatile Thread worker;
    private static final Stopped STOPPED = new Stopped();
    private BodyWork() { }

    public static void workerCreated(Thread thread) { worker = thread; }

    public static void checkpoint() {
        Thread current = Thread.currentThread();
        if (current == worker && current.isInterrupted()) throw STOPPED;
    }

    // The formatter catches Exception as a raw-HTML fallback. This private signal
    // deliberately escapes that fallback and is caught by the already-cancelled
    // FutureTask; it never escapes to the Activity or unrelated threads.
    private static final class Stopped extends Error {
        Stopped() { super("Cancelled article formatting"); }
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
}
