package net.pvpserver.core.stats;

import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.storage.repository.MatchHistoryRepository;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies match and FFA results to cached profiles (ELO, wins, streaks, kills) and writes match history.
 */
public final class StatsService {

    private final ProfileService profiles;
    private final MatchHistoryRepository history;
    private EloCalculator elo = new EloCalculator(32, 0, 1);
    private EloCalculator ffaElo = new EloCalculator(16, 0, 1);
    private int startingElo = 1000;

    /**
     * @param profiles profiles
     * @param history match history storage
     */
    public StatsService(ProfileService profiles, MatchHistoryRepository history) {
        this.profiles = profiles;
        this.history = history;
    }

    /**
     * @param section {@code elo} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        startingElo = section.getInt("starting", 1000);
        elo = new EloCalculator(section.getInt("k-factor", 32), section.getInt("floor", 0), section.getInt("min-change", 1));
        ffaElo = new EloCalculator(section.getInt("ffa-k-factor", 16), section.getInt("floor", 0), section.getInt("min-change", 1));
    }

    /** @return duel ELO calculator */
    public EloCalculator elo() {
        return elo;
    }

    /** @return starting ELO */
    public int startingElo() {
        return startingElo;
    }

    /**
     * @param uuid player
     * @param kit kit id
     * @return current duel ELO (starting ELO when offline/unknown)
     */
    public int eloOf(UUID uuid, String kit) {
        PlayerProfile profile = profiles.get(uuid);
        return profile == null ? startingElo : profile.stats(kit).elo();
    }

    /**
     * Records a finished duel. ELO is only exchanged for ranked matches.
     *
     * @param kit kit
     * @param ranked ranked flag
     * @param winners winning players
     * @param losers losing players
     * @return per-player ELO changes (empty for unranked)
     */
    public Map<UUID, Integer> recordDuel(Kit kit, boolean ranked, List<UUID> winners, List<UUID> losers) {
        Map<UUID, Integer> changes = new HashMap<>();
        if (ranked && !winners.isEmpty() && !losers.isEmpty()) {
            List<Integer> winnerElo = new ArrayList<>();
            List<Integer> loserElo = new ArrayList<>();
            winners.forEach(id -> winnerElo.add(eloOf(id, kit.id())));
            losers.forEach(id -> loserElo.add(eloOf(id, kit.id())));
            int change = elo.teamChange(winnerElo, loserElo);
            winners.forEach(id -> changes.put(id, change));
            losers.forEach(id -> changes.put(id, -change));
        }
        for (UUID id : winners) {
            apply(id, kit, ranked, true, changes.getOrDefault(id, 0));
        }
        for (UUID id : losers) {
            apply(id, kit, ranked, false, changes.getOrDefault(id, 0));
        }
        return changes;
    }

    private void apply(UUID id, Kit kit, boolean ranked, boolean won, int change) {
        PlayerProfile profile = profiles.get(id);
        if (profile == null) {
            return;
        }
        KitStats stats = profile.stats(kit.id());
        stats.recordDuel(ranked, won);
        if (change != 0) {
            stats.elo(elo.apply(stats.elo(), change));
        }
        profile.markDirty();
    }

    /**
     * Records an FFA kill, adjusting FFA ratings when ranked.
     *
     * @param killer killer (may be null for environmental deaths)
     * @param victim victim
     * @param kit kit
     * @param ranked whether the arena is ranked
     * @param killerStreak killer's streak after the kill
     * @return rating change for the killer (0 when unranked)
     */
    public int recordFfaKill(UUID killer, UUID victim, Kit kit, boolean ranked, int killerStreak) {
        PlayerProfile victimProfile = profiles.get(victim);
        PlayerProfile killerProfile = killer == null ? null : profiles.get(killer);
        int change = 0;
        if (ranked && victimProfile != null && killerProfile != null) {
            change = ffaElo.change(killerProfile.stats(kit.id()).ffaElo(), victimProfile.stats(kit.id()).ffaElo());
            killerProfile.stats(kit.id()).ffaElo(killerProfile.stats(kit.id()).ffaElo() + change);
            victimProfile.stats(kit.id()).ffaElo(victimProfile.stats(kit.id()).ffaElo() - change);
        }
        if (victimProfile != null) {
            victimProfile.stats(kit.id()).recordFfaDeath();
            victimProfile.markDirty();
        }
        if (killerProfile != null) {
            killerProfile.stats(kit.id()).recordFfaKill(killerStreak);
            killerProfile.markDirty();
        }
        return change;
    }

    /**
     * @param record finished match
     */
    public void saveHistory(MatchRecord record) {
        history.insert(record);
    }
}
