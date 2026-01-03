package measurementtools.modid.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class SubdivisionRenderer {

    // Subdivision line color (yellow)
    private static final float SUB_R = 1.0f;
    private static final float SUB_G = 1.0f;
    private static final float SUB_B = 0.3f;

    public void renderSubdivisions(Camera camera, Matrix4f viewMatrix, Box bounds, int subdivisions, float alpha) {
        if (subdivisions <= 1) return;

        Vec3d cameraPos = camera.getPos();

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1024));
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        // Translate bounds relative to camera
        Box relativeBounds = new Box(
            bounds.minX - cameraPos.x, bounds.minY - cameraPos.y, bounds.minZ - cameraPos.z,
            bounds.maxX - cameraPos.x, bounds.maxY - cameraPos.y, bounds.maxZ - cameraPos.z
        );

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Render subdivisions on each axis
        renderAxisSubdivisions(camera, matrices, lines, matrix, relativeBounds, subdivisions, Axis.X, alpha);
        renderAxisSubdivisions(camera, matrices, lines, matrix, relativeBounds, subdivisions, Axis.Y, alpha);
        renderAxisSubdivisions(camera, matrices, lines, matrix, relativeBounds, subdivisions, Axis.Z, alpha);

        immediate.draw();

        // Draw labels in world space
        renderAxisLabels(camera, viewMatrix, bounds, subdivisions, Axis.X);
        renderAxisLabels(camera, viewMatrix, bounds, subdivisions, Axis.Y);
        renderAxisLabels(camera, viewMatrix, bounds, subdivisions, Axis.Z);
    }

    private void renderAxisSubdivisions(Camera camera, MatrixStack matrices,
                                        VertexConsumer lines, Matrix4f matrix,
                                        Box bounds, int subdivisions, Axis axis, float alpha) {
        double start, end;
        double perpMin1, perpMax1, perpMin2, perpMax2;

        switch (axis) {
            case X:
                start = bounds.minX;
                end = bounds.maxX;
                perpMin1 = bounds.minY;
                perpMax1 = bounds.maxY;
                perpMin2 = bounds.minZ;
                perpMax2 = bounds.maxZ;
                break;
            case Y:
                start = bounds.minY;
                end = bounds.maxY;
                perpMin1 = bounds.minX;
                perpMax1 = bounds.maxX;
                perpMin2 = bounds.minZ;
                perpMax2 = bounds.maxZ;
                break;
            case Z:
            default:
                start = bounds.minZ;
                end = bounds.maxZ;
                perpMin1 = bounds.minX;
                perpMax1 = bounds.maxX;
                perpMin2 = bounds.minY;
                perpMax2 = bounds.maxY;
                break;
        }

        double span = end - start;
        if (span < 0.01) return;

        double segmentSize = span / subdivisions;

        // Draw subdivision lines (not at start or end)
        for (int i = 1; i < subdivisions; i++) {
            double pos = start + (i * segmentSize);
            drawSubdivisionPlane(lines, matrix, axis, pos,
                perpMin1, perpMax1, perpMin2, perpMax2, alpha);
        }
    }

    private void renderAxisLabels(Camera camera, Matrix4f viewMatrix,
                                  Box bounds, int subdivisions, Axis axis) {
        double start, end;

        switch (axis) {
            case X:
                start = bounds.minX;
                end = bounds.maxX;
                break;
            case Y:
                start = bounds.minY;
                end = bounds.maxY;
                break;
            case Z:
            default:
                start = bounds.minZ;
                end = bounds.maxZ;
                break;
        }

        double span = end - start;
        if (span < 0.01) return;

        double segmentSize = span / subdivisions;

        // Draw segment labels at midpoints
        for (int i = 0; i < subdivisions; i++) {
            double segMid = start + (i + 0.5) * segmentSize;

            double labelX, labelY, labelZ;
            switch (axis) {
                case X:
                    labelX = segMid;
                    labelY = (bounds.minY + bounds.maxY) / 2;
                    labelZ = bounds.maxZ + 0.5;
                    break;
                case Y:
                    labelX = (bounds.minX + bounds.maxX) / 2;
                    labelY = segMid;
                    labelZ = bounds.maxZ + 0.5;
                    break;
                case Z:
                default:
                    labelX = bounds.maxX + 0.5;
                    labelY = (bounds.minY + bounds.maxY) / 2;
                    labelZ = segMid;
                    break;
            }

            RenderUtils.drawWorldLabel(camera, viewMatrix, labelX, labelY, labelZ,
                String.format("%.1f", segmentSize));
        }
    }

    private void drawSubdivisionPlane(VertexConsumer lines, Matrix4f matrix,
                                      Axis axis, double pos,
                                      double p1Min, double p1Max, double p2Min, double p2Max,
                                      float alpha) {
        switch (axis) {
            case X:
                RenderUtils.drawLine(matrix, lines,
                    (float) pos, (float) p1Min, (float) p2Min,
                    (float) pos, (float) p1Max, (float) p2Min,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) pos, (float) p1Min, (float) p2Max,
                    (float) pos, (float) p1Max, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) pos, (float) p1Min, (float) p2Min,
                    (float) pos, (float) p1Min, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) pos, (float) p1Max, (float) p2Min,
                    (float) pos, (float) p1Max, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                break;
            case Y:
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) pos, (float) p2Min,
                    (float) p1Max, (float) pos, (float) p2Min,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) pos, (float) p2Max,
                    (float) p1Max, (float) pos, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) pos, (float) p2Min,
                    (float) p1Min, (float) pos, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Max, (float) pos, (float) p2Min,
                    (float) p1Max, (float) pos, (float) p2Max,
                    SUB_R, SUB_G, SUB_B, alpha);
                break;
            case Z:
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) p2Min, (float) pos,
                    (float) p1Max, (float) p2Min, (float) pos,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) p2Max, (float) pos,
                    (float) p1Max, (float) p2Max, (float) pos,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Min, (float) p2Min, (float) pos,
                    (float) p1Min, (float) p2Max, (float) pos,
                    SUB_R, SUB_G, SUB_B, alpha);
                RenderUtils.drawLine(matrix, lines,
                    (float) p1Max, (float) p2Min, (float) pos,
                    (float) p1Max, (float) p2Max, (float) pos,
                    SUB_R, SUB_G, SUB_B, alpha);
                break;
        }
    }

    public enum Axis {
        X, Y, Z
    }
}
