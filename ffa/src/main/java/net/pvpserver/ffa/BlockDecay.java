package net.pvpserver.ffa;

import net.pvpserver.core.arena.ArenaInstance;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Removes player-placed FFA blocks after a delay so arenas never fill up.
 */
public final class BlockDecay {

    private final Map<Long, Long> placed = new LinkedHashMap<>();
    private World world;
    private long lifetimeMillis = 10_000;

    /**
     * @param lifetimeSeconds seconds before a placed block disappears
     */
    public BlockDecay(int lifetimeSeconds) {
        configure(lifetimeSeconds);
        Tasks.timer(this::tick, 20L, 20L);
    }

    /**
     * @param lifetimeSeconds seconds before a placed block disappears
     */
    public void configure(int lifetimeSeconds) {
        this.lifetimeMillis = Math.max(1, lifetimeSeconds) * 1000L;
    }

    /**
     * @param block placed block
     */
    public void track(Block block) {
        world = block.getWorld();
        placed.put(key(block), System.currentTimeMillis() + lifetimeMillis);
    }

    /**
     * @param block block
     * @return whether a player placed it (and it has not decayed yet)
     */
    public boolean isPlaced(Block block) {
        return placed.containsKey(key(block));
    }

    /**
     * @param block block broken by a player
     */
    public void untrack(Block block) {
        placed.remove(key(block));
    }

    private void tick() {
        if (world == null || placed.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, Long>> iterator = placed.entrySet().iterator();
        int budget = 500;
        while (iterator.hasNext() && budget-- > 0) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                // Insertion order == expiry order, nothing further has expired.
                break;
            }
            long key = entry.getKey();
            Block block = world.getBlockAt(ArenaInstance.keyX(key), ArenaInstance.keyY(key), ArenaInstance.keyZ(key));
            block.setType(Material.AIR, false);
            iterator.remove();
        }
    }

    /** Clears every tracked block immediately (disable). */
    public void clearAll() {
        if (world != null) {
            for (long key : placed.keySet()) {
                world.getBlockAt(ArenaInstance.keyX(key), ArenaInstance.keyY(key), ArenaInstance.keyZ(key))
                        .setType(Material.AIR, false);
            }
        }
        placed.clear();
    }

    private static long key(Block block) {
        return ArenaInstance.key(block.getX(), block.getY(), block.getZ());
    }
}
