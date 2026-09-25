package net.pvpserver.core.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player named cooldowns.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * @param player player id
     * @param key cooldown name
     * @param millis duration
     */
    public void set(UUID player, String key, long millis) {
        cooldowns.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(key, System.currentTimeMillis() + millis);
    }

    /**
     * @param player player id
     * @param key cooldown name
     * @return remaining millis (0 if ready)
     */
    public long remaining(UUID player, String key) {
        Map<String, Long> map = cooldowns.get(player);
        if (map == null) {
            return 0;
        }
        Long until = map.get(key);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    /**
     * @param player player id
     */
    public void clear(UUID player) {
        cooldowns.remove(player);
    }
}
