package net.pvpserver.lobby.feature;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.LeaderboardService;
import net.pvpserver.core.stats.StatField;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The leaderboard wall: one panel per kit (plus a global one) showing the top players, cycling through the configured
 * statistics. Panels read the core's cached leaderboards, so a rotation never touches the database; the cache itself
 * refreshes every few minutes and the wall re-renders when it does.
 */
public final class LeaderboardWall {

    private final PvPLobby plugin;
    private final Displays displays;
    private final List<String> boards = new ArrayList<>();
    private int statIndex;
    private long lastRotation;

    /**
     * @param plugin lobby plugin
     * @param displays displays
     */
    public LeaderboardWall(PvPLobby plugin, Displays displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    private LobbySettings.WallSettings settings() {
        return plugin.settings().wall();
    }

    /** @return kit ids on the wall ("global" first when shown) */
    public List<String> boards() {
        return List.copyOf(boards);
    }

    /** Spawns the panels and icons. */
    public void spawn() {
        boards.clear();
        LobbyLayout.Wall wall = plugin.lobbyWorld().layout().wall();
        if (!settings().enabled() || wall == null) {
            return;
        }
        if (settings().kits().isEmpty()) {
            boards.add(LeaderboardService.GLOBAL);
            plugin.api().kits().all().stream().filter(kit -> kit.enabled() && kit.ranked()).sorted(Comparator.comparingInt(Kit::order))
                    .forEach(kit -> boards.add(kit.id()));
        } else {
            for (String kit : settings().kits()) {
                if (kit.equals(LeaderboardService.GLOBAL) || plugin.api().kits().get(kit).isPresent()) {
                    boards.add(kit);
                } else {
                    plugin.getLogger().warning("leaderboard-wall.kits: unknown kit " + kit);
                }
            }
        }
        for (int i = 0; i < boards.size(); i++) {
            Point panel = wall.panel(i);
            Location at = panel.at(plugin.lobbyWorld().world());
            displays.text("wall:" + i, at, Display.Billboard.FIXED, 0.62f, settings().background(), render(boards.get(i)));
            if (settings().icons()) {
                displays.item("wall-icon:" + i, at.clone().add(0, 3.25, 0), icon(boards.get(i)), 0.7f);
            }
        }
        lastRotation = System.currentTimeMillis();
    }

    private ItemStack icon(String kit) {
        if (kit.equals(LeaderboardService.GLOBAL)) {
            return new ItemStack(Material.NETHER_STAR);
        }
        Optional<Kit> found = plugin.api().kits().get(kit);
        ItemStack icon = found.map(Kit::icon).orElse(null);
        return icon == null ? new ItemStack(Material.IRON_SWORD) : new ItemStack(icon.getType());
    }

    /** @return statistic currently shown */
    public StatField stat() {
        List<StatField> stats = settings().stats();
        return stats.get(Math.floorMod(statIndex, stats.size()));
    }

    /** Moves to the next statistic when its time is up. Called every second. */
    public void tick() {
        if (boards.isEmpty() || System.currentTimeMillis() - lastRotation < settings().rotateSeconds() * 1000L) {
            return;
        }
        lastRotation = System.currentTimeMillis();
        statIndex++;
        refresh();
    }

    /** Re-renders every panel from the cache (after a rotation or a leaderboard refresh). */
    public void refresh() {
        for (int i = 0; i < boards.size(); i++) {
            displays.update("wall:" + i, render(boards.get(i)));
        }
    }

    private Component render(String kit) {
        MessageService m = plugin.messages();
        StatField field = stat();
        String kitId = kit.equals(LeaderboardService.GLOBAL) ? null : kit.toLowerCase(Locale.ROOT);
        Component name = kitId == null ? m.get("wall.global") : plugin.api().kits().get(kitId).map(Kit::name).orElse(Component.text(kit));
        Component text = m.get("wall.title", MessageService.c("kit", name), MessageService.p("stat", field.displayName()));
        List<LeaderboardEntry> entries = plugin.api().leaderboards().top(kitId, field);
        int limit = Math.min(settings().entries(), entries.size());
        for (int i = 0; i < limit; i++) {
            LeaderboardEntry entry = entries.get(i);
            String key = i == 0 ? "wall.first" : i == 1 ? "wall.second" : i == 2 ? "wall.third" : "wall.line";
            text = text.append(Component.newline()).append(m.get(key, MessageService.p("position", i + 1),
                    MessageService.p("player", entry.name()), MessageService.p("value", entry.value())));
        }
        if (limit == 0) {
            text = text.append(Component.newline()).append(m.get("wall.empty"));
        }
        return text.append(Component.newline()).append(m.get("wall.footer", MessageService.p("seconds", settings().rotateSeconds())));
    }
}
