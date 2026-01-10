package measurementtools.modid.gui;

import net.minecraft.text.Text;

public interface RadialMenuAction {
    Text getName();

    void execute();

    boolean isEnabled();

    default int getColor() {
        return 0xFFFFFF;
    }

    /**
     * Called when the scroll wheel is used while hovering over this action.
     * @param amount positive for scroll up, negative for scroll down
     * @return true if this action handled the scroll, false otherwise
     */
    default boolean onScroll(double amount) {
        return false;
    }
}
