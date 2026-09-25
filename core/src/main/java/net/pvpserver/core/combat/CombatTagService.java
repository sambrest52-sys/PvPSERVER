package net.pvpserver.core.combat;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks combat tags (recent PvP involvement) and the last attacker of each player.
 */
public final class CombatTagService {

    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private final Map<UUID, LastHit> lastHits = new ConcurrentHashMap<>();
    private long tagMillis;
    private long assistMillis;

    /**
     * @param tagSeconds combat tag length
     * @param killCreditSeconds how long the last attacker gets kill credit for environmental deaths
     */
    public CombatTagService(int tagSeconds, int killCreditSeconds) {
        configure(tagSeconds, killCreditSeconds);
    }

    /**
     * @param tagSeconds combat tag length
     * @param killCreditSeconds kill credit window
     */
    public void configure(int tagSeconds, int killCreditSeconds) {
        this.tagMillis = Math.max(0, tagSeconds) * 1000L;
        this.assistMillis = Math.max(1, killCreditSeconds) * 1000L;
    }

    /**
     * Tags both players and records the attacker.
     *
     * @param attacker attacker
     * @param victim victim
     */
    public void tag(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        if (tagMillis > 0) {
            tags.put(attacker.getUniqueId(), new Tag(now + tagMillis, victim.getUniqueId()));
            tags.put(victim.getUniqueId(), new Tag(now + tagMillis, attacker.getUniqueId()));
        }
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), now));
    }

    /**
     * @param player player
     * @return whether the player is combat tagged
     */
    public boolean isTagged(Player player) {
        Tag tag = tags.get(player.getUniqueId());
        return tag != null && tag.expires > System.currentTimeMillis();
    }

    /**
     * @param player player
     * @return remaining tag millis (0 when not tagged)
     */
    public long remaining(Player player) {
        Tag tag = tags.get(player.getUniqueId());
        return tag == null ? 0 : Math.max(0, tag.expires - System.currentTimeMillis());
    }

    /**
     * @param player victim
     * @return id of the last attacker within the kill credit window, or null
     */
    public UUID lastAttacker(Player player) {
        LastHit hit = lastHits.get(player.getUniqueId());
        return hit != null && System.currentTimeMillis() - hit.time <= assistMillis ? hit.attacker : null;
    }

    /**
     * Clears tag and last hit (on death, match end, leaving FFA, quit).
     *
     * @param player player
     */
    public void clear(Player player) {
        tags.remove(player.getUniqueId());
        lastHits.remove(player.getUniqueId());
    }

    private record Tag(long expires, UUID opponent) {
    }

    private record LastHit(UUID attacker, long time) {
    }
}
