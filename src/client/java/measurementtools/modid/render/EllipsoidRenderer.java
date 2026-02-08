package measurementtools.modid.render;

import measurementtools.modid.SelectionManager;
import measurementtools.modid.shapes.EllipsoidMode;
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

public class EllipsoidRenderer implements ShapeRenderer {
    private static final int LAT_SEGMENTS = 12;
    private static final int LONG_SEGMENTS = 24;
    private static final int BUFFER_SIZE = 8192;
    private BufferAllocator buffer;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty()) return;

        SelectionManager manager = SelectionManager.getInstance();

        double centerX, centerY, centerZ;
        double radiusX, radiusY, radiusZ;

        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            // Fit ellipsoid inside bounding box
            BlockPos minPos = manager.getMinPos();
            BlockPos maxPos = manager.getMaxPos();
            if (minPos == null || maxPos == null) return;

            centerX = (minPos.getX() + maxPos.getX() + 1) / 2.0;
            centerY = (minPos.getY() + maxPos.getY() + 1) / 2.0;
            centerZ = (minPos.getZ() + maxPos.getZ() + 1) / 2.0;

            radiusX = (maxPos.getX() - minPos.getX() + 1) / 2.0;
            radiusY = (maxPos.getY() - minPos.getY() + 1) / 2.0;
            radiusZ = (maxPos.getZ() - minPos.getZ() + 1) / 2.0;
        } else {
            // CENTER_RADIUS mode: first block is center, furthest defines XZ radius
            BlockPos center = manager.getCenterBlock();
            if (center == null) return;

            centerX = center.getX() + 0.5;
            centerZ = center.getZ() + 0.5;

            double radiusXZ = manager.getMaxRadiusXZ();
            if (radiusXZ < 0.5) radiusXZ = 0.5;
            radiusX = radiusXZ;
            radiusZ = radiusXZ;

            int minY = manager.getMinY();
            int maxY = manager.getMaxY();
            radiusY = (maxY - minY + 1) / 2.0;
            centerY = (minY + maxY + 1) / 2.0;
        }

        if (radiusX < 0.5) radiusX = 0.5;
        if (radiusY < 0.5) radiusY = 0.5;
        if (radiusZ < 0.5) radiusZ = 0.5;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        matrices.push();
        matrices.translate(
            centerX - cameraPos.x,
            centerY - cameraPos.y,
            centerZ - cameraPos.z
        );

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Draw latitude lines (horizontal ellipses at different Y levels)
        for (int lat = -LAT_SEGMENTS / 2; lat <= LAT_SEGMENTS / 2; lat++) {
            double phi = Math.PI * lat / LAT_SEGMENTS;
            double y = Math.sin(phi) * radiusY;
            double latRadiusX = Math.cos(phi) * radiusX;
            double latRadiusZ = Math.cos(phi) * radiusZ;

            drawEllipseXZ(matrix, lines, (float) y, latRadiusX, latRadiusZ, LONG_SEGMENTS, r, g, b, a);
        }

        // Draw longitude lines (vertical ellipses around the Y axis)
        for (int lon = 0; lon < 8; lon++) {
            double theta = Math.PI * lon / 8;
            drawLongitudeLine(matrix, lines, theta, radiusX, radiusZ, radiusY, LAT_SEGMENTS * 2, r, g, b, a);
        }

        // Draw labels
        if (config.showLabels()) {
            drawLabels(camera, viewMatrix, matrices, radiusX, radiusY, radiusZ);
        }

        matrices.pop();
        immediate.draw();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.isEmpty() || !config.showLabels()) return;

        SelectionManager manager = SelectionManager.getInstance();

        double centerX, centerY, centerZ;
        double radiusX, radiusY, radiusZ;

        if (manager.getEllipsoidMode() == EllipsoidMode.FIT_TO_BOX) {
            BlockPos minPos = manager.getMinPos();
            BlockPos maxPos = manager.getMaxPos();
            if (minPos == null || maxPos == null) return;

            centerX = (minPos.getX() + maxPos.getX() + 1) / 2.0;
            centerY = (minPos.getY() + maxPos.getY() + 1) / 2.0;
            centerZ = (minPos.getZ() + maxPos.getZ() + 1) / 2.0;

            radiusX = (maxPos.getX() - minPos.getX() + 1) / 2.0;
            radiusY = (maxPos.getY() - minPos.getY() + 1) / 2.0;
            radiusZ = (maxPos.getZ() - minPos.getZ() + 1) / 2.0;
        } else {
            BlockPos center = manager.getCenterBlock();
            if (center == null) return;

            centerX = center.getX() + 0.5;
            centerZ = center.getZ() + 0.5;

            double radiusXZ = manager.getMaxRadiusXZ();
            if (radiusXZ < 0.5) radiusXZ = 0.5;
            radiusX = radiusXZ;
            radiusZ = radiusXZ;

            int minY = manager.getMinY();
            int maxY = manager.getMaxY();
            radiusY = (maxY - minY + 1) / 2.0;
            centerY = (minY + maxY + 1) / 2.0;
        }

        if (radiusX < 0.5) radiusX = 0.5;
        if (radiusY < 0.5) radiusY = 0.5;
        if (radiusZ < 0.5) radiusZ = 0.5;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        matrices.push();
        matrices.translate(
            centerX - cameraPos.x,
            centerY - cameraPos.y,
            centerZ - cameraPos.z
        );

        drawLabels(camera, viewMatrix, matrices, radiusX, radiusY, radiusZ);

        matrices.pop();
    }

    private void drawLabels(Camera camera, Matrix4f viewMatrix, MatrixStack matrices,
                            double radiusX, double radiusY, double radiusZ) {
        RenderUtils.drawLabel(camera, viewMatrix, matrices, radiusX + 0.5, 0, 0,
            String.format("rx=%.1f", radiusX));
        RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, radiusY + 0.5, 0,
            String.format("ry=%.1f", radiusY));
        if (Math.abs(radiusX - radiusZ) > 0.1) {
            RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, 0, radiusZ + 0.5,
                String.format("rz=%.1f", radiusZ));
        }
    }

    private void drawEllipseXZ(Matrix4f matrix, VertexConsumer lines,
                               float y, double radiusX, double radiusZ, int segments,
                               float r, float g, float b, float a) {
        if (radiusX < 0.01 && radiusZ < 0.01) return;

        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;

            float x1 = (float) (Math.cos(angle1) * radiusX);
            float z1 = (float) (Math.sin(angle1) * radiusZ);
            float x2 = (float) (Math.cos(angle2) * radiusX);
            float z2 = (float) (Math.sin(angle2) * radiusZ);

            RenderUtils.drawLine(matrix, lines, x1, y, z1, x2, y, z2, r, g, b, a);
        }
    }

    private void drawLongitudeLine(Matrix4f matrix, VertexConsumer lines,
                                   double theta, double radiusX, double radiusZ, double radiusY, int segments,
                                   float r, float g, float b, float a) {
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        // Draw full vertical ellipse (0 to 2π) so lines go all the way around
        for (int i = 0; i < segments; i++) {
            double phi1 = 2 * Math.PI * i / segments;
            double phi2 = 2 * Math.PI * (i + 1) / segments;

            float x1 = (float) (Math.cos(phi1) * radiusX * cosTheta);
            float y1 = (float) (Math.sin(phi1) * radiusY);
            float z1 = (float) (Math.cos(phi1) * radiusZ * sinTheta);

            float x2 = (float) (Math.cos(phi2) * radiusX * cosTheta);
            float y2 = (float) (Math.sin(phi2) * radiusY);
            float z2 = (float) (Math.cos(phi2) * radiusZ * sinTheta);

            RenderUtils.drawLine(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }
    }
}
