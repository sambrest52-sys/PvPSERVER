package net.pvpserver.core.profile;

import net.pvpserver.core.stats.KitStats;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything persisted about a player plus a little session state. Loaded before join and cached while online.
 * Mutate only on the main thread; persistence works on snapshots.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private volatile String name;
    private volatile String rankId;
    private final long firstJoin;
    private volatile long lastJoin;
    private volatile long playtimeMillis;
    private final PlayerSettings settings;
    private final CosmeticSelection cosmetics;
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    private final Map<String, KitStats> stats = new ConcurrentHashMap<>();
    private final Map<String, String> kitLayouts = new ConcurrentHashMap<>();
    private final int startingElo;

    private volatile boolean dirty;
    private volatile UUID lastMessaged;
    private volatile long sessionStart = System.currentTimeMillis();

    /**
     * @param uuid player id
     * @param name name
     * @param rankId built-in rank id
     * @param firstJoin first join epoch millis
     * @param lastJoin last join epoch millis
     * @param playtimeMillis accumulated play time
     * @param settings settings
     * @param cosmetics cosmetic selection
     * @param startingElo ELO for kits the player has not played yet
     */
    public PlayerProfile(UUID uuid, String name, String rankId, long firstJoin, long lastJoin, long playtimeMillis,
                         PlayerSettings settings, CosmeticSelection cosmetics, int startingElo) {
        this.uuid = uuid;
        this.name = name;
        this.rankId = rankId;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.playtimeMillis = playtimeMillis;
        this.settings = settings;
        this.cosmetics = cosmetics;
        this.startingElo = startingElo;
    }

    /** @return player id */
    public UUID uuid() {
        return uuid;
    }

    /** @return last known name */
    public String name() {
        return name;
    }

    /** @param name new name */
    public void name(String name) {
        this.name = name;
        markDirty();
    }

    /** @return built-in rank id */
    public String rankId() {
        return rankId;
    }

    /** @param rankId new rank id */
    public void rankId(String rankId) {
        this.rankId = rankId;
        markDirty();
    }

    /** @return first join time */
    public long firstJoin() {
        return firstJoin;
    }

    /** @return last join time */
    public long lastJoin() {
        return lastJoin;
    }

    /** @param lastJoin last join time */
    public void lastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
        markDirty();
    }

    /** @return play time including the current session */
    public long playtimeMillis() {
        return playtimeMillis + (System.currentTimeMillis() - sessionStart);
    }

    /** Folds the current session into the stored play time (on quit/save). */
    public void flushPlaytime() {
        long now = System.currentTimeMillis();
        playtimeMillis += now - sessionStart;
        sessionStart = now;
    }

    /** @return settings (mutable) */
    public PlayerSettings settings() {
        return settings;
    }

    /** @return cosmetics (mutable) */
    public CosmeticSelection cosmetics() {
        return cosmetics;
    }

    /** @return ignored player ids (mutable, thread safe) */
    public Set<UUID> ignored() {
        return ignored;
    }

    /**
     * @param kit kit id
     * @return stats for the kit, created lazily with the starting ELO
     */
    public KitStats stats(String kit) {
        return stats.computeIfAbsent(kit, k -> new KitStats(k, startingElo));
    }

    /** @return all loaded stats keyed by kit */
    public Map<String, KitStats> allStats() {
        return stats;
    }

    /** @return saved kit layouts keyed by kit (serialised form) */
    public Map<String, String> kitLayouts() {
        return kitLayouts;
    }

    /**
     * Average duel ELO across kits the player has played, or the starting ELO.
     *
     * @return global ELO
     */
    public int globalElo() {
        return (int) stats.values().stream().filter(s -> s.wins() + s.losses() > 0).mapToInt(KitStats::elo)
                .average().orElse(startingElo);
    }

    /** @return last private message partner */
    public UUID lastMessaged() {
        return lastMessaged;
    }

    /** @param lastMessaged last private message partner */
    public void lastMessaged(UUID lastMessaged) {
        this.lastMessaged = lastMessaged;
    }

    /** Marks the profile as needing a save. */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * @return whether a save is pending, clearing the flag
     */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }
}
