package io.github.mcengine.extension.addon.essential.home.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for creating and ensuring the main configuration file
 * for the Essential Home AddOn exists.
 *
 * <p>Mirrors the behavior of the Entity add-on configuration utility:
 * creates <code>config.yml</code> under a supplied folder path and
 * sets sane defaults on first run.</p>
 */
public final class HomeConfigUtil {

    private HomeConfigUtil() {
        // Utility class; no instances.
    }

    /**
     * Creates the default <code>config.yml</code> for the Home AddOn if it does not exist.
     *
     * @param plugin     The plugin instance used to resolve the data folder.
     * @param folderPath The folder path relative to the plugin's data directory
     *                   (e.g., "extensions/addons/configs/MCEngineHome").
     * @param logger     Logger for reporting creation outcomes.
     */
    public static void createConfig(Plugin plugin, String folderPath, MCEngineExtensionLogger logger) {
        File configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");

        if (configFile.exists()) {
            return;
        }

        File configDir = configFile.getParentFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.warning("Failed to create Home config directory: " + configDir.getAbsolutePath());
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.options().header("Configuration file for Essential Home AddOn");

        // Required default: license must be "free"
        config.set("license", "free");

        try {
            config.save(configFile);
            logger.info("Created default Home config: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.warning("Failed to save Home config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
