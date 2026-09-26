package net.pvpserver.smoke;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.TextDisplayMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.UUID;
import java.util.function.Consumer;

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

    /**
     * MockBukkit's block-state parser cannot apply some valid 1.21 properties (lantern {@code hanging}, chain
     * {@code axis}, snow {@code layers}, wall/pane/bars connections) that Paper's vanilla parser accepts. For a block
     * id that exists, fall back to its default state; unknown ids still fail. The generator's unit tests validate
     * every property against a schema, so nothing real is hidden.
     */
    @Override
    public org.bukkit.block.data.BlockData createBlockData(String data) {
        try {
            return super.createBlockData(data);
        } catch (IllegalArgumentException e) {
            int bracket = data.indexOf('[');
            if (bracket > 0 && org.bukkit.Material.matchMaterial(data.substring(0, bracket)) != null) {
                return super.createBlockData(data.substring(0, bracket));
            }
            throw e;
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
     * Adds a world with the same flat terrain as {@link #addSimpleWorld(String)}, but backed by {@link PracticeWorldMock}.
     *
     * @param name world name
     * @return the world
     */
    PracticeWorldMock addPracticeWorld(String name) {
        PracticeWorldMock world = new PracticeWorldMock();
        world.setName(name);
        addWorld(world);
        return world;
    }

    /**
     * WorldMock with chunk tickets as no-ops (everything stays "loaded" in the mock) and text displays that support
     * billboards (used by leaderboard holograms).
     */
    static final class PracticeWorldMock extends WorldMock {
        PracticeWorldMock() {
            super();
        }

        PracticeWorldMock(WorldCreator creator) {
            super(creator);
        }

        @Override
        public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<? super T> function,
                                          CreatureSpawnEvent.SpawnReason reason, boolean randomizeData, boolean callEvent) {
            if (clazz != TextDisplay.class) {
                return super.spawn(location, clazz, function, reason, randomizeData, callEvent);
            }
            ServerMock server = MockBukkit.getMock();
            BillboardTextDisplay display = new BillboardTextDisplay(server, UUID.randomUUID());
            Location at = location.clone();
            at.setWorld(this);
            display.setLocation(at);
            T entity = clazz.cast(display);
            if (function != null) {
                function.accept(entity);
            }
            server.registerEntity(display);
            return entity;
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

    /**
     * Text display implementing the billboard property MockBukkit leaves unimplemented.
     */
    static final class BillboardTextDisplay extends TextDisplayMock {
        private Display.Billboard billboard = Display.Billboard.FIXED;

        BillboardTextDisplay(ServerMock server, UUID uuid) {
            super(server, uuid);
        }

        @Override
        public Display.Billboard getBillboard() {
            return billboard;
        }

        @Override
        public void setBillboard(Display.Billboard billboard) {
            this.billboard = billboard;
        }
    }
}
