package measurementtools.modid.gui;

import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.EllipsoidMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.literal("MeasurementTools Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Ellipsoid Mode Toggle Button
        addDrawableChild(ButtonWidget.builder(
            getEllipsoidModeText(),
            button -> {
                toggleEllipsoidMode();
                button.setMessage(getEllipsoidModeText());
            })
            .dimensions(centerX - 100, startY, 200, 20)
            .build()
        );

        // Done Button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Done"),
            button -> close())
            .dimensions(centerX - 100, this.height - 40, 200, 20)
            .build()
        );
    }

    private Text getEllipsoidModeText() {
        EllipsoidMode mode = SelectionManager.getInstance().getEllipsoidMode();
        String modeName = mode == EllipsoidMode.FIT_TO_BOX ? "Fit to Box" : "Center + Radius";
        return Text.literal("Ellipsoid Mode: " + modeName);
    }

    private void toggleEllipsoidMode() {
        SelectionManager manager = SelectionManager.getInstance();
        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            manager.setEllipsoidMode(EllipsoidMode.CENTER_RADIUS);
        } else {
            manager.setEllipsoidMode(EllipsoidMode.FIT_TO_BOX);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw title
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            20,
            0xFFFFFF
        );

        // Draw description
        String desc1 = "Fit to Box: Ellipsoid fits inside the selection bounding box";
        String desc2 = "Center + Radius: First block is center, furthest block defines radius";

        int descY = this.height / 4 + 30;
        context.drawCenteredTextWithShadow(this.textRenderer, desc1, this.width / 2, descY, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, desc2, this.width / 2, descY + 12, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
