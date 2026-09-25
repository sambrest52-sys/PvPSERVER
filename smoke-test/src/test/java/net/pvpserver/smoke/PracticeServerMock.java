package net.pvpserver.smoke;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;

/**
 * ServerMock with the few server/world methods MockBukkit leaves unimplemented but the plugins use.
 */
class PracticeServerMock extends ServerMock {

    private final File worldContainer;

    PracticeServerMock(File worldContainer) {
        this.worldContainer = worldContainer;
        worldContainer.mkdirs();
    }

    @Override
    public File getWorldContainer() {
        return worldContainer;
    }

    private final PracticeScoreboards.Manager scoreboards = new PracticeScoreboards.Manager();

    @Override
    public org.mockbukkit.mockbukkit.scoreboard.ScoreboardManagerMock getScoreboardManager() {
        return scoreboards;
    }

    @Override
    public org.mockbukkit.mockbukkit.inventory.InventoryMock createInventory(org.bukkit.inventory.InventoryHolder owner, int size,
                                                                            net.kyori.adventure.text.Component title) {
        return new HolderAwareChest(owner, size);
    }

    /**
     * Chest inventory implementing Paper's {@code getHolder(boolean)} (used by the menu framework).
     */
    static final class HolderAwareChest extends org.mockbukkit.mockbukkit.inventory.ChestInventoryMock {
        HolderAwareChest(org.bukkit.inventory.InventoryHolder holder, int size) {
            super(holder, size);
        }

        @Override
        public org.bukkit.inventory.InventoryHolder getHolder(boolean useSnapshot) {
            return getHolder();
        }
    }

    @Override
    public double[] getTPS() {
        return new double[]{20.0, 20.0, 20.0};
    }

    @Override
    public World createWorld(WorldCreator creator) {
        World existing = getWorld(creator.name());
        if (existing != null) {
            return existing;
        }
        PracticeWorldMock world = new PracticeWorldMock(creator);
        addWorld(world);
        return world;
    }

    /**
     * WorldMock with chunk tickets as no-ops (everything stays "loaded" in the mock).
     */
    static final class PracticeWorldMock extends WorldMock {
        PracticeWorldMock(WorldCreator creator) {
            super(creator);
        }

        @Override
        public boolean addPluginChunkTicket(int x, int z, Plugin plugin) {
            return true;
        }

        @Override
        public boolean removePluginChunkTicket(int x, int z, Plugin plugin) {
            return true;
        }
    }
}
