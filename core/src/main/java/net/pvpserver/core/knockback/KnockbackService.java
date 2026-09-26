package net.pvpserver.core.knockback;

import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, edits and saves knockback profiles in kb.yml.
 */
public final class KnockbackService implements Reloadable {

    private final ConfigFile file;
    private final Map<String, KnockbackProfile> profiles = new LinkedHashMap<>();
    private String defaultProfile = "default";
    private boolean enabled = true;

    /**
     * @param plugin core plugin
     */
    public KnockbackService(JavaPlugin plugin) {
        this.file = new ConfigFile(plugin, "kb.yml", true);
        reload();
    }

    @Override
    public void reload() {
        file.reload();
        profiles.clear();
        enabled = file.get().getBoolean("enabled", true);
        defaultProfile = file.get().getString("default-profile", "default").toLowerCase(Locale.ROOT);
        ConfigurationSection root = file.get().getConfigurationSection("profiles");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s != null) {
                    String id = name.toLowerCase(Locale.ROOT);
                    profiles.put(id, new KnockbackProfile(id, s.getDouble("horizontal", 0.4), s.getDouble("vertical", 0.4),
                            s.getDouble("friction", 2.0), s.getDouble("extra-horizontal", 0.5), s.getDouble("extra-vertical", 0.1),
                            s.getDouble("vertical-limit", 0.4), s.getDouble("air-horizontal-multiplier", 1.0)));
                }
            }
        }
        profiles.putIfAbsent(defaultProfile, new KnockbackProfile(defaultProfile, 0.4, 0.4, 2.0, 0.5, 0.1, 0.4, 1.0));
    }

    /** @return whether custom knockback is enabled at all */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @param name profile id (null/blank = default)
     * @return profile, falling back to the default
     */
    public KnockbackProfile profile(String name) {
        if (name == null || name.isBlank()) {
            return profiles.get(defaultProfile);
        }
        return profiles.getOrDefault(name.toLowerCase(Locale.ROOT), profiles.get(defaultProfile));
    }

    /**
     * @param name profile id
     * @return whether it exists
     */
    public boolean exists(String name) {
        return profiles.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** @return all profiles */
    public Collection<KnockbackProfile> profiles() {
        return profiles.values();
    }

    /** @return default profile id */
    public String defaultProfile() {
        return defaultProfile;
    }

    /**
     * Stores (creates or replaces) a profile and writes kb.yml.
     *
     * @param profile profile
     */
    public void put(KnockbackProfile profile) {
        profiles.put(profile.name(), profile);
        String base = "profiles." + profile.name() + ".";
        file.get().set(base + "horizontal", profile.horizontal());
        file.get().set(base + "vertical", profile.vertical());
        file.get().set(base + "friction", profile.friction());
        file.get().set(base + "extra-horizontal", profile.extraHorizontal());
        file.get().set(base + "extra-vertical", profile.extraVertical());
        file.get().set(base + "vertical-limit", profile.verticalLimit());
        file.get().set(base + "air-horizontal-multiplier", profile.airHorizontalMultiplier());
        file.save();
    }

    /**
     * @param name profile to delete (the default cannot be deleted)
     * @return whether it was deleted
     */
    public boolean delete(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (id.equals(defaultProfile) || profiles.remove(id) == null) {
            return false;
        }
        file.get().set("profiles." + id, null);
        file.save();
        return true;
    }

    /**
     * @param name new default profile
     */
    public void setDefault(String name) {
        defaultProfile = name.toLowerCase(Locale.ROOT);
        file.get().set("default-profile", defaultProfile);
        file.save();
    }
}
