package net.pvpserver.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * A YAML file in the plugin data folder backed by a bundled default copy.
 * <p>
 * On load the bundled defaults are applied and any keys missing from the user's file are written back,
 * so upgrading the plugin adds new options without overwriting edited values.
 */
public final class ConfigFile {

    private final JavaPlugin plugin;
    private final String resourcePath;
    private final File file;
    private YamlConfiguration config;

    /**
     * @param plugin owning plugin
     * @param resourcePath path of the default inside the jar and of the file relative to the data folder
     */
    public ConfigFile(JavaPlugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        reload();
    }

    /**
     * Re-reads the file from disk, creating it from the bundled default when missing.
     */
    public void reload() {
        if (!file.exists()) {
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create " + resourcePath, e);
                }
            }
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid YAML in " + resourcePath + ", using bundled defaults", e);
        }
        InputStream defaults = plugin.getResource(resourcePath);
        if (defaults != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));
            loaded.setDefaults(defaultConfig);
            loaded.options().copyDefaults(true);
            if (hasMissingKeys(loaded, defaultConfig)) {
                try {
                    loaded.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write new defaults to " + resourcePath, e);
                }
            }
        }
        this.config = loaded;
    }

    private boolean hasMissingKeys(YamlConfiguration loaded, YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!loaded.isSet(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the in-memory configuration to disk.
     */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + resourcePath, e);
        }
    }

    /** @return the live configuration */
    public YamlConfiguration get() {
        return config;
    }

    /** @return the file on disk */
    public File file() {
        return file;
    }
}
