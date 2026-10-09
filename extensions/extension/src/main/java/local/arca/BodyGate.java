package local.arca;

import V.n;

/** Create the native article view after first resume; keep it during exit motion. */
public final class BodyGate {
    private boolean shown;

    public static boolean allow(n composer, boolean resumed) {
        composer.V(-1692130117);
        Object remembered = composer.h();
        BodyGate gate;
        if (remembered instanceof BodyGate) {
            gate = (BodyGate) remembered;
        } else {
            gate = new BodyGate();
            composer.M(gate);
        }
        composer.L();
        if (resumed || !MorphePrefs.reuseBodyView()) gate.shown = true;
        return gate.shown;
    }
}
