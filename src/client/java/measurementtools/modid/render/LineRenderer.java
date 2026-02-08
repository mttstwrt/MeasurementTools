package measurementtools.modid.render;

import measurementtools.modid.SelectionManager;
import measurementtools.modid.util.SplineMath;
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

/**
 * Renders straight lines between selected points.
 * Unlike SplineRenderer which uses Catmull-Rom curves, this draws direct lines.
 * Supports tube radius for creating cylindrical paths around the lines.
 */
public class LineRenderer implements ShapeRenderer {
    private static final int BUFFER_SIZE = 8192;
    private static final int TUBE_CIRCLE_SEGMENTS = 16;

    private BufferAllocator buffer;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2) return;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        int tubeRadius = SelectionManager.getInstance().getSplineRadius();

        // Draw straight lines between consecutive points
        double totalLength = 0;
        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos from = selection.get(i);
            BlockPos to = selection.get(i + 1);

            Vec3d fromCenter = new Vec3d(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
            Vec3d toCenter = new Vec3d(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);

            drawLine(matrix, lines, fromCenter, toCenter, cameraPos, r, g, b, a);
            totalLength += fromCenter.distanceTo(toCenter);

            // Draw tube if radius > 0
            if (tubeRadius > 0) {
                drawTubeForSegment(matrix, lines, fromCenter, toCenter, tubeRadius, cameraPos, r, g, b, a);
            }

            // Draw segment length label at midpoint
            if (config.showLabels() && selection.size() > 2) {
                Vec3d midPoint = fromCenter.add(toCenter).multiply(0.5);
                double segmentLength = fromCenter.distanceTo(toCenter);
                RenderUtils.drawWorldLabel(camera, viewMatrix,
                    midPoint.x, midPoint.y + 0.3, midPoint.z,
                    String.format("%.1f", segmentLength));
            }
        }

        // Draw markers at each point
        for (BlockPos pos : selection) {
            Vec3d point = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            drawPointMarker(matrix, lines, point, cameraPos, r, g, b, a);
        }

        immediate.draw();

        // Draw total length label
        if (config.showLabels() && selection.size() >= 2) {
            drawTotalLengthLabel(camera, viewMatrix, selection, totalLength);
        }

        // Draw angle labels at vertices (between segments)
        if (config.showLabels() && selection.size() >= 3) {
            drawAngleLabels(camera, viewMatrix, selection);
        }
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2 || !config.showLabels()) return;

        double totalLength = 0;
        for (int i = 0; i < selection.size() - 1; i++) {
            BlockPos from = selection.get(i);
            BlockPos to = selection.get(i + 1);
            Vec3d fromCenter = new Vec3d(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
            Vec3d toCenter = new Vec3d(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
            totalLength += fromCenter.distanceTo(toCenter);
        }

        drawTotalLengthLabel(camera, viewMatrix, selection, totalLength);
    }

    private void drawTotalLengthLabel(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, double totalLength) {
        // Calculate midpoint of the entire path
        int midIndex = selection.size() / 2;
        BlockPos midPos = selection.get(midIndex);
        double labelX = midPos.getX() + 0.5;
        double labelY = midPos.getY() + 1.0;
        double labelZ = midPos.getZ() + 0.5;

        RenderUtils.drawWorldLabel(camera, viewMatrix, labelX, labelY, labelZ,
            String.format("len=%.1f", totalLength));
    }

    private void drawLine(Matrix4f matrix, VertexConsumer lines, Vec3d from, Vec3d to,
                          Vec3d cameraPos, float r, float g, float b, float a) {
        float x1 = (float) (from.x - cameraPos.x);
        float y1 = (float) (from.y - cameraPos.y);
        float z1 = (float) (from.z - cameraPos.z);
        float x2 = (float) (to.x - cameraPos.x);
        float y2 = (float) (to.y - cameraPos.y);
        float z2 = (float) (to.z - cameraPos.z);

        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private void drawPointMarker(Matrix4f matrix, VertexConsumer lines, Vec3d point,
                                  Vec3d cameraPos, float r, float g, float b, float a) {
        float size = 0.15f;
        float x = (float) (point.x - cameraPos.x);
        float y = (float) (point.y - cameraPos.y);
        float z = (float) (point.z - cameraPos.z);

        // Draw a small 3D cross
        RenderUtils.drawLine(matrix, lines, x - size, y, z, x + size, y, z, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x, y - size, z, x, y + size, z, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x, y, z - size, x, y, z + size, r, g, b, a);
    }

    /**
     * Draws tube circles along a straight line segment.
     */
    private void drawTubeForSegment(Matrix4f matrix, VertexConsumer lines, Vec3d from, Vec3d to,
                                     int radius, Vec3d cameraPos, float r, float g, float b, float a) {
        Vec3d direction = to.subtract(from).normalize();
        double length = from.distanceTo(to);

        // Draw circles at regular intervals along the line
        int circleCount = Math.max(2, (int) Math.ceil(length / 2.0));
        for (int i = 0; i <= circleCount; i++) {
            double t = (double) i / circleCount;
            Vec3d center = from.add(to.subtract(from).multiply(t));
            drawTubeCircle(matrix, lines, center, direction, radius, cameraPos, r, g, b, a);
        }

        // Draw longitudinal lines connecting the circles
        drawTubeLongitudinalLines(matrix, lines, from, to, direction, radius, cameraPos, r, g, b, a);
    }

    /**
     * Draws a circle perpendicular to the given direction at the specified center point.
     */
    private void drawTubeCircle(Matrix4f matrix, VertexConsumer lines, Vec3d center, Vec3d direction,
                                 int radius, Vec3d cameraPos, float r, float g, float b, float a) {
        Vec3d perp1 = SplineMath.findPerpendicular(direction);
        Vec3d perp2 = direction.crossProduct(perp1).normalize();

        Vec3d[] circlePoints = new Vec3d[TUBE_CIRCLE_SEGMENTS];
        for (int i = 0; i < TUBE_CIRCLE_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / TUBE_CIRCLE_SEGMENTS;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            circlePoints[i] = center.add(perp1.multiply(cos)).add(perp2.multiply(sin));
        }

        // Draw the circle
        for (int i = 0; i < TUBE_CIRCLE_SEGMENTS; i++) {
            Vec3d fromPoint = circlePoints[i];
            Vec3d toPoint = circlePoints[(i + 1) % TUBE_CIRCLE_SEGMENTS];
            drawLine(matrix, lines, fromPoint, toPoint, cameraPos, r, g, b, a);
        }
    }

    /**
     * Draws longitudinal lines along a straight tube section.
     */
    private void drawTubeLongitudinalLines(Matrix4f matrix, VertexConsumer lines,
                                            Vec3d from, Vec3d to, Vec3d direction, int radius,
                                            Vec3d cameraPos, float r, float g, float b, float a) {
        Vec3d perp1 = SplineMath.findPerpendicular(direction);
        Vec3d perp2 = direction.crossProduct(perp1).normalize();

        int longitudinalCount = 8; // Number of lines along the tube
        for (int i = 0; i < longitudinalCount; i++) {
            double angle = 2 * Math.PI * i / longitudinalCount;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;

            Vec3d offset = perp1.multiply(cos).add(perp2.multiply(sin));
            Vec3d lineStart = from.add(offset);
            Vec3d lineEnd = to.add(offset);

            drawLine(matrix, lines, lineStart, lineEnd, cameraPos, r, g, b, a);
        }
    }

    /**
     * Draws angle labels at each vertex between line segments.
     * Shows both horizontal (turn) and vertical (pitch) angles.
     */
    private void drawAngleLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection) {
        for (int i = 1; i < selection.size() - 1; i++) {
            BlockPos prev = selection.get(i - 1);
            BlockPos curr = selection.get(i);
            BlockPos next = selection.get(i + 1);

            Vec3d prevCenter = new Vec3d(prev.getX() + 0.5, prev.getY() + 0.5, prev.getZ() + 0.5);
            Vec3d currCenter = new Vec3d(curr.getX() + 0.5, curr.getY() + 0.5, curr.getZ() + 0.5);
            Vec3d nextCenter = new Vec3d(next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5);

            // Direction vectors
            Vec3d inDir = currCenter.subtract(prevCenter).normalize();
            Vec3d outDir = nextCenter.subtract(currCenter).normalize();

            // Calculate 3D angle between segments
            double dot = inDir.dotProduct(outDir);
            dot = Math.max(-1.0, Math.min(1.0, dot)); // Clamp for numerical stability
            double angle3D = Math.toDegrees(Math.acos(dot));

            // Calculate horizontal angle (turn in XZ plane)
            double inYaw = Math.atan2(inDir.x, inDir.z);
            double outYaw = Math.atan2(outDir.x, outDir.z);
            double horizontalAngle = Math.toDegrees(outYaw - inYaw);
            // Normalize to -180 to 180
            while (horizontalAngle > 180) horizontalAngle -= 360;
            while (horizontalAngle < -180) horizontalAngle += 360;

            // Calculate vertical angles (pitch)
            double inPitch = Math.toDegrees(Math.asin(inDir.y));
            double outPitch = Math.toDegrees(Math.asin(outDir.y));
            double verticalAngle = outPitch - inPitch;

            // Calculate offset direction for label placement (perpendicular to the angle bisector)
            Vec3d bisector = inDir.add(outDir).normalize();
            if (bisector.lengthSquared() < 0.01) {
                // Segments are nearly opposite, use perpendicular
                bisector = SplineMath.findPerpendicular(inDir);
            }

            // Offset perpendicular to bisector in XZ plane for horizontal label
            Vec3d horizOffset = new Vec3d(-bisector.z, 0, bisector.x).normalize();
            if (horizOffset.lengthSquared() < 0.01) {
                horizOffset = new Vec3d(1, 0, 0);
            }

            // Position labels with clear separation
            // Horizontal angle: offset to the side and slightly above
            double hLabelX = currCenter.x + horizOffset.x * 1.2;
            double hLabelY = currCenter.y + 0.8;
            double hLabelZ = currCenter.z + horizOffset.z * 1.2;

            // Vertical angle: offset to the opposite side and slightly below
            double vLabelX = currCenter.x - horizOffset.x * 1.2;
            double vLabelY = currCenter.y - 0.3;
            double vLabelZ = currCenter.z - horizOffset.z * 1.2;

            // Format angle strings with direction indicators
            String hAngleStr = formatHorizontalAngle(horizontalAngle);
            String vAngleStr = formatVerticalAngle(verticalAngle);

            // Draw labels
            RenderUtils.drawWorldLabel(camera, viewMatrix, hLabelX, hLabelY, hLabelZ, hAngleStr);
            RenderUtils.drawWorldLabel(camera, viewMatrix, vLabelX, vLabelY, vLabelZ, vAngleStr);
        }
    }

    /**
     * Formats horizontal angle with turn direction indicator.
     */
    private String formatHorizontalAngle(double angle) {
        String direction;
        if (Math.abs(angle) < 1) {
            direction = "straight";
        } else if (angle > 0) {
            direction = "L"; // Left turn
        } else {
            direction = "R"; // Right turn
        }
        return String.format("H:%.0f° %s", Math.abs(angle), direction);
    }

    /**
     * Formats vertical angle with pitch direction indicator.
     */
    private String formatVerticalAngle(double angle) {
        String direction;
        if (Math.abs(angle) < 1) {
            direction = "level";
        } else if (angle > 0) {
            direction = "up";
        } else {
            direction = "dn";
        }
        return String.format("V:%.0f° %s", Math.abs(angle), direction);
    }
}
