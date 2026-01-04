package measurementtools.modid.gui;

import measurementtools.modid.BlockCounter;
import measurementtools.modid.SelectionManager;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

import java.util.Map;

public class BlockCountOverlay {
    private static final BlockCountOverlay INSTANCE = new BlockCountOverlay();
    private static final int PADDING = 5;
    private static final int ICON_SIZE = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_ROWS = 20;

    private BlockCountOverlay() {}

    public static BlockCountOverlay getInstance() {
        return INSTANCE;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        SelectionManager manager = SelectionManager.getInstance();

        if (!manager.isBlockCountingEnabled() || !manager.hasSelection()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        TextRenderer textRenderer = client.textRenderer;
        Map<Block, Integer> blockCounts = BlockCounter.getInstance().getBlockCounts();

        if (blockCounts.isEmpty()) return;

        int totalBlocks = BlockCounter.getInstance().getTotalBlockCount();

        // Calculate total height for vertical centering
        int visibleRows = Math.min(blockCounts.size(), MAX_VISIBLE_ROWS);
        int totalHeight = ROW_HEIGHT + visibleRows * ROW_HEIGHT + ROW_HEIGHT; // header + rows + total

        // Center vertically on left side
        int screenHeight = client.getWindow().getScaledHeight();
        int y = (screenHeight - totalHeight) / 2;
        int x = PADDING;

        // Draw header
        String header = "";
        context.drawText(textRenderer, header, x + ICON_SIZE + 4, y + 4, 0xFFFFFF00, true);
        y += ROW_HEIGHT;

        // Draw each block entry
        int rowCount = 0;
        for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
            if (rowCount >= MAX_VISIBLE_ROWS) {
                int remaining = blockCounts.size() - MAX_VISIBLE_ROWS;
                context.drawText(textRenderer, "... +" + remaining + " more", x + ICON_SIZE + 4, y + 4, 0xFFAAAAAA, true);
                break;
            }

            Block block = entry.getKey();
            int count = entry.getValue();

            // Draw block icon
            ItemStack itemStack = new ItemStack(block.asItem());
            context.drawItem(itemStack, x, y);

            // Draw count
            String countText = formatCount(count);
            context.drawText(textRenderer, countText, x + ICON_SIZE + 4, y + 4, 0xFFFFFFFF, true);

            y += ROW_HEIGHT;
            rowCount++;
        }

        // Draw total
        y += 4;
        String totalText = formatCount(totalBlocks);
        context.drawText(textRenderer, totalText, x + ICON_SIZE + 4, y, 0xFF88FF88, true);
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        } else if (count >= 1_000) {
            return String.format("%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }
}
