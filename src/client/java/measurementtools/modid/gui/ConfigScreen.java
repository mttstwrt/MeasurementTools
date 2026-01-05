package measurementtools.modid.gui;

import measurementtools.modid.ModConfig;
import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.EllipsoidMode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private SliderWidget opacitySlider;

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

        // Ghost Block Render Mode Toggle Button
        addDrawableChild(ButtonWidget.builder(
            getGhostBlockRenderModeText(),
            button -> {
                toggleGhostBlockRenderMode();
                button.setMessage(getGhostBlockRenderModeText());
            })
            .dimensions(centerX - 100, startY + 50, 200, 20)
            .build()
        );

        // Ghost Block Opacity Slider
        float currentOpacity = ModConfig.getInstance().getGhostBlockOpacity();
        opacitySlider = new SliderWidget(centerX - 100, startY + 80, 200, 20,
                getOpacityText(currentOpacity), currentOpacity) {
            @Override
            protected void updateMessage() {
                setMessage(getOpacityText((float) this.value));
            }

            @Override
            protected void applyValue() {
                ModConfig.getInstance().setGhostBlockOpacity((float) this.value);
            }
        };
        addDrawableChild(opacitySlider);

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

    private Text getGhostBlockRenderModeText() {
        ModConfig.GhostBlockRenderMode mode = ModConfig.getInstance().getGhostBlockRenderMode();
        String modeName = mode == ModConfig.GhostBlockRenderMode.WIREFRAME ? "Wireframe" : "Solid";
        return Text.literal("Ghost Block Style: " + modeName);
    }

    private void toggleGhostBlockRenderMode() {
        ModConfig config = ModConfig.getInstance();
        if (config.getGhostBlockRenderMode() == ModConfig.GhostBlockRenderMode.WIREFRAME) {
            config.setGhostBlockRenderMode(ModConfig.GhostBlockRenderMode.SOLID);
        } else {
            config.setGhostBlockRenderMode(ModConfig.GhostBlockRenderMode.WIREFRAME);
        }
    }

    private Text getOpacityText(float opacity) {
        return Text.literal("Ghost Block Opacity: " + String.format("%.0f%%", opacity * 100));
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

        // Draw ellipsoid mode description
        String desc1 = "Fit to Box: Ellipsoid fits inside the selection bounding box";
        String desc2 = "Center + Radius: First block is center, furthest block defines radius";

        int descY = this.height / 4 + 25;
        context.drawCenteredTextWithShadow(this.textRenderer, desc1, this.width / 2, descY, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, desc2, this.width / 2, descY + 10, 0xAAAAAA);

        // Draw ghost block settings description
        int ghostDescY = this.height / 4 + 105;
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Wireframe shows block outlines, Solid shows filled blocks",
            this.width / 2, ghostDescY, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
