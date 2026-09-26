package net.pvpserver.lobby.feature;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The rotating tips boss bar (one shared bar, shown to players in the lobby) and timed chat announcements.
 */
public final class AnnouncementService {

    private final PvPLobby plugin;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private int tip = -1;
    private int tipSeconds;
    private int announcement;
    private int announcementSeconds;

    /**
     * @param plugin lobby plugin
     */
    public AnnouncementService(PvPLobby plugin) {
        this.plugin = plugin;
    }

    /** @return the tips bar */
    public BossBar bar() {
        return bar;
    }

    /** Applies bar style changes (reload). */
    public void reload() {
        LobbySettings.Bossbar settings = plugin.settings().bossbar();
        bar.color(settings.color());
        bar.overlay(settings.overlay());
        tip = -1;
        tipSeconds = 0;
        nextTip();
        if (!settings.enabled()) {
            hideAll();
        }
    }

    private void nextTip() {
        List<String> tips = plugin.settings().bossbar().tips();
        if (tips.isEmpty()) {
            bar.name(Component.empty());
            return;
        }
        tip = (tip + 1) % tips.size();
        bar.name(plugin.messages().parse(tips.get(tip)));
        bar.progress(1f);
        tipSeconds = 0;
    }

    /** Called every second. */
    public void tick() {
        LobbySettings.Bossbar settings = plugin.settings().bossbar();
        if (settings.enabled()) {
            tipSeconds++;
            if (tipSeconds >= settings.secondsPerTip()) {
                nextTip();
            } else {
                bar.progress(Math.max(0f, 1f - (float) tipSeconds / settings.secondsPerTip()));
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean show = inLobby(player);
                if (show && viewers.add(player.getUniqueId())) {
                    player.showBossBar(bar);
                } else if (!show && viewers.remove(player.getUniqueId())) {
                    player.hideBossBar(bar);
                }
            }
        }
        LobbySettings.Announcements announcements = plugin.settings().announcements();
        if (announcements.enabled() && !announcements.messages().isEmpty() && ++announcementSeconds >= announcements.intervalSeconds()) {
            announcementSeconds = 0;
            String message = announcements.messages().get(announcement++ % announcements.messages().size());
            Component text = plugin.messages().parse(message);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!announcements.lobbyOnly() || inLobby(player)) {
                    player.sendMessage(text);
                }
            }
        }
    }

    private boolean inLobby(Player player) {
        return plugin.api().states().is(player, PlayerState.LOBBY, PlayerState.QUEUE, PlayerState.EDITING);
    }

    /**
     * @param player player leaving
     */
    public void forget(Player player) {
        if (viewers.remove(player.getUniqueId())) {
            player.hideBossBar(bar);
        }
    }

    /** Hides the bar from everyone (disable). */
    public void hideAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewers.contains(player.getUniqueId())) {
                player.hideBossBar(bar);
            }
        }
        viewers.clear();
    }

    /**
     * @param player player
     * @return whether the player currently sees the tips bar
     */
    public boolean showing(Player player) {
        return viewers.contains(player.getUniqueId());
    }
}
