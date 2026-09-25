package net.pvpserver.core.scoreboard;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns every player's {@link Sidebar} and refreshes them from the provider registered for the player's state.
 * Players are spread over {@code interval} ticks (bucketed by id) so per-tick work stays flat with 200+ players.
 */
public final class SidebarService implements Listener {

    private final ProfileService profiles;
    private final PlayerStateService states;
    private final Logger logger;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final Map<PlayerState, SidebarProvider> providers = new EnumMap<>(PlayerState.class);
    private int interval = 10;
    private long tick;

    /**
     * @param profiles profiles (scoreboard setting)
     * @param states states
     * @param logger logger for provider errors
     */
    public SidebarService(ProfileService profiles, PlayerStateService states, Logger logger) {
        this.profiles = profiles;
        this.states = states;
        this.logger = logger;
    }

    /**
     * @param intervalTicks refresh period per player
     */
    public void start(int intervalTicks) {
        this.interval = Math.max(1, intervalTicks);
        Tasks.timer(this::tick, 1L, 1L);
    }

    /**
     * Registers the provider for a state (last registration wins).
     *
     * @param state state
     * @param provider provider
     */
    public void register(PlayerState state, SidebarProvider provider) {
        providers.put(state, provider);
    }

    /**
     * @param state state
     */
    public void unregister(PlayerState state) {
        providers.remove(state);
    }

    /**
     * @param player player
     * @return the player's sidebar (created on demand)
     */
    public Sidebar sidebar(Player player) {
        return sidebars.computeIfAbsent(player.getUniqueId(), id -> new Sidebar(player));
    }

    private void tick() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (((player.getUniqueId().hashCode() & 0x7fffffff) + tick) % interval == 0) {
                refresh(player);
            }
        }
    }

    /**
     * Refreshes one player's sidebar immediately (e.g. right after a state change).
     *
     * @param player player
     */
    public void refresh(Player player) {
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            return;
        }
        PlayerProfile profile = profiles.get(player);
        boolean enabled = profile == null || profile.settings().is(Setting.SCOREBOARD);
        SidebarProvider provider = providers.get(states.get(player));
        if (!enabled || provider == null) {
            sidebar.visible(false);
            return;
        }
        try {
            Component title = provider.title(player);
            List<Component> lines = provider.lines(player);
            sidebar.update(title, lines);
            sidebar.visible(true);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Sidebar provider for " + states.get(player) + " failed", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        sidebar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sidebars.remove(event.getPlayer().getUniqueId());
    }
}
