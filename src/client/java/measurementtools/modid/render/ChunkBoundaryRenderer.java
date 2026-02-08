package measurementtools.modid.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ChunkBoundaryRenderer {
    private static final int BUFFER_SIZE = 8192;
    private static final int CHUNK_SIZE = 16;
    private static final int RENDER_RADIUS = 2; // Render chunks within this radius
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private BufferAllocator buffer;

    public void render(Camera camera, Matrix4f viewMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d cameraPos = camera.getPos();
        ChunkPos playerChunk = client.player.getChunkPos();

        RenderSystem.lineWidth(2.0f);

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);

        if (buffer == null) {
            buffer = new BufferAllocator(BUFFER_SIZE);
        }
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);
        VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Colors for different boundary types
        float currentR = 1.0f, currentG = 1.0f, currentB = 0.0f; // Yellow for current chunk
        float neighborR = 0.3f, neighborG = 0.7f, neighborB = 1.0f; // Blue for neighbor chunks

        // Render chunk boundaries within radius
        for (int dx = -RENDER_RADIUS; dx <= RENDER_RADIUS; dx++) {
            for (int dz = -RENDER_RADIUS; dz <= RENDER_RADIUS; dz++) {
                int chunkX = playerChunk.x + dx;
                int chunkZ = playerChunk.z + dz;

                boolean isCurrentChunk = (dx == 0 && dz == 0);
                float r = isCurrentChunk ? currentR : neighborR;
                float g = isCurrentChunk ? currentG : neighborG;
                float b = isCurrentChunk ? currentB : neighborB;
                float a = isCurrentChunk ? 1.0f : 0.6f;

                renderChunkBoundary(matrix, lines, cameraPos, chunkX, chunkZ, r, g, b, a);
            }
        }

        immediate.draw();

        // Draw chunk coordinate labels
        drawChunkLabels(camera, viewMatrix, playerChunk, cameraPos);
    }

    private void renderChunkBoundary(Matrix4f matrix, VertexConsumer lines, Vec3d cameraPos,
                                      int chunkX, int chunkZ, float r, float g, float b, float a) {
        // Calculate world coordinates for chunk boundaries
        float x1 = (float) (chunkX * CHUNK_SIZE - cameraPos.x);
        float x2 = (float) ((chunkX + 1) * CHUNK_SIZE - cameraPos.x);
        float z1 = (float) (chunkZ * CHUNK_SIZE - cameraPos.z);
        float z2 = (float) ((chunkZ + 1) * CHUNK_SIZE - cameraPos.z);

        // Render Y range based on camera position to avoid rendering too many lines
        int viewMinY = Math.max(MIN_Y, (int) cameraPos.y - 64);
        int viewMaxY = Math.min(MAX_Y, (int) cameraPos.y + 64);

        float y1 = (float) (viewMinY - cameraPos.y);
        float y2 = (float) (viewMaxY - cameraPos.y);

        // Draw the four vertical edges of the chunk
        RenderUtils.drawLine(matrix, lines, x1, y1, z1, x1, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z1, x2, y2, z1, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x1, y1, z2, x1, y2, z2, r, g, b, a);
        RenderUtils.drawLine(matrix, lines, x2, y1, z2, x2, y2, z2, r, g, b, a);

        // Draw horizontal lines at regular Y intervals
        int yStep = 16;
        for (int y = ((viewMinY / yStep) * yStep); y <= viewMaxY; y += yStep) {
            float yPos = (float) (y - cameraPos.y);

            // Bottom edges
            RenderUtils.drawLine(matrix, lines, x1, yPos, z1, x2, yPos, z1, r, g, b, a);
            RenderUtils.drawLine(matrix, lines, x1, yPos, z2, x2, yPos, z2, r, g, b, a);
            RenderUtils.drawLine(matrix, lines, x1, yPos, z1, x1, yPos, z2, r, g, b, a);
            RenderUtils.drawLine(matrix, lines, x2, yPos, z1, x2, yPos, z2, r, g, b, a);
        }
    }

    private void drawChunkLabels(Camera camera, Matrix4f viewMatrix, ChunkPos playerChunk, Vec3d cameraPos) {
        // Draw label for current chunk at a visible position
        double labelX = (playerChunk.x * CHUNK_SIZE) + CHUNK_SIZE / 2.0;
        double labelY = cameraPos.y + 2;
        double labelZ = (playerChunk.z * CHUNK_SIZE) + CHUNK_SIZE / 2.0;

        String chunkLabel = "Chunk [" + playerChunk.x + ", " + playerChunk.z + "]";
        RenderUtils.drawWorldLabel(camera, viewMatrix, labelX, labelY, labelZ, chunkLabel);
    }
}
