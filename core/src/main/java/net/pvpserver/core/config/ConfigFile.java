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
 * By default the bundled defaults are applied on load and any keys missing from the user's file are written back,
 * so upgrading the plugin adds new options without overwriting edited values.
 * <p>
 * Files whose entries are whole definitions (kits, knockback profiles) are <em>versioned</em> instead: they are not
 * merged key by key (a deleted kit or item must stay deleted), and when the bundled {@code config-version} is newer
 * than the user's, the old file is kept as {@code <name>.v<old>.bak} and replaced by the new default.
 */
public final class ConfigFile {

    /** Key holding the file format version of versioned files. */
    public static final String VERSION_KEY = "config-version";

    private final JavaPlugin plugin;
    private final String resourcePath;
    private final File file;
    private final boolean versioned;
    private YamlConfiguration config;

    /**
     * @param plugin owning plugin
     * @param resourcePath path of the default inside the jar and of the file relative to the data folder
     */
    public ConfigFile(JavaPlugin plugin, String resourcePath) {
        this(plugin, resourcePath, false);
    }

    /**
     * @param plugin owning plugin
     * @param resourcePath path of the default inside the jar and of the file relative to the data folder
     * @param versioned replace outdated files (with a backup) instead of merging missing keys
     */
    public ConfigFile(JavaPlugin plugin, String resourcePath, boolean versioned) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        this.versioned = versioned;
        reload();
    }

    /**
     * Re-reads the file from disk, creating it from the bundled default when missing.
     */
    public void reload() {
        if (versioned && file.exists()) {
            upgradeIfOutdated();
        }
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
        InputStream defaults = versioned ? null : plugin.getResource(resourcePath);
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

    private void upgradeIfOutdated() {
        int bundled = bundledVersion();
        if (bundled <= 0) {
            return;
        }
        int current = YamlConfiguration.loadConfiguration(file).getInt(VERSION_KEY, 1);
        if (current >= bundled) {
            return;
        }
        File backup = new File(file.getParentFile(), file.getName() + ".v" + current + ".bak");
        for (int i = 2; backup.exists(); i++) {
            backup = new File(file.getParentFile(), file.getName() + ".v" + current + "-" + i + ".bak");
        }
        if (!file.renameTo(backup)) {
            plugin.getLogger().severe("Could not back up outdated " + resourcePath + "; keeping it");
            return;
        }
        plugin.getLogger().warning(resourcePath + " was version " + current + "; replaced with the new version " + bundled
                + " defaults. Your old file was saved as " + backup.getName());
    }

    private int bundledVersion() {
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return 0;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getInt(VERSION_KEY, 0);
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
