package measurementtools.modid.render;

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
 */
public class SplineRenderer implements ShapeRenderer {
    // Number of line segments between each pair of control points
    private static final int SEGMENTS_PER_SPAN = 16;

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

        // Draw the spline
        if (points.length == 2) {
            // Just two points - draw a straight line
            drawLine(matrix, lines, points[0], points[1], cameraPos, r, g, b, a);
        } else {
            // Three or more points - draw Catmull-Rom spline
            drawCatmullRomSpline(matrix, lines, points, cameraPos, r, g, b, a);
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
}
