package net.pvpserver.lobby;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.event.PlayerStateChangeEvent;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.lobby.config.LobbySettings;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.util.Vector;

/**
 * Lobby protection (no damage, hunger, block edits, drops, pickups, mob spawns or entity tampering), void rescue,
 * double jump and join handling.
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

    private boolean lobbyWorld(Entity entity) {
        return entity.getWorld().equals(plugin.lobbyWorld().world());
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
                if (profile == null || profile.settings().is(Setting.JOIN_MESSAGES)) {
                    online.sendMessage(custom);
                }
            }
        }
        plugin.messages().send(player, "lobby.welcome", MessageService.p("player", player.getName()),
                MessageService.p("online", player.getServer().getOnlinePlayers().size()));
        plugin.features().welcome(player);
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
                plugin.features().triggers().rescue(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        // Item frames, armour stands and paintings of an imported lobby.
        if (!(event.getEntity() instanceof Player) && event.getDamager() instanceof Player player && inLobby(player) && !builder(player)
                && lobbyWorld(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (lobbyWorld(event.getEntity()) && !(event.getRemover() instanceof Player player && builder(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (inLobby(player) && !builder(player) && lobbyWorld(event.getRightClicked()) && !(event.getRightClicked() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!builder(event.getPlayer()) && lobbyWorld(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (lobbyWorld(event.getEntity()) && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (lobbyWorld(event.getPlayer())) {
            event.setCancelled(true);
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
        // Players editing kits are outside the trigger system but still need rescuing.
        if (event.getTo().getBlockY() == event.getFrom().getBlockY()) {
            return;
        }
        Player player = event.getPlayer();
        if (api.states().is(player, PlayerState.EDITING) && event.getTo().getY() < plugin.lobbyWorld().voidY()
                && player.getWorld().equals(lobby.world())) {
            plugin.features().triggers().rescue(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!inLobby(player) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (plugin.features().parkour().running(player)) {
            event.setCancelled(true);
            player.setAllowFlight(false);
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
        LobbySettings.DoubleJump settings = plugin.settings().doubleJump();
        Vector velocity = player.getLocation().getDirection().setY(0).normalize().multiply(settings.forward()).setY(settings.up());
        player.setVelocity(velocity);
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.6f, 1.4f);
        Tasks.later(() -> {
            if (player.isOnline() && inLobby(player) && !plugin.features().parkour().running(player)) {
                player.setAllowFlight(true);
            }
        }, settings.cooldownTicks());
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
        } else if (event.to() != PlayerState.EDITING) {
            plugin.features().parkour().cancel(event.getPlayer(), false);
        }
    }
}
