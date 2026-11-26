package local.arca;

/** Keeps unknown-length downloads inside the active progress range. */
public final class DownloadProgress {
    private DownloadProgress() { }

    public static int activePercent(int percent) {
        // The app divides downloaded bytes by max(contentLength, 1). For a
        // chunked response this can overflow an int or report >100 long before
        // EOF. Keep the UI at 99% until the queue marks the item complete.
        if (percent < 0 || percent >= 100) {
            return 99;
        }
        return percent;
    }
}
