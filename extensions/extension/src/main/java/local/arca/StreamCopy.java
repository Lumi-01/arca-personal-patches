package local.arca;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamCopy {
    private StreamCopy() { }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        } finally {
            input.close();
        }
    }
}
