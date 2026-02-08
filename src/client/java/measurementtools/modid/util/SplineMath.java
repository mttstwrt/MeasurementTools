package measurementtools.modid.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Utility class for Catmull-Rom spline calculations.
 * Consolidates spline math used across BlockCounter, HollowBlockCalculator, and SplineRenderer.
 */
public final class SplineMath {

    private SplineMath() {}

    /**
     * Catmull-Rom spline interpolation between p1 and p2.
     * @param p0 control point before p1
     * @param p1 start point of segment
     * @param p2 end point of segment
     * @param p3 control point after p2
     * @param t interpolation parameter [0, 1]
     * @return interpolated point
     */
    public static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

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
     * Derivative of Catmull-Rom spline (tangent direction).
     */
    public static Vec3d catmullRomDerivative(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;

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

    /**
     * Extrapolates a phantom control point before the start of the curve.
     */
    public static Vec3d extrapolateStart(Vec3d p0, Vec3d p1) {
        return new Vec3d(
            2 * p0.x - p1.x,
            2 * p0.y - p1.y,
            2 * p0.z - p1.z
        );
    }

    /**
     * Extrapolates a phantom control point after the end of the curve.
     */
    public static Vec3d extrapolateEnd(Vec3d pn1, Vec3d pn) {
        return new Vec3d(
            2 * pn.x - pn1.x,
            2 * pn.y - pn1.y,
            2 * pn.z - pn1.z
        );
    }

    /**
     * Gets a point on the spline at parameter t (0 to 1 across entire spline).
     */
    public static Vec3d getSplinePoint(Vec3d[] points, double t) {
        if (points.length < 2) return points[0];
        if (points.length == 2) {
            return points[0].lerp(points[1], t);
        }

        int n = points.length;
        double scaledT = t * (n - 1);
        int i = (int) Math.floor(scaledT);
        if (i >= n - 1) i = n - 2;
        double localT = scaledT - i;

        Vec3d p0 = (i == 0) ? extrapolateStart(points[0], points[1]) : points[i - 1];
        Vec3d p1 = points[i];
        Vec3d p2 = points[i + 1];
        Vec3d p3 = (i == n - 2) ? extrapolateEnd(points[n - 2], points[n - 1]) : points[i + 2];

        return catmullRom(p0, p1, p2, p3, localT);
    }

    /**
     * Gets the minimum distance from a point to the spline curve.
     * @param point the point to measure from
     * @param controlPoints the spline control points
     * @param samplesPerSegment number of samples per segment (higher = more accurate)
     */
    public static double getMinDistanceToSpline(Vec3d point, Vec3d[] controlPoints, int samplesPerSegment) {
        double minDist = Double.MAX_VALUE;
        int n = controlPoints.length;

        for (int i = 0; i < n - 1; i++) {
            Vec3d p0 = (i == 0) ? extrapolateStart(controlPoints[0], controlPoints[1]) : controlPoints[i - 1];
            Vec3d p1 = controlPoints[i];
            Vec3d p2 = controlPoints[i + 1];
            Vec3d p3 = (i == n - 2) ? extrapolateEnd(controlPoints[n - 2], controlPoints[n - 1]) : controlPoints[i + 2];

            for (int seg = 0; seg <= samplesPerSegment; seg++) {
                double t = (double) seg / samplesPerSegment;
                Vec3d splinePoint = catmullRom(p0, p1, p2, p3, t);
                double dist = point.distanceTo(splinePoint);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }

        return minDist;
    }

    /**
     * Converts a list of BlockPos to an array of Vec3d (block centers).
     */
    public static Vec3d[] blockPosListToVec3d(List<BlockPos> positions) {
        Vec3d[] points = new Vec3d[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            points[i] = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        return points;
    }

    /**
     * Calculates approximate total length of the spline.
     */
    public static double calculateSplineLength(Vec3d[] points, int segmentsPerSpan) {
        if (points.length < 2) return 0;

        double totalLength = 0;
        int totalSegments = (points.length - 1) * segmentsPerSpan;
        Vec3d prevPoint = points[0];

        for (int seg = 1; seg <= totalSegments; seg++) {
            double t = (double) seg / totalSegments;
            Vec3d currPoint = getSplinePoint(points, t);
            totalLength += prevPoint.distanceTo(currPoint);
            prevPoint = currPoint;
        }

        return totalLength;
    }

    /**
     * Finds a vector perpendicular to the given direction.
     */
    public static Vec3d findPerpendicular(Vec3d direction) {
        Vec3d up = new Vec3d(0, 1, 0);
        if (Math.abs(direction.dotProduct(up)) > 0.99) {
            up = new Vec3d(1, 0, 0);
        }
        return direction.crossProduct(up).normalize();
    }
}
