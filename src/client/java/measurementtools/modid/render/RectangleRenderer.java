package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import measurementtools.modid.SelectionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class RectangleRenderer implements ShapeRenderer {

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        SelectionManager manager = SelectionManager.getInstance();
        BlockPos minPos = manager.getMinPos();
        BlockPos maxPos = manager.getMaxPos();
        if (minPos == null || maxPos == null) return;

        Vec3d cameraPos = camera.getPos();

        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        // Calculate box corners relative to camera
        float x1 = (float) (minPos.getX() - cameraPos.x);
        float y1 = (float) (minPos.getY() - cameraPos.y);
        float z1 = (float) (minPos.getZ() - cameraPos.z);
        float x2 = (float) (maxPos.getX() + 1 - cameraPos.x);
        float y2 = (float) (maxPos.getY() + 1 - cameraPos.y);
        float z2 = (float) (maxPos.getZ() + 1 - cameraPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        // Bottom face
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x2, y1, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z2, x2, y1, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x1, y1, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z1, x2, y1, z2, r, g, b, a);

        // Top face
        RenderUtils.drawLine(matrix, lines, x1, y2, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y2, z2, x2, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y2, z1, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y2, z1, x2, y2, z2, r, g, b, a);

        // Vertical edges
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x1, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z2, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z2, x2, y2, z2, r, g, b, a);

        immediate.draw();

        // Draw dimension labels
        drawLabels(camera, viewMatrix, minPos, maxPos);
    }

    private void drawLabels(Camera camera, Matrix4f viewMatrix, BlockPos minPos, BlockPos maxPos) {
        int sizeX = maxPos.getX() - minPos.getX() + 1;
        int sizeY = maxPos.getY() - minPos.getY() + 1;
        int sizeZ = maxPos.getZ() - minPos.getZ() + 1;

        // Calculate label positions (at edge midpoints)
        double midX = (minPos.getX() + maxPos.getX() + 1) / 2.0;
        double midY = (minPos.getY() + maxPos.getY() + 1) / 2.0;
        double midZ = (minPos.getZ() + maxPos.getZ() + 1) / 2.0;

        // X dimension label
        RenderUtils.drawWorldLabel(camera, viewMatrix, midX, maxPos.getY() + 1.3, maxPos.getZ() + 1.3, String.valueOf(sizeX));

        // Y dimension label
        RenderUtils.drawWorldLabel(camera, viewMatrix, maxPos.getX() + 1.3, midY, maxPos.getZ() + 1.3, String.valueOf(sizeY));

        // Z dimension label
        RenderUtils.drawWorldLabel(camera, viewMatrix, maxPos.getX() + 1.3, maxPos.getY() + 1.3, midZ, String.valueOf(sizeZ));
    }
}
