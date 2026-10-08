package local.arca;

import android.content.Context;
import android.content.res.Resources;

/** Selects saved stock activity animations when the in-app motion switch is off. */
public final class MorpheActivityMotion {
    private MorpheActivityMotion() { }

    public static int resolve(int id) {
        if (id == 0 || MorphePrefs.smoothNavigation()) return id;
        Context context = MorphePrefs.context();
        if (context == null) return id;
        Resources resources = context.getResources();
        try {
            String name = resources.getResourceEntryName(id);
            if (!name.equals("pull_in_left") && !name.equals("pull_in_right") &&
                    !name.equals("push_out_left") && !name.equals("push_out_right")) {
                return id;
            }
            int stock = resources.getIdentifier(
                    "morphe_original_" + name, "anim", context.getPackageName());
            return stock != 0 ? stock : id;
        } catch (Resources.NotFoundException ignored) {
            return id;
        }
    }
}
