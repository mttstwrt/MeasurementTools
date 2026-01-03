package measurementtools.modid.gui;

import net.minecraft.text.Text;

public interface RadialMenuAction {
    Text getName();

    void execute();

    boolean isEnabled();

    default int getColor() {
        return 0xFFFFFF;
    }
}
