package measurementtools.modid.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class RenderUtils {

    public static void drawLine(Matrix4f matrix, VertexConsumer lines,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float red, float green, float blue, float alpha) {
        // Calculate normal for the line
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001f) {
            dx = 1;
            dy = 0;
            dz = 0;
        } else {
            dx /= length;
            dy /= length;
            dz /= length;
        }

        lines.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(dx, dy, dz);
        lines.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(dx, dy, dz);
    }

    /**
     * Draw a label in world space that faces the camera (billboard)
     */
    public static void drawWorldLabel(Camera camera, Matrix4f viewMatrix, double worldX, double worldY, double worldZ, String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        Vec3d cameraPos = camera.getPos();

        // Calculate distance for scaling and culling
        double dx = worldX - cameraPos.x;
        double dy = worldY - cameraPos.y;
        double dz = worldZ - cameraPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Don't render if too far or too close
        if (distance > 128 || distance < 0.5) return;

        MatrixStack matrices = new MatrixStack();

        // Apply the view matrix passed from the render pipeline
        matrices.multiplyPositionMatrix(viewMatrix);

        // Translate to position relative to camera
        matrices.translate(dx, dy, dz);

        // Billboard - face the camera
        matrices.multiply(camera.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));

        // Scale based on distance (smaller when close, larger when far)
        float scale = (float) Math.max(0.02f, 0.015f + 0.002f * distance);
        scale = Math.min(scale, 0.08f);
        matrices.scale(-scale, -scale, scale);

        // Draw text with background
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(256));

        int textWidth = textRenderer.getWidth(text);
        textRenderer.draw(
            text,
            -textWidth / 2.0f,
            0,
            0xFFFFFFFF,
            false,
            matrices.peek().getPositionMatrix(),
            immediate,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x80000000,
            15728880
        );

        immediate.draw();
    }

    /**
     * Draw a label relative to an existing transformed matrix stack
     */
    public static void drawLabel(Camera camera, Matrix4f viewMatrix, MatrixStack existingMatrices,
                                 double localX, double localY, double localZ, String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        // Use the existing matrix transformation and add the local offset
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(existingMatrices.peek().getPositionMatrix());
        matrices.translate(localX, localY, localZ);

        // Billboard - face the camera
        matrices.multiply(camera.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));

        // Calculate approximate distance for scaling
        Vec3d cameraPos = camera.getPos();
        double distance = 10.0; // Default distance for relative labels
        float scale = (float) Math.max(0.02f, 0.015f + 0.002f * distance);
        scale = Math.min(scale, 0.08f);
        matrices.scale(-scale, -scale, scale);

        // Draw text with background
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(256));

        int textWidth = textRenderer.getWidth(text);
        textRenderer.draw(
            text,
            -textWidth / 2.0f,
            0,
            0xFFFFFFFF,
            false,
            matrices.peek().getPositionMatrix(),
            immediate,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x80000000,
            15728880
        );

        immediate.draw();
    }
}
