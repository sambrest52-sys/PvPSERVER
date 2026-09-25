package net.pvpserver.lobby;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.event.PlayerStateChangeEvent;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.util.Vector;

/**
 * Lobby protection: no damage, hunger, block edits, drops or pickups; void rescue; double jump; join handling.
 */
public final class LobbyListener implements Listener {

    private final PracticeApi api;
    private final LobbyService lobby;
    private final PvPLobby plugin;

    /**
     * @param plugin lobby plugin
     * @param api practice api
     * @param lobby lobby service
     */
    public LobbyListener(PvPLobby plugin, PracticeApi api, LobbyService lobby) {
        this.plugin = plugin;
        this.api = api;
        this.lobby = lobby;
    }

    private boolean inLobby(Player player) {
        PlayerState state = api.states().get(player);
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE || state == PlayerState.EDITING;
    }

    private boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("pvp.lobby.build");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        lobby.sendToLobby(player);
        Component custom = api.cosmetics().joinMessage(player);
        if (custom != null) {
            for (Player online : player.getServer().getOnlinePlayers()) {
                var profile = api.profiles().get(online);
                if (profile == null || profile.settings().is(net.pvpserver.core.profile.Setting.JOIN_MESSAGES)) {
                    online.sendMessage(custom);
                }
            }
        }
        plugin.messages().send(player, "lobby.welcome", net.pvpserver.core.message.MessageService.p("player", player.getName()),
                net.pvpserver.core.message.MessageService.p("online", player.getServer().getOnlinePlayers().size()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(lobby.spawn());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inLobby(event.getPlayer()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inLobby(event.getPlayer()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (inLobby(event.getPlayer()) && !builder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player) && !builder(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!inLobby(player) || builder(player)) {
            return;
        }
        // Block interactions with the world (doors, chests, pressure plates, throwing potions in the editor...).
        if (event.getClickedBlock() != null || api.states().is(player, PlayerState.EDITING)) {
            if (api.hotbar().actionOf(event.getItem()) == null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getBlockY() == event.getFrom().getBlockY()) {
            return;
        }
        Player player = event.getPlayer();
        if (inLobby(player) && event.getTo().getY() < lobby.config().get().getInt("void-y", 0)
                && player.getWorld().equals(lobby.world())) {
            player.teleport(lobby.spawn());
            player.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!inLobby(player) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (player.hasPermission("pvp.lobby.fly") && plugin.flying(player)) {
            return;
        }
        if (!lobby.allowsDoubleJump(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        double forward = lobby.config().get().getDouble("double-jump.forward", 1.2);
        double up = lobby.config().get().getDouble("double-jump.up", 0.9);
        Vector velocity = player.getLocation().getDirection().setY(0).normalize().multiply(forward).setY(up);
        player.setVelocity(velocity);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.4f);
        long cooldown = lobby.config().get().getLong("double-jump.cooldown-ticks", 20);
        Tasks.later(() -> {
            if (player.isOnline() && inLobby(player)) {
                player.setAllowFlight(true);
            }
        }, cooldown);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState() && event.getWorld().equals(lobby.world())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStateChange(PlayerStateChangeEvent event) {
        // Players returning to the lobby side refresh visibility (e.g. leaving a queue keeps them in the lobby).
        if (event.to() == PlayerState.LOBBY || event.to() == PlayerState.QUEUE) {
            plugin.visibility().update(event.getPlayer());
        }
    }
}
