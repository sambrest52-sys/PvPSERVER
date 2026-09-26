package net.pvpserver.core.arena;

import org.bukkit.Material;

import java.util.Set;

/**
 * Arena definition from arenas.yml. All positions are relative to the template's minimum corner.
 *
 * @param name id (lowercase, also the template file name)
 * @param displayName MiniMessage display name
 * @param icon menu icon
 * @param enabled whether matches may use it
 * @param tags tags matched against kit {@code arena-tags}
 * @param spawnA first team spawn
 * @param spawnB second team spawn
 * @param spectator spectator spawn (defaults to between spawns)
 * @param buildLimit maximum relative Y for placing blocks
 * @param voidY relative Y below which players are considered fallen
 * @param goalA goal of team A (bridge), nullable
 * @param goalB goal of team B (bridge), nullable
 * @param goalRadius horizontal goal radius
 * @param buildArea where blocks may be placed (relative box), or null for the whole footprint below the build limit
 */
public record Arena(String name, String displayName, Material icon, boolean enabled, Set<String> tags,
                    RelativePosition spawnA, RelativePosition spawnB, RelativePosition spectator, int buildLimit,
                    int voidY, RelativePosition goalA, RelativePosition goalB, double goalRadius, RelativeBox buildArea) {

    /** @return whether both spawns are set */
    public boolean complete() {
        return spawnA != null && spawnB != null;
    }

    /** @return spectator spawn or the midpoint of the spawns raised by 3 blocks */
    public RelativePosition spectatorOrDefault() {
        if (spectator != null) {
            return spectator;
        }
        return new RelativePosition((spawnA.x() + spawnB.x()) / 2, Math.max(spawnA.y(), spawnB.y()) + 3,
                (spawnA.z() + spawnB.z()) / 2, spawnA.yaw(), 30f);
    }

    /**
     * @param enabled new state
     * @return copy
     */
    public Arena withEnabled(boolean enabled) {
        return new Arena(name, displayName, icon, enabled, tags, spawnA, spawnB, spectator, buildLimit, voidY, goalA, goalB, goalRadius,
                buildArea);
    }
}
