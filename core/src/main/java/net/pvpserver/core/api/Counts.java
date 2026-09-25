package net.pvpserver.core.api;

import net.pvpserver.core.api.bridge.FfaBridge;
import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;

/**
 * Server-wide counters (online, queued, fighting, FFA, TPS) refreshed once per second and read by sidebars, tab
 * lists and menus without recomputing per viewer.
 */
public final class Counts {

    private final BridgeRegistry bridges;
    private final PlayerStateService states;
    private volatile int online;
    private volatile int queued;
    private volatile int fighting;
    private volatile int ffa;
    private volatile int spectating;
    private volatile double tps = 20.0;

    /**
     * @param bridges bridges
     * @param states states
     */
    public Counts(BridgeRegistry bridges, PlayerStateService states) {
        this.bridges = bridges;
        this.states = states;
    }

    /** Starts the refresh timer. */
    public void start() {
        Tasks.timer(this::refresh, 20L, 20L);
    }

    private void refresh() {
        online = Bukkit.getOnlinePlayers().size();
        queued = bridges.get(QueueBridge.class).map(QueueBridge::queuedPlayers).orElse(states.count(PlayerState.QUEUE));
        fighting = bridges.get(MatchBridge.class).map(MatchBridge::playersInMatches).orElse(states.count(PlayerState.MATCH));
        ffa = bridges.get(FfaBridge.class).map(FfaBridge::playerCount).orElse(states.count(PlayerState.FFA));
        spectating = states.count(PlayerState.SPECTATING);
        tps = Math.min(20.0, Bukkit.getTPS()[0]);
    }

    /** @return online players */
    public int online() {
        return online;
    }

    /** @return queued players */
    public int queued() {
        return queued;
    }

    /** @return players in matches */
    public int fighting() {
        return fighting;
    }

    /** @return players in FFA */
    public int ffa() {
        return ffa;
    }

    /** @return spectators */
    public int spectating() {
        return spectating;
    }

    /** @return 1 minute TPS capped at 20 */
    public double tps() {
        return tps;
    }

    /** @return TPS formatted with one decimal */
    public String tpsFormatted() {
        return String.format(java.util.Locale.ROOT, "%.1f", tps);
    }
}
