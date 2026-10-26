package local.arca;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamCopy {
    private StreamCopy() { }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        // Okio's network stream can return just one 8 KiB segment even when
        // asked for 64 KiB. Coalesce those short reads before MediaStore writes.
        // The bounded buffer is flushed/closed on completion and on failure.
        try (InputStream source = input;
             OutputStream target = new BufferedOutputStream(output, 64 * 1024)) {
            int count;
            while ((count = source.read(buffer)) != -1) {
                target.write(buffer, 0, count);
            }
        }
    }
}
