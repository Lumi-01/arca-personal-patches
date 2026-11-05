package local.arca;

import B.A;
import B.B;
import F0.Y;
import F0.Z;
import V.H1;
import V.Z0;
import V.n;
import c8.q;

/** Keep the body composed as an ordinary scrolling item for the life of its article. */
public final class BodyPin implements Z0 {
    private static final H1 ZERO = new H1() {
        @Override public Object getValue() { return Integer.valueOf(0); }
    };
    private final Y container;
    private Y.a handle;

    private BodyPin(Y container) { this.container = container; }

    public static H1 offset() { return ZERO; }

    public static void item(B scope, Object key, Object type, q content, int flags, Object marker) {
        if (MorphePrefs.syncBodyOffset()) A.a(scope, key, type, content, flags, marker);
        else A.c(scope, key, type, content, flags, marker);
    }

    public static void retain(n composer) {
        if (!MorphePrefs.syncBodyOffset()) return;
        composer.V(-1692130118);
        Y container = (Y) composer.o(Z.a());
        Object previous = composer.h();
        if (!(previous instanceof BodyPin) || ((BodyPin) previous).container != container) {
            composer.M(new BodyPin(container));
        }
        composer.L();
    }

    @Override public void d() {
        if (container != null && handle == null) handle = container.b();
    }
    @Override public void b() { release(); }
    @Override public void c() { release(); }
    private void release() {
        Y.a current = handle;
        handle = null;
        if (current != null) current.a();
    }
}
