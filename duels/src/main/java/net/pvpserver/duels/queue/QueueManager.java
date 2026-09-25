package net.pvpserver.duels.queue;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.core.party.PartyUpdateEvent;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.MatchType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all queues: joining/leaving with validation, periodic matchmaking via {@link QueueMatcher} and hand-off to the
 * match manager.
 */
public final class QueueManager implements Listener {

    private final PvPDuels plugin;
    private final PracticeApi api;
    private final MessageService messages;
    private final Map<QueueKey, List<QueueEntry>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, QueueKey> keyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, QueueEntry> entryByPlayer = new ConcurrentHashMap<>();
    private EloRange range = new EloRange(50, 25, 5, 500);
    private int requiredUnrankedWins;
    private BukkitTask task;

    /**
     * @param plugin duels plugin
     */
    public QueueManager(PvPDuels plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
        this.messages = plugin.messages();
    }

    /**
     * @param section {@code queue} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        range = new EloRange(section.getInt("elo-range.base", 50), section.getInt("elo-range.step", 25),
                section.getInt("elo-range.interval-seconds", 5), section.getInt("elo-range.max", 500));
        requiredUnrankedWins = section.getInt("ranked-required-unranked-wins", 0);
        if (task != null) {
            task.cancel();
        }
        long period = Math.max(5, section.getInt("match-interval-ticks", 20));
        task = Tasks.timer(this::tick, period, period);
    }

    /**
     * Joins a queue (solo or as party leader).
     *
     * @param player player
     * @param kit kit
     * @param ranked ranked flag
     * @param mode team size
     */
    public void join(Player player, Kit kit, boolean ranked, QueueMode mode) {
        if (isQueued(player.getUniqueId())) {
            messages.send(player, "queue.already-queued");
            return;
        }
        if (!api.states().is(player, PlayerState.LOBBY)) {
            messages.send(player, "queue.busy");
            return;
        }
        if (ranked ? !kit.ranked() : !kit.unranked()) {
            messages.send(player, "queue.kit-unavailable");
            return;
        }
        Optional<Party> party = api.parties().partyOf(player);
        List<UUID> members = new ArrayList<>();
        if (party.isPresent()) {
            if (!party.get().isLeader(player.getUniqueId())) {
                messages.send(player, "queue.party-not-leader");
                return;
            }
            if (mode == QueueMode.ONE_V_ONE || party.get().size() > mode.teamSize()) {
                messages.send(player, "queue.party-too-large", MessageService.p("mode", mode.label()));
                return;
            }
            UUID busy = api.states().firstBusy(party.get().members());
            if (busy != null) {
                Player busyPlayer = Bukkit.getPlayer(busy);
                messages.send(player, "queue.party-member-busy", MessageService.p("player", busyPlayer == null ? "?" : busyPlayer.getName()));
                return;
            }
            members.addAll(party.get().members());
        } else {
            members.add(player.getUniqueId());
        }
        if (ranked && requiredUnrankedWins > 0) {
            for (UUID member : members) {
                PlayerProfile profile = api.profiles().get(member);
                int wins = profile == null ? 0 : profile.stats(kit.id()).unrankedWins();
                if (wins < requiredUnrankedWins) {
                    messages.send(player, "queue.ranked-locked", MessageService.p("wins", requiredUnrankedWins),
                            MessageService.p("current", wins));
                    return;
                }
            }
        }
        int elo = (int) members.stream().mapToInt(id -> api.stats().eloOf(id, kit.id())).average().orElse(api.stats().startingElo());
        QueueKey key = new QueueKey(kit.id(), ranked, mode);
        QueueEntry entry = new QueueEntry(player.getUniqueId(), members, elo, System.currentTimeMillis());
        queues.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        for (UUID member : members) {
            keyByPlayer.put(member, key);
            entryByPlayer.put(member, entry);
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                api.states().set(online, PlayerState.QUEUE);
                refreshHotbar(online);
                messages.send(online, ranked ? "queue.joined-ranked" : "queue.joined-unranked", MessageService.c("kit", kit.name()),
                        MessageService.p("mode", mode.label()), MessageService.p("elo", elo));
                api.sidebars().refresh(online);
            }
        }
    }

    /**
     * Leaves the queue (the whole party entry leaves when any member leaves).
     *
     * @param player player
     * @param notify whether to send a message
     * @return whether the player was queued
     */
    public boolean leave(Player player, boolean notify) {
        return leave(player.getUniqueId(), notify ? "queue.left" : null);
    }

    private boolean leave(UUID uuid, String messageKey) {
        QueueEntry entry = entryByPlayer.get(uuid);
        QueueKey key = keyByPlayer.get(uuid);
        if (entry == null || key == null) {
            return false;
        }
        List<QueueEntry> queue = queues.get(key);
        if (queue != null) {
            queue.remove(entry);
        }
        for (UUID member : entry.members()) {
            entryByPlayer.remove(member);
            keyByPlayer.remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null && api.states().is(online, PlayerState.QUEUE)) {
                api.states().set(online, PlayerState.LOBBY);
                refreshHotbar(online);
                if (messageKey != null) {
                    messages.send(online, messageKey);
                }
                api.sidebars().refresh(online);
            }
        }
        return true;
    }

    private void refreshHotbar(Player player) {
        api.bridges().get(LobbyBridge.class).ifPresent(lobby -> lobby.refreshHotbar(player));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<QueueKey, List<QueueEntry>> queue : queues.entrySet()) {
            List<QueueEntry> entries = queue.getValue();
            if (entries.size() < 2 && queue.getKey().mode() == QueueMode.ONE_V_ONE) {
                continue;
            }
            QueueKey key = queue.getKey();
            List<QueueMatcher.Pairing> pairings = QueueMatcher.match(entries, key.mode().teamSize(), key.ranked(), range, now);
            for (QueueMatcher.Pairing pairing : pairings) {
                start(key, pairing);
            }
        }
        for (Map.Entry<UUID, QueueEntry> entry : entryByPlayer.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            QueueKey key = keyByPlayer.get(entry.getKey());
            if (player == null || key == null) {
                continue;
            }
            long waited = entry.getValue().waited(now);
            if (key.ranked()) {
                int r = range.range(waited);
                messages.actionBar(player, "queue.actionbar-ranked", MessageService.p("time", net.pvpserver.core.util.TimeUtil.formatClock(waited)),
                        MessageService.p("min", Math.max(0, entry.getValue().elo() - r)), MessageService.p("max", entry.getValue().elo() + r));
            } else {
                messages.actionBar(player, "queue.actionbar-unranked", MessageService.p("time", net.pvpserver.core.util.TimeUtil.formatClock(waited)));
            }
        }
    }

    private void start(QueueKey key, QueueMatcher.Pairing pairing) {
        Optional<Kit> kit = api.kits().get(key.kit());
        List<QueueEntry> entries = pairing.entries();
        for (QueueEntry entry : entries) {
            queues.getOrDefault(key, new ArrayList<>()).remove(entry);
            for (UUID member : entry.members()) {
                entryByPlayer.remove(member);
                keyByPlayer.remove(member);
            }
        }
        if (kit.isEmpty()) {
            return;
        }
        List<UUID> teamA = new ArrayList<>();
        pairing.teamA().forEach(entry -> teamA.addAll(entry.members()));
        List<UUID> teamB = new ArrayList<>();
        pairing.teamB().forEach(entry -> teamB.addAll(entry.members()));
        for (UUID member : teamA) {
            notifyFound(member, teamB, kit.get(), key);
        }
        for (UUID member : teamB) {
            notifyFound(member, teamA, kit.get(), key);
        }
        plugin.matches().create(MatchType.QUEUE, kit.get(), key.ranked(), List.of(teamA, teamB), kit.get().rules().rounds(), null);
    }

    private void notifyFound(UUID member, List<UUID> opponents, Kit kit, QueueKey key) {
        Player player = Bukkit.getPlayer(member);
        if (player == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        int elo = 0;
        for (UUID opponent : opponents) {
            Player online = Bukkit.getPlayer(opponent);
            names.add(online == null ? "?" : online.getName());
            elo += api.stats().eloOf(opponent, kit.id());
        }
        elo = opponents.isEmpty() ? 0 : elo / opponents.size();
        messages.send(player, key.ranked() ? "queue.found-ranked" : "queue.found-unranked", MessageService.c("kit", kit.name()),
                MessageService.p("opponent", String.join(", ", names)), MessageService.p("elo", elo));
    }

    /**
     * @param uuid player
     * @return whether queued
     */
    public boolean isQueued(UUID uuid) {
        return entryByPlayer.containsKey(uuid);
    }

    /**
     * @param uuid player
     * @return queue key or null
     */
    public QueueKey keyOf(UUID uuid) {
        return keyByPlayer.get(uuid);
    }

    /**
     * @param uuid player
     * @return entry or null
     */
    public QueueEntry entryOf(UUID uuid) {
        return entryByPlayer.get(uuid);
    }

    /** @return current ELO range policy */
    public EloRange range() {
        return range;
    }

    /** @return total queued players */
    public int queuedPlayers() {
        return entryByPlayer.size();
    }

    /**
     * @param kit kit id
     * @param ranked ranked flag
     * @return queued players for the kit
     */
    public int queuedPlayers(String kit, boolean ranked) {
        int count = 0;
        for (Map.Entry<QueueKey, List<QueueEntry>> queue : queues.entrySet()) {
            if (queue.getKey().kit().equals(kit) && queue.getKey().ranked() == ranked) {
                for (QueueEntry entry : queue.getValue()) {
                    count += entry.size();
                }
            }
        }
        return count;
    }

    /** Removes everybody from every queue (shutdown). */
    public void clear() {
        for (UUID uuid : List.copyOf(entryByPlayer.keySet())) {
            leave(uuid, null);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer().getUniqueId(), "queue.left-member-quit");
    }

    @EventHandler
    public void onPartyUpdate(PartyUpdateEvent event) {
        // Membership changes invalidate a queued party entry.
        UUID affected = event.player();
        QueueEntry entry = entryByPlayer.get(affected);
        if (entry == null) {
            for (UUID member : event.party().members()) {
                entry = entryByPlayer.get(member);
                if (entry != null) {
                    affected = member;
                    break;
                }
            }
        }
        if (entry != null && (entry.size() > 1 || event.type() == PartyUpdateEvent.Type.JOIN)) {
            leave(affected, "queue.left-party-changed");
        }
    }
}
