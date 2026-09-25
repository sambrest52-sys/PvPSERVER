package net.pvpserver.duels;

import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.api.bridge.SpectateBridge;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.match.MatchParticipant;
import net.pvpserver.duels.match.MatchState;
import net.pvpserver.duels.menu.DuelOptionsMenu;
import net.pvpserver.duels.menu.KitPickMenu;
import net.pvpserver.duels.menu.PartyFightMenu;
import net.pvpserver.duels.menu.QueueMenu;
import net.pvpserver.duels.menu.SpectateMenu;
import net.pvpserver.duels.queue.QueueEntry;
import net.pvpserver.duels.queue.QueueKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exposes duels features to the lobby and other plugins through core bridges.
 */
public final class DuelsBridge implements QueueBridge, MatchBridge, SpectateBridge {

    private final PvPDuels plugin;

    /**
     * @param plugin duels plugin
     */
    public DuelsBridge(PvPDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openQueueMenu(Player player, boolean ranked) {
        if (!plugin.api().states().is(player, PlayerState.LOBBY)) {
            plugin.messages().send(player, "queue.busy");
            return;
        }
        var party = plugin.api().parties().partyOf(player);
        if (party.isPresent() && !party.get().isLeader(player.getUniqueId())) {
            plugin.messages().send(player, "queue.party-not-leader");
            return;
        }
        new QueueMenu(plugin, player, ranked).open();
    }

    @Override
    public void leaveQueue(Player player) {
        plugin.queues().leave(player, true);
    }

    @Override
    public boolean isQueued(UUID uuid) {
        return plugin.queues().isQueued(uuid);
    }

    @Override
    public int queuedPlayers() {
        return plugin.queues().queuedPlayers();
    }

    @Override
    public int queuedPlayers(String kit, boolean ranked) {
        return plugin.queues().queuedPlayers(kit, ranked);
    }

    @Override
    public QueueInfo info(UUID uuid) {
        QueueKey key = plugin.queues().keyOf(uuid);
        QueueEntry entry = plugin.queues().entryOf(uuid);
        if (key == null || entry == null) {
            return null;
        }
        long waited = entry.waited(System.currentTimeMillis());
        int range = plugin.queues().range().range(waited);
        String kitName = plugin.api().kits().get(key.kit()).map(Kit::displayName).orElse(key.kit());
        return new QueueInfo(kitName, key.ranked(), key.mode().label(), waited, Math.max(0, entry.elo() - range), entry.elo() + range);
    }

    @Override
    public int playersInMatches() {
        return plugin.matches().playerCount();
    }

    @Override
    public List<MatchInfo> activeMatches() {
        List<MatchInfo> list = new ArrayList<>();
        for (Match match : plugin.matches().matches()) {
            list.add(info(match));
        }
        return list;
    }

    private MatchInfo info(Match match) {
        return new MatchInfo(match.id(), match.kit().plainName(), match.ranked(), match.description(),
                match.participants().stream().map(MatchParticipant::uuid).toList(), match.spectators().size(), match.durationMillis());
    }

    @Override
    public MatchInfo matchOf(UUID uuid) {
        Match match = plugin.matches().matchOf(uuid);
        return match == null ? null : info(match);
    }

    @Override
    public void openDuelMenu(Player sender, Player target) {
        List<Kit> kits = plugin.api().kits().queueKits(false);
        new KitPickMenu(plugin, sender, plugin.messages().parse(plugin.menus().getString("titles.duel-kit", "Duel <player>"),
                MessageService.p("player", target.getName())), kits,
                (player, kit) -> new DuelOptionsMenu(plugin, player, target.getUniqueId(), kit).open()).open();
    }

    @Override
    public void openPartyFightMenu(Player leader) {
        new PartyFightMenu(plugin, leader).open();
    }

    @Override
    public boolean spectate(Player spectator, Player target) {
        return plugin.spectators().spectate(spectator, target);
    }

    @Override
    public void openSpectateMenu(Player player) {
        if (plugin.matches().matches().stream().noneMatch(m -> m.state() != MatchState.STARTING && m.state() != MatchState.ENDED)) {
            plugin.messages().send(player, "spectate.no-matches");
            return;
        }
        new SpectateMenu(plugin, player).open();
    }
}
