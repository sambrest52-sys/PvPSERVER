package net.pvpserver.duels.match;

import net.pvpserver.core.arena.ArenaInstance;
import net.pvpserver.core.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A running match. State transitions are driven by {@link MatchManager}; this class holds data and small queries.
 */
public final class Match {

    private final UUID id = UUID.randomUUID();
    private final MatchType type;
    private final Kit kit;
    private final boolean ranked;
    private final String preferredArena;
    private final int roundsToWin;
    private final List<MatchTeam> teams = new ArrayList<>();
    private final Map<UUID, MatchParticipant> participants = new LinkedHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final long createdAt = System.currentTimeMillis();
    private ArenaInstance arena;
    private MatchState state = MatchState.STARTING;
    private int round = 1;
    private long fightStartedAt;
    private BukkitTask task;
    private MatchTeam winner;
    private boolean cancelled;

    /**
     * @param type match type
     * @param kit kit
     * @param ranked ranked flag
     * @param teams player ids per team
     * @param roundsToWin rounds needed to win (1 = single round)
     * @param preferredArena preferred arena name or null
     */
    public Match(MatchType type, Kit kit, boolean ranked, List<List<UUID>> teams, int roundsToWin, String preferredArena) {
        this.type = type;
        this.kit = kit;
        this.ranked = ranked;
        this.roundsToWin = Math.max(1, roundsToWin);
        this.preferredArena = preferredArena;
        for (int i = 0; i < teams.size(); i++) {
            MatchTeam team = new MatchTeam(i);
            for (UUID uuid : teams.get(i)) {
                Player player = Bukkit.getPlayer(uuid);
                MatchParticipant participant = new MatchParticipant(uuid, player == null ? "?" : player.getName(), team);
                team.participants().add(participant);
                participants.put(uuid, participant);
            }
            this.teams.add(team);
        }
    }

    /**
     * @param uuid player id
     * @return participant or null
     */
    public MatchParticipant participant(UUID uuid) {
        return participants.get(uuid);
    }

    /** @return participants in team order */
    public Collection<MatchParticipant> participants() {
        return participants.values();
    }

    /** @return online participant players */
    public List<Player> onlinePlayers() {
        List<Player> players = new ArrayList<>();
        for (MatchParticipant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null && !participant.disconnected()) {
                players.add(player);
            }
        }
        return players;
    }

    /** @return online spectators */
    public List<Player> onlineSpectators() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    /** @return everyone to notify (participants and spectators) */
    public List<Player> audience() {
        List<Player> all = onlinePlayers();
        all.addAll(onlineSpectators());
        return all;
    }

    /**
     * @param uuid player
     * @return opposing participants
     */
    public List<MatchParticipant> opponentsOf(UUID uuid) {
        MatchParticipant self = participants.get(uuid);
        List<MatchParticipant> list = new ArrayList<>();
        for (MatchParticipant participant : participants.values()) {
            if (self == null || participant.team() != self.team()) {
                list.add(participant);
            }
        }
        return list;
    }

    /**
     * @param a player
     * @param b player
     * @return whether both are on the same team
     */
    public boolean teammates(UUID a, UUID b) {
        MatchParticipant pa = participants.get(a);
        MatchParticipant pb = participants.get(b);
        return pa != null && pb != null && pa.team() == pb.team();
    }

    /** @return teams that still have alive players this round */
    public List<MatchTeam> aliveTeams() {
        return teams.stream().filter(MatchTeam::anyAlive).toList();
    }

    /** @return teams that still have connected players */
    public List<MatchTeam> connectedTeams() {
        return teams.stream().filter(team -> !team.allDisconnected()).toList();
    }

    /** @return elapsed fight time */
    public long durationMillis() {
        return fightStartedAt == 0 ? 0 : System.currentTimeMillis() - fightStartedAt;
    }

    /** @return short "A vs B" description */
    public String description() {
        if (type == MatchType.PARTY_FFA) {
            return "Party FFA (" + participants.size() + " players)";
        }
        return String.join(" vs ", teams.stream().map(MatchTeam::names).toList());
    }

    /** @return match id */
    public UUID id() {
        return id;
    }

    /** @return type */
    public MatchType type() {
        return type;
    }

    /** @return kit */
    public Kit kit() {
        return kit;
    }

    /** @return ranked flag */
    public boolean ranked() {
        return ranked;
    }

    /** @return preferred arena or null */
    public String preferredArena() {
        return preferredArena;
    }

    /** @return rounds needed to win */
    public int roundsToWin() {
        return roundsToWin;
    }

    /** @return teams */
    public List<MatchTeam> teams() {
        return teams;
    }

    /** @return spectator ids (mutable) */
    public Set<UUID> spectators() {
        return spectators;
    }

    /** @return creation time */
    public long createdAt() {
        return createdAt;
    }

    /** @return arena instance (null while starting) */
    public ArenaInstance arena() {
        return arena;
    }

    /** @param arena instance */
    void arena(ArenaInstance arena) {
        this.arena = arena;
    }

    /** @return state */
    public MatchState state() {
        return state;
    }

    /** @param state new state */
    void state(MatchState state) {
        this.state = state;
    }

    /** @return current round (1-based) */
    public int round() {
        return round;
    }

    /** Advances the round counter. */
    void nextRound() {
        round++;
    }

    /** Marks the fight start. */
    void markFightStart() {
        if (fightStartedAt == 0) {
            fightStartedAt = System.currentTimeMillis();
        }
    }

    /** @return running countdown/timer task */
    BukkitTask task() {
        return task;
    }

    /** @param task task to track (previous one is cancelled) */
    void task(BukkitTask task) {
        if (this.task != null) {
            this.task.cancel();
        }
        this.task = task;
    }

    /** @return winning team (null = draw/undecided) */
    public MatchTeam winner() {
        return winner;
    }

    /** @param winner winning team */
    void winner(MatchTeam winner) {
        this.winner = winner;
    }

    /** @return whether the match was cancelled (no stats) */
    public boolean cancelled() {
        return cancelled;
    }

    /** Marks the match as cancelled. */
    void cancel() {
        this.cancelled = true;
    }
}
