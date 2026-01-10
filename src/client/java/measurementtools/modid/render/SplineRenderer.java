package measurementtools.modid.render;

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

/**
 * Renders a smooth spline curve through selected blocks using Catmull-Rom interpolation.
 * The curve passes through each selected block in the order they were selected.
 * Supports tube radius for creating cylindrical paths around the spline.
 */
public class SplineRenderer implements ShapeRenderer {
    // Number of line segments between each pair of control points
    private static final int SEGMENTS_PER_SPAN = 16;
    // Number of points around the tube circumference
    private static final int TUBE_CIRCLE_SEGMENTS = 16;

    @Override
    public void render(Camera camera, Matrix4f viewMatrix, List<BlockPos> selection, RenderConfig config) {
        if (selection.size() < 2) return;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        Vec3d cameraPos = camera.getPos();

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(4096));
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float r = config.red();
        float g = config.green();
        float b = config.blue();
        float a = config.alpha();

        // Convert BlockPos list to Vec3d (block centers)
        Vec3d[] points = new Vec3d[selection.size()];
        for (int i = 0; i < selection.size(); i++) {
            BlockPos pos = selection.get(i);
            points[i] = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

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
            double totalLength = calculateSplineLength(points);
            // Place label at the midpoint of the spline
            Vec3d midPoint = getSplinePoint(points, 0.5);

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

        immediate.draw();
    }

    /**
     * Draws a Catmull-Rom spline through the given points.
     */
    private void drawCatmullRomSpline(Matrix4f matrix, VertexConsumer lines, Vec3d[] points,
                                       Vec3d cameraPos, float r, float g, float b, float a) {
        // For Catmull-Rom, we need 4 points for each segment
        // We'll extrapolate phantom points at the start and end
        int n = points.length;

        for (int i = 0; i < n - 1; i++) {
            // Get the 4 control points for this segment
            Vec3d p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
            Vec3d p1 = points[i];
            Vec3d p2 = points[i + 1];
            Vec3d p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            // Draw segments along this span
            Vec3d prevPoint = p1;
            for (int seg = 1; seg <= SEGMENTS_PER_SPAN; seg++) {
                float t = (float) seg / SEGMENTS_PER_SPAN;
                Vec3d currPoint = catmullRom(p0, p1, p2, p3, t);
                drawLine(matrix, lines, prevPoint, currPoint, cameraPos, r, g, b, a);
                prevPoint = currPoint;
            }
        }
    }

    /**
     * Catmull-Rom spline interpolation.
     * Returns a point on the curve between p1 and p2.
     */
    private Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        // Catmull-Rom basis matrix coefficients
        double x = 0.5 * ((2 * p1.x) +
                         (-p0.x + p2.x) * t +
                         (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                         (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

        double y = 0.5 * ((2 * p1.y) +
                         (-p0.y + p2.y) * t +
                         (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                         (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

        double z = 0.5 * ((2 * p1.z) +
                         (-p0.z + p2.z) * t +
                         (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                         (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

        return new Vec3d(x, y, z);
    }

    /**
     * Extrapolates a phantom point before the start of the curve.
     */
    private Vec3d extrapolateStart(Vec3d p0, Vec3d p1) {
        return new Vec3d(
            2 * p0.x - p1.x,
            2 * p0.y - p1.y,
            2 * p0.z - p1.z
        );
    }

    /**
     * Extrapolates a phantom point after the end of the curve.
     */
    private Vec3d extrapolateEnd(Vec3d pn1, Vec3d pn) {
        return new Vec3d(
            2 * pn.x - pn1.x,
            2 * pn.y - pn1.y,
            2 * pn.z - pn1.z
        );
    }

    /**
     * Gets a point on the spline at parameter t (0 to 1 across entire spline).
     */
    private Vec3d getSplinePoint(Vec3d[] points, double t) {
        if (points.length < 2) return points[0];
        if (points.length == 2) {
            return new Vec3d(
                points[0].x + (points[1].x - points[0].x) * t,
                points[0].y + (points[1].y - points[0].y) * t,
                points[0].z + (points[1].z - points[0].z) * t
            );
        }

        int n = points.length;
        double scaledT = t * (n - 1);
        int i = (int) Math.floor(scaledT);
        if (i >= n - 1) i = n - 2;
        float localT = (float) (scaledT - i);

        Vec3d p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
        Vec3d p1 = points[i];
        Vec3d p2 = points[i + 1];
        Vec3d p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

        return catmullRom(p0, p1, p2, p3, localT);
    }

    /**
     * Calculates approximate total length of the spline.
     */
    private double calculateSplineLength(Vec3d[] points) {
        if (points.length < 2) return 0;

        double totalLength = 0;
        int totalSegments = (points.length - 1) * SEGMENTS_PER_SPAN;
        Vec3d prevPoint = points[0];

        for (int seg = 1; seg <= totalSegments; seg++) {
            double t = (double) seg / totalSegments;
            Vec3d currPoint = getSplinePoint(points, t);
            totalLength += prevPoint.distanceTo(currPoint);
            prevPoint = currPoint;
        }

        return totalLength;
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
        int circlesPerSpan = 4; // Number of circles between each control point pair

        Vec3d prevCenter = null;
        Vec3d prevTangent = null;

        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
            Vec3d p1 = points[i];
            Vec3d p2 = points[i + 1];
            Vec3d p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

            for (int seg = 0; seg <= circlesPerSpan; seg++) {
                // Skip first point of subsequent segments to avoid duplicates
                if (i > 0 && seg == 0) continue;

                float t = (float) seg / circlesPerSpan;
                Vec3d center = catmullRom(p0, p1, p2, p3, t);

                // Calculate tangent (derivative of Catmull-Rom)
                Vec3d tangent = catmullRomDerivative(p0, p1, p2, p3, t).normalize();

                drawTubeCircle(matrix, lines, center, tangent, radius, cameraPos, r, g, b, a);

                // Draw longitudinal lines to previous circle
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
        // Find two perpendicular vectors to the direction
        Vec3d perp1 = findPerpendicular(direction);
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
        Vec3d perp1 = findPerpendicular(direction);
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
        Vec3d perp1_1 = findPerpendicular(tangent1);
        Vec3d perp2_1 = tangent1.crossProduct(perp1_1).normalize();

        Vec3d perp1_2 = findPerpendicular(tangent2);
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

    /**
     * Finds a vector perpendicular to the given direction.
     */
    private Vec3d findPerpendicular(Vec3d direction) {
        // Use world up as reference, unless direction is nearly vertical
        Vec3d up = new Vec3d(0, 1, 0);
        if (Math.abs(direction.dotProduct(up)) > 0.99) {
            up = new Vec3d(1, 0, 0); // Use world X if direction is vertical
        }
        return direction.crossProduct(up).normalize();
    }

    /**
     * Derivative of Catmull-Rom spline (tangent direction).
     */
    private Vec3d catmullRomDerivative(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        float t2 = t * t;

        double dx = 0.5 * ((-p0.x + p2.x) +
                          2 * (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t +
                          3 * (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t2);

        double dy = 0.5 * ((-p0.y + p2.y) +
                          2 * (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t +
                          3 * (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t2);

        double dz = 0.5 * ((-p0.z + p2.z) +
                          2 * (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t +
                          3 * (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t2);

        return new Vec3d(dx, dy, dz);
    }
}
