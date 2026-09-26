package net.pvpserver.smoke;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Boots the four real plugin jars in a mocked Paper server for each test and offers helpers that drive it through
 * the Bukkit API only (tests never link against plugin classes, just like a server).
 */
@ExtendWith(SmokeTestBase.FailOnUnimplemented.class)
abstract class SmokeTestBase {

    /** Countdown (5 s) plus a margin, in ticks. */
    protected static final int COUNTDOWN_TICKS = 110;
    /** End screen (4 s) plus a margin, in ticks. */
    protected static final int END_TICKS = 100;

    @TempDir
    Path temp;
    protected ServerMock server;
    protected Plugin core;
    protected Plugin lobby;
    protected Plugin duels;
    protected Plugin ffa;

    @BeforeEach
    void boot() throws Exception {
        server = MockBukkit.mock(new PracticeServerMock(temp.resolve("worlds").toFile()));
        ((PracticeServerMock) server).addPracticeWorld("world");
        File pluginsFolder = temp.resolve("plugins").toFile();
        beforeLoad(pluginsFolder);
        PluginHarness harness = new PluginHarness(server, new File("target/plugins"), pluginsFolder);
        core = harness.load("PvPCore.jar");
        lobby = harness.load("PvPLobby.jar");
        duels = harness.load("PvPDuels.jar");
        ffa = harness.load("PvPFFA.jar");
        // Let arena templates load (async) and the arena/FFA worlds get pasted.
        waitFor(() -> false, 2000);
    }

    /**
     * Hook to write configuration before the plugins load.
     *
     * @param pluginsFolder the server's plugins folder (data folders are {@code <folder>/<PluginName>})
     * @throws IOException on write failure
     */
    protected void beforeLoad(File pluginsFolder) throws IOException {
    }

    @AfterEach
    void shutdown() throws Exception {
        MockBukkit.unmock();
        afterShutdown();
    }

    /**
     * Hook that runs after the server stopped and the plugins saved their data.
     *
     * @throws Exception on failure
     */
    protected void afterShutdown() throws Exception {
    }

    // ------------------------------------------------------------------ time

    /** Runs server ticks (with short sleeps so async storage work can finish) until the condition holds or time runs out. */
    protected void waitFor(BooleanSupplier condition, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end && !condition.getAsBoolean()) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
    }

    /** Like {@link #waitFor} but fails the test when the condition never holds. */
    protected void await(String what, BooleanSupplier condition, long millis) throws InterruptedException {
        waitFor(condition, millis);
        assertTrue(condition.getAsBoolean(), "timed out waiting for: " + what);
    }

    /** Runs exactly {@code count} server ticks. */
    protected void ticks(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            server.getScheduler().performOneTick();
            if (i % 10 == 0) {
                Thread.sleep(1);
            }
        }
    }

    // ------------------------------------------------------------------ players

    protected PlayerMock join(String name) throws InterruptedException {
        PlayerMock player = new PracticePlayerMock(server, name);
        server.addPlayer(player);
        waitFor(() -> false, 100);
        return player;
    }

    protected boolean run(PlayerMock player, String command) {
        return server.dispatchCommand(player, command);
    }

    protected static boolean in(String world, PlayerMock... players) {
        return Arrays.stream(players).allMatch(p -> p.isOnline() && world.equals(p.getWorld().getName()));
    }

    /** Waits until every player is inside an arena instance. */
    protected void awaitArena(PlayerMock... players) throws InterruptedException {
        await("players in an arena", () -> in("pvp_arenas", players), 6000);
    }

    /** Waits until every player is back in the lobby world. */
    protected void awaitLobby(PlayerMock... players) throws InterruptedException {
        await("players back in the lobby", () -> in("world", players), 8000);
    }

    /** Joins an FFA arena, retrying while FFA is still pasting its arenas after startup. */
    protected void joinFfa(PlayerMock player, String arena) throws InterruptedException {
        await(player.getName() + " joins FFA " + arena, () -> {
            if (!in("pvp_ffa", player)) {
                run(player, "ffa " + arena);
                for (int i = 0; i < 10; i++) {
                    server.getScheduler().performOneTick();
                }
            }
            return in("pvp_ffa", player);
        }, 10000);
    }

    /** Clicks the first slot of the open top inventory holding {@code material}, then lets the menu action run. */
    protected void clickItem(PlayerMock player, Material material) throws InterruptedException {
        Inventory top = player.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack item = top.getItem(slot);
            if (item != null && item.getType() == material) {
                player.simulateInventoryClick(player.getOpenInventory(), slot);
                ticks(2);
                return;
            }
        }
        fail(player.getName() + "'s open menu has no " + material + " (" + top.getSize() + " slots)");
    }

    /** Clicks a slot of the open top inventory, then lets the menu action run. */
    protected void clickSlot(PlayerMock player, int slot) throws InterruptedException {
        player.simulateInventoryClick(player.getOpenInventory(), slot);
        ticks(2);
    }

    // ------------------------------------------------------------------ combat

    /** Fires the damage event Paper would fire for a melee hit and applies it when not cancelled. */
    protected EntityDamageByEntityEvent hit(PlayerMock attacker, PlayerMock victim, double damage) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, damage);
        server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            victim.damage(event.getFinalDamage());
        }
        return event;
    }

    /** Moves the attacker next to the victim so the alert-only reach check stays quiet. */
    protected static void closeIn(PlayerMock attacker, PlayerMock victim) {
        Location spot = victim.getLocation().clone().add(victim.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        spot.setDirection(victim.getLocation().toVector().subtract(spot.toVector()));
        attacker.teleport(spot);
    }

    // ------------------------------------------------------------------ chat

    /** Drains and returns the player's received messages as plain text. */
    protected static List<String> drain(PlayerMock player) {
        List<String> out = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            out.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return out;
    }

    /** Drains the player's messages and returns the first click command starting with the prefix, or null. */
    protected static String drainClickCommand(PlayerMock player, String prefix) {
        String found = null;
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            if (found == null) {
                found = clickCommand(message, prefix);
            }
        }
        return found;
    }

    private static String clickCommand(Component component, String prefix) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND && click.value().startsWith(prefix)) {
            return click.value();
        }
        for (Component child : component.children()) {
            String found = clickCommand(child, prefix);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ storage

    /** Opens the default SQLite database the core plugin writes to. */
    protected Connection sqlite() throws SQLException {
        File db = new File(core.getDataFolder(), "data/practice.db");
        return DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
    }

    /**
     * MockBukkit's {@link UnimplementedOperationException} aborts a test, which JUnit reports as <em>skipped</em>.
     * A plugin reaching an unimplemented mock method must fail loudly instead, so the gap gets filled in
     * {@link PracticeServerMock} rather than silently hiding a scenario.
     */
    static final class FailOnUnimplemented implements TestExecutionExceptionHandler {
        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            if (throwable instanceof UnimplementedOperationException) {
                throw new AssertionError("MockBukkit does not implement a method the plugins used", throwable);
            }
            throw throwable;
        }
    }
}
