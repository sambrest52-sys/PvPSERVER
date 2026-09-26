package net.pvpserver.core.arena;

import net.pvpserver.core.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A pasted copy of an arena in the arena world. Tracks every block changed while in use so it can be rolled back
 * before being returned to the pool.
 */
public final class ArenaInstance {

    /**
     * Lifecycle of a pooled instance.
     */
    public enum State { PASTING, IDLE, IN_USE, RESETTING, DESTROYED }

    private final UUID id = UUID.randomUUID();
    private final Arena arena;
    private final ArenaTemplate template;
    private final World world;
    private final int slot;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final Cuboid bounds;
    private final Map<Long, BlockData> journal = new HashMap<>();
    private final Set<Long> placed = new HashSet<>();
    private volatile State state = State.PASTING;

    /**
     * @param arena definition
     * @param template template
     * @param world arena world
     * @param slot grid slot
     * @param originX min corner x
     * @param originY min corner y
     * @param originZ min corner z
     * @param margin horizontal margin around the template that still belongs to the instance
     */
    public ArenaInstance(Arena arena, ArenaTemplate template, World world, int slot, int originX, int originY, int originZ, int margin) {
        this.arena = arena;
        this.template = template;
        this.world = world;
        this.slot = slot;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        int top = Math.max(template.sizeY() - 1, arena.buildLimit()) + 8;
        this.bounds = new Cuboid(originX - margin, originY + Math.min(0, arena.voidY()) - 16, originZ - margin,
                originX + template.sizeX() - 1 + margin, originY + top, originZ + template.sizeZ() - 1 + margin);
    }

    /**
     * @param x absolute x
     * @param y absolute y
     * @param z absolute z
     * @return packed position key
     */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * @param key packed key
     * @return x
     */
    public static int keyX(long key) {
        return (int) (key >> 38) << 6 >> 6;
    }

    /**
     * @param key packed key
     * @return z
     */
    public static int keyZ(long key) {
        return (int) ((key >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    /**
     * @param key packed key
     * @return y
     */
    public static int keyY(long key) {
        return (int) (key & 0xFFF) << 20 >> 20;
    }

    /**
     * Records the original state of a block before it changes (first change wins).
     *
     * @param block block about to change
     */
    public void journal(Block block) {
        journal.putIfAbsent(key(block.getX(), block.getY(), block.getZ()), block.getBlockData());
    }

    /**
     * Records an original state explicitly (e.g. from {@code BlockPlaceEvent#getBlockReplacedState}).
     *
     * @param block block position
     * @param original original data
     */
    public void journal(Block block, BlockData original) {
        journal.putIfAbsent(key(block.getX(), block.getY(), block.getZ()), original);
    }

    /**
     * @param block block placed by a player
     */
    public void markPlaced(Block block) {
        placed.add(key(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * @param block block
     * @return whether a player placed it during the current use
     */
    public boolean isPlaced(Block block) {
        return placed.contains(key(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * @param block block that was broken
     */
    public void unmarkPlaced(Block block) {
        placed.remove(key(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * @param location location
     * @return whether the location is within the instance bounds (same world)
     */
    public boolean contains(Location location) {
        return location.getWorld() == world && bounds.contains(location);
    }

    /**
     * @param x block x
     * @param y block y
     * @param z block z
     * @return whether building is allowed at the position (template footprint, below the build limit and inside the
     *         arena's build area when it has one)
     */
    public boolean canBuildAt(int x, int y, int z) {
        boolean inFootprint = x >= originX && x < originX + template.sizeX() && z >= originZ && z < originZ + template.sizeZ()
                && y >= originY && y <= originY + arena.buildLimit();
        RelativeBox area = arena.buildArea();
        return inFootprint && (area == null || area.contains(x - originX, y - originY, z - originZ));
    }

    /** @return absolute Y below which players count as fallen */
    public int voidY() {
        return originY + arena.voidY();
    }

    /**
     * @param position relative position
     * @return absolute location
     */
    public Location at(RelativePosition position) {
        return position == null ? null : position.toLocation(world, originX, originY, originZ);
    }

    /** @return team A spawn */
    public Location spawnA() {
        return at(arena.spawnA());
    }

    /** @return team B spawn */
    public Location spawnB() {
        return at(arena.spawnB());
    }

    /** @return spectator spawn */
    public Location spectatorSpawn() {
        return at(arena.spectatorOrDefault());
    }

    /**
     * @param location location
     * @param goal relative goal centre
     * @return whether the location is inside the goal (horizontal radius, within 2 blocks vertically)
     */
    public boolean inGoal(Location location, RelativePosition goal) {
        if (goal == null || location.getWorld() != world) {
            return false;
        }
        Location centre = at(goal);
        double dx = location.getX() - centre.getX();
        double dz = location.getZ() - centre.getZ();
        double dy = location.getY() - centre.getY();
        return dx * dx + dz * dz <= arena.goalRadius() * arena.goalRadius() && dy <= 1.5 && dy >= -3;
    }

    /** @return changed blocks and their original data */
    Map<Long, BlockData> journalEntries() {
        return journal;
    }

    /** Clears journal and placed markers after a rollback. */
    void clearTracking() {
        journal.clear();
        placed.clear();
    }

    /** @return instance id */
    public UUID id() {
        return id;
    }

    /** @return arena definition */
    public Arena arena() {
        return arena;
    }

    /** @return template */
    public ArenaTemplate template() {
        return template;
    }

    /** @return world */
    public World world() {
        return world;
    }

    /** @return grid slot */
    public int slot() {
        return slot;
    }

    /** @return origin x */
    public int originX() {
        return originX;
    }

    /** @return origin y */
    public int originY() {
        return originY;
    }

    /** @return origin z */
    public int originZ() {
        return originZ;
    }

    /** @return absolute bounds */
    public Cuboid bounds() {
        return bounds;
    }

    /** @return state */
    public State state() {
        return state;
    }

    /** @param state new state */
    void state(State state) {
        this.state = state;
    }
}
