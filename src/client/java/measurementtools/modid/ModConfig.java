package measurementtools.modid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static ModConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("measurementtools.json");

    public enum GhostBlockRenderMode {
        WIREFRAME,
        SOLID
    }

    private GhostBlockRenderMode ghostBlockRenderMode = GhostBlockRenderMode.WIREFRAME;
    private float ghostBlockOpacity = 0.5f;

    private ModConfig() {
        load();
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    public GhostBlockRenderMode getGhostBlockRenderMode() {
        return ghostBlockRenderMode;
    }

    public void setGhostBlockRenderMode(GhostBlockRenderMode mode) {
        this.ghostBlockRenderMode = mode;
        save();
    }

    public float getGhostBlockOpacity() {
        return ghostBlockOpacity;
    }

    public void setGhostBlockOpacity(float opacity) {
        this.ghostBlockOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        save();
    }

    private void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    if (data.ghostBlockRenderMode != null) {
                        this.ghostBlockRenderMode = data.ghostBlockRenderMode;
                    }
                    this.ghostBlockOpacity = Math.max(0.0f, Math.min(1.0f, data.ghostBlockOpacity));
                }
            } catch (IOException e) {
                System.err.println("Failed to load MeasurementTools config: " + e.getMessage());
            }
        }
    }

    private void save() {
        try {
            ConfigData data = new ConfigData();
            data.ghostBlockRenderMode = this.ghostBlockRenderMode;
            data.ghostBlockOpacity = this.ghostBlockOpacity;
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            System.err.println("Failed to save MeasurementTools config: " + e.getMessage());
        }
    }

    private static class ConfigData {
        GhostBlockRenderMode ghostBlockRenderMode = GhostBlockRenderMode.WIREFRAME;
        float ghostBlockOpacity = 0.5f;
    }
}
