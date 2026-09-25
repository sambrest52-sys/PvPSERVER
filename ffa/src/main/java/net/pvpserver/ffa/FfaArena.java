package net.pvpserver.ffa;

import net.pvpserver.core.kit.Kit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A live FFA arena: pasted template, spawns, safe zone and current players.
 */
public final class FfaArena {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final Kit kit;
    private final boolean ranked;
    private final String knockback;
    private final double safeRadius;
    private final List<Location> spawns;
    private final int minY;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();

    /**
     * @param id arena id
     * @param displayName MiniMessage name
     * @param icon menu icon
     * @param kit kit
     * @param ranked ranked flag (FFA rating)
     * @param knockback knockback profile override or empty
     * @param safeRadius horizontal safe zone radius around spawns
     * @param spawns spawn points
     * @param minY Y below which players count as fallen
     */
    public FfaArena(String id, String displayName, Material icon, Kit kit, boolean ranked, String knockback, double safeRadius,
                    List<Location> spawns, int minY) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.kit = kit;
        this.ranked = ranked;
        this.knockback = knockback;
        this.safeRadius = safeRadius;
        this.spawns = spawns;
        this.minY = minY;
    }

    /** @return a random spawn */
    public Location randomSpawn() {
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())).clone();
    }

    /**
     * @param location location
     * @return whether it is inside the spawn safe zone
     */
    public boolean inSafeZone(Location location) {
        if (safeRadius <= 0) {
            return false;
        }
        for (Location spawn : spawns) {
            if (spawn.getWorld() != location.getWorld()) {
                continue;
            }
            double dx = spawn.getX() - location.getX();
            double dz = spawn.getZ() - location.getZ();
            if (dx * dx + dz * dz <= safeRadius * safeRadius && Math.abs(spawn.getY() - location.getY()) < 6) {
                return true;
            }
        }
        return false;
    }

    /** @return id */
    public String id() {
        return id;
    }

    /** @return display name */
    public String displayName() {
        return displayName;
    }

    /** @return icon */
    public Material icon() {
        return icon;
    }

    /** @return kit */
    public Kit kit() {
        return kit;
    }

    /** @return ranked flag */
    public boolean ranked() {
        return ranked;
    }

    /** @return knockback override */
    public String knockback() {
        return knockback;
    }

    /** @return fall Y */
    public int minY() {
        return minY;
    }

    /** @return players (mutable) */
    public Set<UUID> players() {
        return players;
    }
}
