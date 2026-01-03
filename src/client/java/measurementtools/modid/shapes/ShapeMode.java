package measurementtools.modid.shapes;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public enum ShapeMode {
    RECTANGLE("rectangle", "measurementtools.shape.rectangle"),
    CYLINDER("cylinder", "measurementtools.shape.cylinder"),
    ELLIPSOID("ellipsoid", "measurementtools.shape.ellipsoid");

    private final String id;
    private final String translationKey;

    ShapeMode(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public Text getDisplayName() {
        return Text.translatable(translationKey);
    }

    public Identifier getIconTexture() {
        return Identifier.of("measurementtools", "textures/gui/icons/" + id + ".png");
    }
}
