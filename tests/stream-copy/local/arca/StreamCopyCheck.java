package local.arca;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public final class StreamCopyCheck {
    static final class Source extends ByteArrayInputStream {
        boolean closed;
        boolean fail;
        Source(byte[] data) { super(data); }
        @Override public synchronized int read(byte[] data, int offset, int count) {
            if (fail && pos >= 8192) throw new IllegalStateException("read failed");
            return super.read(data, offset, Math.min(count, 8192));
        }
        @Override public void close() { closed = true; }
    }
    static final class Target extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean closed;
        boolean fail;
        int writes;
        @Override public void write(int value) { throw new AssertionError("byte-wise write"); }
        @Override public void write(byte[] data, int offset, int count) throws IOException {
            writes++;
            if (fail) throw new IOException("write failed");
            bytes.write(data, offset, count);
        }
        @Override public void close() { closed = true; }
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{0, 1, 8191, 8192, 65535, 65536, 65537, 4 * 1024 * 1024 + 17}) {
            byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++) bytes[i] = (byte)(i * 31);
            Source input = new Source(bytes);
            Target output = new Target();
            StreamCopy.copy(input, output);
            check(Arrays.equals(bytes, output.bytes.toByteArray()), "data/flush: " + size);
            check(input.closed && output.closed, "close: " + size);
            check(output.writes == (size + 65535) / 65536, "coalescing: " + size);
            if (size > 1024 * 1024) System.out.println("8 KiB reads: 513 -> " + output.writes + " file writes; bytes identical");
        }
        Source brokenInput = new Source(new byte[131072]);
        Target writeFailure = new Target();
        writeFailure.fail = true;
        try { StreamCopy.copy(brokenInput, writeFailure); throw new AssertionError("write failure hidden"); }
        catch (IOException expected) { check("write failed".equals(expected.getMessage()), "wrong error"); }
        check(brokenInput.closed && writeFailure.closed, "write failure leaked stream");
        Source readFailure = new Source(new byte[131072]);
        readFailure.fail = true;
        Target target = new Target();
        try { StreamCopy.copy(readFailure, target); throw new AssertionError("read failure hidden"); }
        catch (IllegalStateException expected) { check("read failed".equals(expected.getMessage()), "wrong read error"); }
        check(readFailure.closed && target.closed, "read failure leaked stream");
        System.out.println("PASS: bounded writes, EOF flush, exact bytes, error propagation and stream closure");
    }
}
