package local.arca;

/** Shares the Plus download OkHttp client so same-host transfers can reuse TLS connections. */
public final class DownloadClientPool {
    private static final String DOWNLOAD_SERVICE = "N8.a";
    private static Object downloadClient;

    private DownloadClientPool() { }

    public static synchronized Object reuse(Class<?> service, Object candidate) {
        if (service == null || !DOWNLOAD_SERVICE.equals(service.getName())) {
            return candidate;
        }
        if (downloadClient == null) {
            downloadClient = candidate;
        }
        return downloadClient;
    }
}

