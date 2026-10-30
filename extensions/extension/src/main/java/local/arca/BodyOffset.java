package local.arca;

import B.E;
import V.H1;
import V.n;
import V.s0;

/** Read all inputs in the consumer's Compose snapshot instead of combining delayed emissions. */
public final class BodyOffset implements H1 {
    private final E list;
    private final s0 height;

    private BodyOffset(E list, s0 height) {
        this.list = list;
        this.height = height;
    }

    public static H1 state(E list, s0 height, n composer) {
        composer.V(-1692130118);
        Object previous = composer.h();
        BodyOffset state;
        if (previous instanceof BodyOffset && ((BodyOffset) previous).list == list &&
                ((BodyOffset) previous).height == height) {
            state = (BodyOffset) previous;
        } else {
            state = new BodyOffset(list, height);
            composer.M(state);
        }
        composer.L();
        return state;
    }

    @Override public Object getValue() {
        // These are snapshot-backed reads, so Compose observes the consuming scope.
        // Preserve the stock sticky-body formula and item index; do not move comments.
        int first = list.q();
        return Integer.valueOf(first < 2 ? 0 : first == 2 ? -list.r() : -height.d());
    }
}
