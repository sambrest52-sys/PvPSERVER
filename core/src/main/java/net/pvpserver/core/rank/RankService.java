package net.pvpserver.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Built-in rank system with an optional LuckPerms layer. With {@code provider: luckperms} (or {@code auto} and
 * LuckPerms installed) prefixes and ordering come from LuckPerms and built-in permissions are not applied.
 */
public final class RankService implements Reloadable, Listener {

    private final JavaPlugin plugin;
    private final ConfigFile file;
    private final ProfileService profiles;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private String defaultRank = "default";
    private LuckPermsHook luckPerms;

    /**
     * @param plugin core plugin
     * @param profiles profiles
     */
    public RankService(JavaPlugin plugin, ProfileService profiles) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, "ranks.yml");
        this.profiles = profiles;
        reload();
    }

    @Override
    public void reload() {
        file.reload();
        ranks.clear();
        defaultRank = file.get().getString("default-rank", "default").toLowerCase(Locale.ROOT);
        ConfigurationSection root = file.get().getConfigurationSection("ranks");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                ranks.put(key, new Rank(key, s.getString("display", id), s.getString("prefix", ""),
                        s.getString("name-color", "<gray>"), s.getInt("weight", 0), s.getStringList("permissions"),
                        s.getString("inherits"), s.getBoolean("staff", false)));
            }
        }
        if (!ranks.containsKey(defaultRank)) {
            ranks.put(defaultRank, new Rank(defaultRank, "Default", "", "<gray>", 0, List.of(), null, false));
        }
        String provider = file.get().getString("provider", "auto").toLowerCase(Locale.ROOT);
        boolean lpPresent = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        luckPerms = null;
        if (lpPresent && !provider.equals("builtin")) {
            try {
                luckPerms = new LuckPermsHook();
                plugin.getLogger().info("Using LuckPerms for prefixes and permissions");
            } catch (Throwable t) {
                plugin.getLogger().warning("LuckPerms found but its API is unavailable: " + t.getMessage());
            }
        } else if (provider.equals("luckperms")) {
            plugin.getLogger().warning("ranks.yml provider is 'luckperms' but LuckPerms is not installed; using built-in ranks");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPermissions(player);
        }
    }

    /** @return default rank id */
    public String defaultRankId() {
        return defaultRank;
    }

    /** @return whether LuckPerms is the active provider */
    public boolean usingLuckPerms() {
        return luckPerms != null;
    }

    /**
     * @param id rank id
     * @return rank or null
     */
    public Rank rank(String id) {
        return id == null ? null : ranks.get(id.toLowerCase(Locale.ROOT));
    }

    /** @return all ranks */
    public Collection<Rank> ranks() {
        return ranks.values();
    }

    /**
     * @param player player
     * @return built-in rank (default when unknown)
     */
    public Rank rankOf(Player player) {
        PlayerProfile profile = profiles.get(player);
        Rank rank = profile == null ? null : rank(profile.rankId());
        return rank == null ? ranks.get(defaultRank) : rank;
    }

    /**
     * @param player player
     * @return chat/tab prefix component
     */
    public Component prefix(Player player) {
        if (luckPerms != null) {
            return parseExternal(luckPerms.prefix(player));
        }
        return MessageService.mini().deserialize(rankOf(player).prefix());
    }

    /**
     * @param player player
     * @return name coloured by rank
     */
    public Component coloredName(Player player) {
        if (luckPerms != null) {
            return Component.text(player.getName());
        }
        return MessageService.mini().deserialize(rankOf(player).nameColor() + "<name>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", player.getName()));
    }

    /**
     * @param player player
     * @return sort weight (higher first)
     */
    public int weight(Player player) {
        return luckPerms != null ? luckPerms.weight(player) : rankOf(player).weight();
    }

    /**
     * @param player player
     * @return whether the player holds a staff rank or the staff permission
     */
    public boolean isStaff(Player player) {
        return player.hasPermission("pvp.staff") || rankOf(player).staff();
    }

    private static Component parseExternal(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.indexOf('&') >= 0 || raw.indexOf('§') >= 0) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(raw.replace('§', '&'));
        }
        return MessageService.mini().deserialize(raw);
    }

    /**
     * Sets a player's built-in rank (online or offline).
     *
     * @param uuid player id
     * @param rankId rank id
     * @return completion
     */
    public CompletableFuture<Void> setRank(UUID uuid, String rankId) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile != null) {
            profile.rankId(rankId);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                applyPermissions(player);
            }
        }
        return profiles.repository().setRank(uuid, rankId);
    }

    /**
     * Applies built-in rank permissions (no-op when LuckPerms is the provider).
     *
     * @param player player
     */
    public void applyPermissions(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // attachment already gone
            }
        }
        if (luckPerms != null) {
            return;
        }
        Set<String> nodes = collectPermissions(rankOf(player), new HashSet<>());
        if (nodes.isEmpty()) {
            return;
        }
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String node : nodes) {
            boolean negate = node.startsWith("-");
            String clean = negate ? node.substring(1) : node;
            for (String expanded : expand(clean)) {
                attachment.setPermission(expanded, !negate);
            }
        }
        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
    }

    private Set<String> collectPermissions(Rank rank, Set<String> visited) {
        Set<String> nodes = new HashSet<>();
        if (rank == null || !visited.add(rank.id())) {
            return nodes;
        }
        if (rank.inherits() != null) {
            nodes.addAll(collectPermissions(rank(rank.inherits()), visited));
        }
        nodes.addAll(rank.permissions());
        return nodes;
    }

    private List<String> expand(String node) {
        if (!node.endsWith("*")) {
            return List.of(node);
        }
        String prefix = node.substring(0, node.length() - 1);
        List<String> expanded = new ArrayList<>();
        for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
            if (permission.getName().startsWith(prefix)) {
                expanded.add(permission.getName());
            }
        }
        expanded.add(node);
        return expanded;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        applyPermissions(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
    }
}
