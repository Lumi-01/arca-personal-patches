package local.arca;

import V.H1;
import b1.p;
import c8.l;
import androidx.compose.ui.graphics.c;

/** Reads slide state in the existing layer, rather than during measurement. */
public final class SlideLayer implements l {
    private final H1 offset;
    private final l original;

    private SlideLayer(H1 offset, l original) {
        this.offset = offset;
        this.original = original;
    }

    public static l wrap(H1 offset, l original) {
        return new SlideLayer(offset, original);
    }

    @Override public Object d(Object value) {
        Object result = original.d(value);
        c layer = (c) value;
        long packed = ((p) offset.getValue()).o();
        layer.l(layer.D() + (int) (packed >> 32));
        layer.h(layer.A() + (int) packed);
        return result;
    }
}
