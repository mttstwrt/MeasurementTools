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
 * Renders a smooth spline curve through selected blocks using Catmull-Rom interpolation.
 * The curve passes through each selected block in the order they were selected.
 * Supports tube radius for creating cylindrical paths around the spline.
 */
public class SplineRenderer implements ShapeRenderer {
    private static final int SEGMENTS_PER_SPAN = 16;
    private static final int TUBE_CIRCLE_SEGMENTS = 16;
    private static final int BUFFER_SIZE = 8192;

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

        Vec3d[] points = SplineMath.blockPosListToVec3d(selection);

        int tubeRadius = SelectionManager.getInstance().getSplineRadius();

        // Draw the spline
        if (points.length == 2) {
            // Just two points - draw a straight line
            drawLine(matrix, lines, points[0], points[1], cameraPos, r, g, b, a);
            if (tubeRadius > 0) {
                drawTubeForLine(matrix, lines, points[0], points[1], tubeRadius, cameraPos, r, g, b, a);
            }
        } else {
            // Three or more points - draw Catmull-Rom spline
            drawCatmullRomSpline(matrix, lines, points, cameraPos, r, g, b, a);
            if (tubeRadius > 0) {
                drawTubeForSpline(matrix, lines, points, tubeRadius, cameraPos, r, g, b, a);
            }
        }

        // Draw small markers at each control point
        for (Vec3d point : points) {
            drawPointMarker(matrix, lines, point, cameraPos, r, g, b, a);
        }

        // Draw total length label
        if (config.showLabels() && points.length >= 2) {
            drawLabels(camera, viewMatrix, matrices, points, cameraPos);
        }

        immediate.draw();
    }

    @Override
    public void renderLabels(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2 || !config.showLabels()) return;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        Vec3d[] points = SplineMath.blockPosListToVec3d(selection);

        drawLabels(camera, viewMatrix, matrices, points, cameraPos);
    }

    private void drawLabels(Camera camera, Matrix4f viewMatrix, MatrixStack matrices, Vec3d[] points, Vec3d cameraPos) {
        double totalLength = SplineMath.calculateSplineLength(points, SEGMENTS_PER_SPAN);
        Vec3d midPoint = SplineMath.getSplinePoint(points, 0.5);

        matrices.push();
        matrices.translate(
            midPoint.x - cameraPos.x,
            midPoint.y - cameraPos.y + 0.5,
            midPoint.z - cameraPos.z
        );
        RenderUtils.drawLabel(camera, viewMatrix, matrices, 0, 0, 0,
            String.format("len=%.1f", totalLength));
        matrices.pop();
    }

    /**
     * Draws a Catmull-Rom spline through the given points.
     */
    private void drawCatmullRomSpline(Matrix4f matrix, VertexConsumer lines, Vec3d[] points,
                                       Vec3d cameraPos, float r, float g, float b, float a) {
        int n = points.length;

        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = (i == 0) ? SplineMath.extrapolateStart(points[0], points[1]) : points[i - 1];
            Vec3d p1 = points[i];
            Vec3d p2 = points[i + 1];
            Vec3d p3 = (i == n - 2) ? SplineMath.extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            Vec3d prevPoint = p1;
            for (int seg = 1; seg <= SEGMENTS_PER_SPAN; seg++) {
                double t = (double) seg / SEGMENTS_PER_SPAN;
                Vec3d currPoint = SplineMath.catmullRom(p0, p1, p2, p3, t);
                drawLine(matrix, lines, prevPoint, currPoint, cameraPos, r, g, b, a);
                prevPoint = currPoint;
            }
        }
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

    /**
     * Draws a small cross marker at the given point.
     */
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
     * Draws tube circles along a straight line between two points.
     */
    private void drawTubeForLine(Matrix4f matrix, VertexConsumer lines, Vec3d from, Vec3d to,
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
     * Draws tube circles along a Catmull-Rom spline.
     */
    private void drawTubeForSpline(Matrix4f matrix, VertexConsumer lines, Vec3d[] points,
                                    int radius, Vec3d cameraPos, float r, float g, float b, float a) {
        int n = points.length;
        int circlesPerSpan = 4;

        Vec3d prevCenter = null;
        Vec3d prevTangent = null;

        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = (i == 0) ? SplineMath.extrapolateStart(points[0], points[1]) : points[i - 1];
            Vec3d p1 = points[i];
            Vec3d p2 = points[i + 1];
            Vec3d p3 = (i == n - 2) ? SplineMath.extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            for (int seg = 0; seg <= circlesPerSpan; seg++) {
                if (i > 0 && seg == 0) continue;

                double t = (double) seg / circlesPerSpan;
                Vec3d center = SplineMath.catmullRom(p0, p1, p2, p3, t);
                Vec3d tangent = SplineMath.catmullRomDerivative(p0, p1, p2, p3, t).normalize();

                drawTubeCircle(matrix, lines, center, tangent, radius, cameraPos, r, g, b, a);

                if (prevCenter != null) {
                    drawTubeLongitudinalLinesBetween(matrix, lines, prevCenter, center,
                        prevTangent, tangent, radius, cameraPos, r, g, b, a);
                }

                prevCenter = center;
                prevTangent = tangent;
            }
        }
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
            Vec3d from = circlePoints[i];
            Vec3d to = circlePoints[(i + 1) % TUBE_CIRCLE_SEGMENTS];
            drawLine(matrix, lines, from, to, cameraPos, r, g, b, a);
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
     * Draws longitudinal lines between two tube circles.
     */
    private void drawTubeLongitudinalLinesBetween(Matrix4f matrix, VertexConsumer lines,
                                                   Vec3d center1, Vec3d center2,
                                                   Vec3d tangent1, Vec3d tangent2, int radius,
                                                   Vec3d cameraPos, float r, float g, float b, float a) {
        Vec3d perp1_1 = SplineMath.findPerpendicular(tangent1);
        Vec3d perp2_1 = tangent1.crossProduct(perp1_1).normalize();

        Vec3d perp1_2 = SplineMath.findPerpendicular(tangent2);
        Vec3d perp2_2 = tangent2.crossProduct(perp1_2).normalize();

        int longitudinalCount = 8;
        for (int i = 0; i < longitudinalCount; i++) {
            double angle = 2 * Math.PI * i / longitudinalCount;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;

            Vec3d offset1 = perp1_1.multiply(cos).add(perp2_1.multiply(sin));
            Vec3d offset2 = perp1_2.multiply(cos).add(perp2_2.multiply(sin));

            Vec3d lineStart = center1.add(offset1);
            Vec3d lineEnd = center2.add(offset2);

            drawLine(matrix, lines, lineStart, lineEnd, cameraPos, r, g, b, a);
        }
    }

}
