package com.basescanner.config;

import com.basescanner.BaseScannerClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link ModConfig} as JSON in the game's config folder. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basescanner.json");

    private static ModConfig config = new ModConfig();

    private ConfigManager() {
    }

    public static ModConfig get() {
        return config;
    }

    public static void load() {
        ModConfig loaded = null;
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                BaseScannerClient.LOGGER.error("Could not read {}, using defaults", FILE, e);
            }
        }
        config = loaded != null ? loaded : new ModConfig();
        config.normalize();
        save();
    }

    public static void save() {
        config.normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("basescanner.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            BaseScannerClient.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    public static void resetToDefaults() {
        config = new ModConfig();
        config.normalize();
        save();
    }
}
