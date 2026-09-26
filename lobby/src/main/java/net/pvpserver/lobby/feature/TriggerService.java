package net.pvpserver.lobby.feature;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything that reacts to where players walk or what they click: portals, launch pads, parkour plates, zones, the
 * void, buttons and eggs. Positions are looked up in hash maps keyed by packed block position, and the move handler
 * only does work when a player crosses into another block, so 200 players walking around cost a handful of map
 * lookups per block step.
 */
public final class TriggerService implements Listener {

    private static final int START = -1;
    private static final int FINISH = Integer.MAX_VALUE;

    private final PvPLobby plugin;
    private final Map<Long, LobbyLayout.LaunchPad> pads = new HashMap<>();
    private final Map<Long, String> buttons = new HashMap<>();
    private final Map<Long, LobbyLayout.Egg> eggs = new HashMap<>();
    private final Map<Long, Integer> plates = new HashMap<>();
    private List<LobbyLayout.Portal> portals = List.of();
    private List<LobbyLayout.Zone> zones = List.of();
    private int parkourFallY = Integer.MIN_VALUE;
    private final Map<UUID, Long> padCooldown = new HashMap<>();
    private final Map<UUID, Long> portalCooldown = new HashMap<>();
    private final Map<UUID, String> zoneOf = new HashMap<>();

    /**
     * @param plugin lobby plugin
     */
    public TriggerService(PvPLobby plugin) {
        this.plugin = plugin;
    }

    /** Rebuilds the lookup tables from the layout. */
    public void rebuild() {
        LobbyLayout layout = plugin.lobbyWorld().layout();
        pads.clear();
        buttons.clear();
        eggs.clear();
        plates.clear();
        layout.pads().forEach(pad -> pads.put(pad.at().key(), pad));
        layout.buttons().forEach(button -> buttons.put(button.at().key(), button.action()));
        layout.eggs().forEach(egg -> eggs.put(egg.at().key(), egg));
        portals = List.copyOf(layout.portals());
        // Later zones win where they overlap, so small special areas can sit inside big ones.
        zones = List.copyOf(layout.zones());
        LobbyLayout.Parkour parkour = layout.parkour();
        parkourFallY = Integer.MIN_VALUE;
        if (parkour != null) {
            plates.put(parkour.start().key(), START);
            for (int i = 0; i < parkour.checkpoints().size(); i++) {
                plates.put(parkour.checkpoints().get(i).key(), i);
            }
            plates.put(parkour.finish().key(), FINISH);
            parkourFallY = parkour.fallY();
        }
        zoneOf.clear();
    }

    private boolean active(Player player) {
        return plugin.api().states().is(player, PlayerState.LOBBY, PlayerState.QUEUE)
                && player.getWorld().equals(plugin.lobbyWorld().world()) && player.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (!active(player)) {
            return;
        }
        ParkourService parkour = plugin.features().parkour();
        if (parkour.running(player) && to.getY() < parkourFallY) {
            event.setTo(parkour.fall(player));
            return;
        }
        if (to.getY() < plugin.lobbyWorld().voidY()) {
            event.setTo(rescueTarget(player));
            return;
        }
        int x = to.getBlockX();
        int y = to.getBlockY();
        int z = to.getBlockZ();
        long feet = BlockPos.key(x, y, z);
        Integer plate = plates.get(feet);
        if (plate != null) {
            if (plate == START) {
                parkour.start(player);
            } else if (plate == FINISH) {
                parkour.finish(player);
            } else {
                parkour.checkpoint(player, plate);
            }
        }
        LobbyLayout.LaunchPad pad = pads.get(feet);
        if (pad == null) {
            pad = pads.get(BlockPos.key(x, y - 1, z));
        }
        if (pad != null) {
            launch(player, pad);
        }
        for (LobbyLayout.Portal portal : portals) {
            if (portal.box().contains(x, y, z) || portal.box().contains(x, y + 1, z)) {
                enterPortal(player, portal, from, to);
                break;
            }
        }
        zone(player, to);
    }

    /** Sends a player who fell out of the lobby back to spawn. */
    public void rescue(Player player) {
        player.teleport(rescueTarget(player));
    }

    /** Resets a falling player and returns where they go (spawn); used as the move event's destination. */
    private Location rescueTarget(Player player) {
        plugin.features().parkour().cancel(player, false);
        player.setFallDistance(0);
        player.setVelocity(new Vector());
        String sound = plugin.settings().voidSound();
        if (!sound.isEmpty()) {
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 0.7f, 1f));
        }
        return plugin.lobbyWorld().spawn();
    }

    private void launch(Player player, LobbyLayout.LaunchPad pad) {
        LobbySettings.Pads settings = plugin.settings().pads();
        if (!settings.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = padCooldown.get(player.getUniqueId());
        if (last != null && now - last < settings.cooldownTicks() * 50) {
            return;
        }
        padCooldown.put(player.getUniqueId(), now);
        player.setVelocity(new Vector(pad.vx(), pad.vy(), pad.vz()));
        player.setFallDistance(0);
        player.playSound(Sound.sound(Key.key(settings.sound()), Sound.Source.MASTER, 0.8f, 1.2f));
        player.getWorld().spawnParticle(settings.particle(), player.getLocation(), 12, 0.3, 0.1, 0.3, 0.05);
    }

    private void enterPortal(Player player, LobbyLayout.Portal portal, Location from, Location to) {
        LobbySettings.Portals settings = plugin.settings().portals();
        if (!settings.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = portalCooldown.get(player.getUniqueId());
        if (last != null && now - last < settings.cooldownTicks() * 50) {
            return;
        }
        portalCooldown.put(player.getUniqueId(), now);
        Vector back = from.toVector().subtract(to.toVector()).setY(0);
        if (back.lengthSquared() < 1e-6) {
            back = player.getLocation().getDirection().multiply(-1).setY(0);
        }
        if (back.lengthSquared() > 1e-6 && settings.pushBack() > 0) {
            player.setVelocity(back.normalize().multiply(settings.pushBack()).setY(0.25));
        }
        player.playSound(Sound.sound(Key.key(settings.sound()), Sound.Source.MASTER, 0.4f, 1.6f));
        plugin.features().actions().run(player, portal.action());
    }

    private void zone(Player player, Location at) {
        LobbySettings.Zones settings = plugin.settings().zones();
        if (!settings.enabled() || zones.isEmpty()) {
            return;
        }
        String inside = null;
        for (int i = zones.size() - 1; i >= 0; i--) {
            LobbyLayout.Zone zone = zones.get(i);
            if (zone.contains(at.getX(), at.getY(), at.getZ())) {
                inside = zone.id();
                break;
            }
        }
        String previous = zoneOf.get(player.getUniqueId());
        if (inside == null) {
            zoneOf.remove(player.getUniqueId());
            return;
        }
        if (inside.equals(previous)) {
            return;
        }
        zoneOf.put(player.getUniqueId(), inside);
        String name = settings.names().get(inside);
        if (name != null) {
            player.sendActionBar(plugin.messages().parse(name));
            player.playSound(Sound.sound(Key.key(settings.sound()), Sound.Source.MASTER, 0.5f, 1.5f));
        }
    }

    /**
     * @param player player
     * @return id of the zone the player is in, or null
     */
    public String zoneOf(Player player) {
        return zoneOf.get(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        if (!active(player) || (player.getGameMode() == GameMode.CREATIVE && player.isSneaking())) {
            return;
        }
        long key = BlockPos.key(block.getX(), block.getY(), block.getZ());
        LobbyLayout.Egg egg = eggs.get(key);
        String action = buttons.get(key);
        if (egg == null && action == null) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (egg != null) {
            plugin.features().eggs().click(player, egg);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.features().actions().run(player, action);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) {
            ParkourService parkour = plugin.features().parkour();
            if (parkour.running(event.getPlayer()) && !isParkourTeleport(event.getTo())) {
                parkour.cancel(event.getPlayer(), true);
            }
        }
    }

    /** Teleports onto a start or checkpoint plate belong to the parkour itself. */
    private boolean isParkourTeleport(Location to) {
        return plates.containsKey(BlockPos.key(to.getBlockX(), to.getBlockY(), to.getBlockZ()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        padCooldown.remove(uuid);
        portalCooldown.remove(uuid);
        zoneOf.remove(uuid);
    }
}
