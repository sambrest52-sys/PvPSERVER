package net.pvpserver.duels.spectate;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.gui.ItemTemplates;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.match.MatchParticipant;
import net.pvpserver.duels.match.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spectator mode: invisible to participants, flying, invulnerable, with a teleport compass and a leave item.
 */
public final class SpectatorManager implements Listener {

    private final PvPDuels plugin;
    private final PracticeApi api;
    private final MessageService messages;
    private final Map<UUID, Match> spectating = new ConcurrentHashMap<>();

    /**
     * @param plugin duels plugin
     */
    public SpectatorManager(PvPDuels plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
        this.messages = plugin.messages();
        api.hotbar().register("spectate-leave", player -> stop(player, true));
        api.hotbar().register("spectate-players", player -> new SpectateTeleportMenu(plugin, player).open());
    }

    /**
     * Starts spectating the match of {@code target}.
     *
     * @param spectator spectator
     * @param target participant to watch
     * @return whether spectating started
     */
    public boolean spectate(Player spectator, Player target) {
        Match match = plugin.matches().matchOf(target.getUniqueId());
        if (match == null || match.state() == MatchState.ENDED || match.arena() == null) {
            messages.send(spectator, "spectate.not-in-match", MessageService.p("player", target.getName()));
            return false;
        }
        if (plugin.matches().matchOf(spectator.getUniqueId()) != null) {
            messages.send(spectator, "spectate.busy");
            return false;
        }
        boolean staff = spectator.hasPermission("pvp.staff");
        if (!staff) {
            for (MatchParticipant participant : match.participants()) {
                PlayerProfile profile = api.profiles().get(participant.uuid());
                if (profile != null && !profile.settings().is(Setting.ALLOW_SPECTATORS)) {
                    messages.send(spectator, "spectate.disabled", MessageService.p("player", participant.name()));
                    return false;
                }
            }
        }
        PlayerState state = api.states().get(spectator);
        if (state == PlayerState.QUEUE) {
            api.bridges().get(QueueBridge.class).ifPresent(q -> q.leaveQueue(spectator));
        } else if (state != PlayerState.LOBBY && state != PlayerState.SPECTATING && !staff) {
            messages.send(spectator, "spectate.busy");
            return false;
        }
        Match previous = spectating.remove(spectator.getUniqueId());
        if (previous != null) {
            previous.spectators().remove(spectator.getUniqueId());
            showTo(previous, spectator);
        }
        spectating.put(spectator.getUniqueId(), match);
        match.spectators().add(spectator.getUniqueId());
        api.combat().reset(spectator);
        api.states().set(spectator, PlayerState.SPECTATING);
        spectator.getInventory().clear();
        spectator.setGameMode(GameMode.ADVENTURE);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
        spectator.setCollidable(false);
        spectator.getActivePotionEffects().forEach(effect -> spectator.removePotionEffect(effect.getType()));
        giveItems(spectator);
        for (Player participant : match.onlinePlayers()) {
            participant.hidePlayer(plugin, spectator);
        }
        spectator.teleport(match.state() == MatchState.STARTING ? target.getLocation() : match.arena().spectatorSpawn());
        messages.send(spectator, "spectate.started", MessageService.p("match", match.description()), MessageService.c("kit", match.kit().name()));
        if (!api.staff().isVanished(spectator)) {
            for (Player participant : match.onlinePlayers()) {
                messages.send(participant, "spectate.joined-notify", MessageService.p("player", spectator.getName()));
            }
        }
        api.sidebars().refresh(spectator);
        return true;
    }

    private void giveItems(Player spectator) {
        var config = plugin.menus();
        spectator.getInventory().setItem(0, api.hotbar().tag(
                ItemTemplates.build(config, "items.spectator-players", Material.COMPASS, messages), "spectate-players"));
        spectator.getInventory().setItem(8, api.hotbar().tag(
                ItemTemplates.build(config, "items.spectator-leave", Material.RED_DYE, messages), "spectate-leave"));
    }

    /**
     * Stops spectating and returns to the lobby.
     *
     * @param spectator spectator
     * @param notify whether to send a message
     */
    public void stop(Player spectator, boolean notify) {
        Match match = spectating.remove(spectator.getUniqueId());
        if (match == null) {
            if (api.states().is(spectator, PlayerState.SPECTATING)) {
                toLobby(spectator);
            }
            return;
        }
        match.spectators().remove(spectator.getUniqueId());
        showTo(match, spectator);
        spectator.setCollidable(true);
        if (notify) {
            messages.send(spectator, "spectate.stopped");
        }
        toLobby(spectator);
    }

    private void toLobby(Player player) {
        api.bridges().get(LobbyBridge.class).ifPresentOrElse(lobby -> lobby.sendToLobby(player),
                () -> api.states().set(player, PlayerState.LOBBY));
    }

    private void showTo(Match match, Player spectator) {
        for (MatchParticipant participant : match.participants()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                player.showPlayer(plugin, spectator);
            }
        }
    }

    /**
     * @param player player
     * @return match being spectated or null
     */
    public Match spectatedMatch(Player player) {
        return spectating.get(player.getUniqueId());
    }

    private boolean isSpectator(org.bukkit.entity.Entity entity) {
        return entity instanceof Player player && spectating.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Match match = spectating.remove(event.getPlayer().getUniqueId());
        if (match != null) {
            match.spectators().remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isSpectator(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectile(ProjectileCollideEvent event) {
        if (isSpectator(event.getCollidedWith())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (isSpectator(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isSpectator(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Match match = spectating.get(event.getPlayer().getUniqueId());
        if (match == null || match.arena() == null || event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ() && event.getTo().getBlockY() == event.getFrom().getBlockY()) {
            return;
        }
        if (!match.arena().contains(event.getTo())) {
            event.getPlayer().teleport(match.arena().spectatorSpawn());
        }
    }
}
