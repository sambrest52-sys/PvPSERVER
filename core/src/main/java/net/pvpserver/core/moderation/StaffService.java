package net.pvpserver.core.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Vanish, freeze, staff chat and staff alerts.
 */
public final class StaffService implements Listener {

    /** Permission to see vanished staff. */
    public static final String SEE_VANISHED = "pvp.staff.vanish.see";
    /** Permission to receive staff alerts. */
    public static final String ALERTS = "pvp.staff.alerts";

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private List<String> frozenAllowedCommands = List.of("msg", "r", "reply", "sc", "staffchat");
    private Consumer<Player> onVanishChange = p -> { };

    /**
     * @param plugin core plugin
     * @param messages messages
     */
    public StaffService(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        Tasks.timer(this::remindFrozen, 100L, 100L);
    }

    /**
     * @param allowed commands frozen players may still use
     */
    public void configure(List<String> allowed) {
        this.frozenAllowedCommands = allowed.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * @param callback called after a player's vanish state changes (tab name refresh)
     */
    public void onVanishChange(Consumer<Player> callback) {
        this.onVanishChange = callback;
    }

    // ---------------------------------------------------------------- vanish

    /**
     * @param player player
     * @return whether vanished
     */
    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    /**
     * Toggles vanish.
     *
     * @param player staff member
     * @return new state
     */
    public boolean toggleVanish(Player player) {
        boolean now = !vanished.remove(player.getUniqueId());
        if (now) {
            vanished.add(player.getUniqueId());
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            if (now && !other.hasPermission(SEE_VANISHED)) {
                other.hidePlayer(plugin, player);
            } else {
                other.showPlayer(plugin, player);
            }
        }
        onVanishChange.accept(player);
        return now;
    }

    /**
     * Whether {@code viewer} may see {@code target} considering vanish (gamemodes use this before re-showing players).
     *
     * @param viewer viewer
     * @param target target
     * @return visibility allowed by vanish
     */
    public boolean canSee(Player viewer, Player target) {
        return !isVanished(target) || viewer.hasPermission(SEE_VANISHED);
    }

    // ---------------------------------------------------------------- freeze

    /**
     * @param player player
     * @return whether frozen
     */
    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    /**
     * @param target player to (un)freeze
     * @return new state
     */
    public boolean toggleFreeze(Player target) {
        if (frozen.remove(target.getUniqueId())) {
            messages.send(target, "staff.unfrozen");
            return false;
        }
        frozen.add(target.getUniqueId());
        messages.send(target, "staff.frozen");
        messages.title(target, "staff.frozen-title", 5, 60, 10);
        return true;
    }

    private void remindFrozen() {
        for (UUID uuid : frozen) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                messages.send(player, "staff.frozen");
            }
        }
    }

    // ---------------------------------------------------------------- staff chat

    /**
     * @param player staff member
     * @return new toggle state
     */
    public boolean toggleStaffChat(Player player) {
        if (staffChat.remove(player.getUniqueId())) {
            return false;
        }
        staffChat.add(player.getUniqueId());
        return true;
    }

    /**
     * @param player player
     * @return whether chat goes to staff chat
     */
    public boolean inStaffChat(Player player) {
        return staffChat.contains(player.getUniqueId());
    }

    /**
     * @param senderName sender display name
     * @param message message text
     */
    public void staffChat(String senderName, String message) {
        alert("staff.chat-format", MessageService.p("player", senderName), MessageService.p("message", message));
    }

    /**
     * Sends a message to every online staff member with the alerts permission and to the console.
     *
     * @param key message key
     * @param resolvers placeholders
     */
    public void alert(String key, TagResolver... resolvers) {
        Component component = messages.get(key, resolvers);
        alert(component);
    }

    /**
     * @param component alert
     */
    public void alert(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(ALERTS)) {
                player.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }

    // ---------------------------------------------------------------- listeners

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (!joined.hasPermission(SEE_VANISHED)) {
            for (UUID uuid : vanished) {
                Player staff = Bukkit.getPlayer(uuid);
                if (staff != null) {
                    joined.hidePlayer(plugin, staff);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        vanished.remove(player.getUniqueId());
        staffChat.remove(player.getUniqueId());
        if (frozen.remove(player.getUniqueId())) {
            alert("staff.frozen-quit", MessageService.p("player", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (!frozenAllowedCommands.contains(label)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "staff.frozen-command");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenInteract(PlayerInteractEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDrop(PlayerDropItemEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenPlace(BlockPlaceEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenBreak(BlockBreakEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
