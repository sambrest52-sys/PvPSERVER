package net.pvpserver.lobby.feature;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parkour runs: step on the start plate to start the timer, reach the checkpoints in order, finish on the finish
 * plate. Falling below the course returns runners to their last checkpoint. Best times are kept per player and the
 * fastest are shown on the parkour hologram.
 */
public final class ParkourService {

    /** Hologram id of the best times board. */
    public static final String BOARD = "parkour";

    /**
     * A running attempt.
     *
     * @param started start time (millis)
     * @param checkpoint last reached checkpoint index (-1 = only the start)
     */
    public record Run(long started, int checkpoint) {
    }

    private final PvPLobby plugin;
    private final LobbyData data;
    private final Displays displays;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    /**
     * @param plugin lobby plugin
     * @param data best times
     * @param displays displays
     */
    public ParkourService(PvPLobby plugin, LobbyData data, Displays displays) {
        this.plugin = plugin;
        this.data = data;
        this.displays = displays;
    }

    private LobbySettings.ParkourSettings settings() {
        return plugin.settings().parkour();
    }

    private LobbyLayout.Parkour course() {
        return plugin.lobbyWorld().layout().parkour();
    }

    /**
     * @param player player
     * @return running attempt or null
     */
    public Run run(Player player) {
        return runs.get(player.getUniqueId());
    }

    /**
     * @param player player
     * @return whether the player is running the parkour
     */
    public boolean running(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    /** @return number of runners */
    public int runners() {
        return runs.size();
    }

    /**
     * Start plate stepped on: (re)starts the timer.
     *
     * @param player player
     */
    public void start(Player player) {
        if (!settings().enabled() || course() == null) {
            return;
        }
        if (player.isFlying() || player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            plugin.messages().actionBar(player, "parkour.no-flying");
            return;
        }
        boolean restart = runs.containsKey(player.getUniqueId());
        runs.put(player.getUniqueId(), new Run(System.currentTimeMillis(), -1));
        player.setAllowFlight(false);
        player.setFlying(false);
        play(player, settings().startSound(), 1.4f);
        if (!restart) {
            plugin.messages().send(player, "parkour.started", MessageService.p("checkpoints", course().checkpoints().size()));
            plugin.hotbar().give(player);
        }
    }

    /**
     * Checkpoint plate stepped on.
     *
     * @param player player
     * @param index checkpoint index
     */
    public void checkpoint(Player player, int index) {
        Run run = runs.get(player.getUniqueId());
        if (run == null || index <= run.checkpoint()) {
            return;
        }
        if (index > run.checkpoint() + 1) {
            plugin.messages().actionBar(player, "parkour.checkpoint-skipped", MessageService.p("checkpoint", run.checkpoint() + 2));
            return;
        }
        runs.put(player.getUniqueId(), new Run(run.started(), index));
        play(player, settings().checkpointSound(), 1.2f);
        plugin.messages().send(player, "parkour.checkpoint", MessageService.p("checkpoint", index + 1),
                MessageService.p("total", course().checkpoints().size()),
                MessageService.p("time", TimeUtil.formatMillis(System.currentTimeMillis() - run.started())));
    }

    /**
     * Finish plate stepped on.
     *
     * @param player player
     */
    public void finish(Player player) {
        Run run = runs.get(player.getUniqueId());
        LobbyLayout.Parkour course = course();
        if (run == null || course == null) {
            return;
        }
        if (run.checkpoint() < course.checkpoints().size() - 1) {
            plugin.messages().actionBar(player, "parkour.missed-checkpoints", MessageService.p("checkpoint", run.checkpoint() + 2));
            return;
        }
        long millis = System.currentTimeMillis() - run.started();
        end(player);
        LobbyData.Time previousRecord = data.top(1).isEmpty() ? null : data.top(1).get(0);
        LobbyData.Time previous = data.record(player.getUniqueId(), player.getName(), millis);
        String time = TimeUtil.formatMillis(millis);
        play(player, settings().finishSound(), 1f);
        if (previous == null) {
            plugin.messages().send(player, "parkour.first-finish", MessageService.p("time", time));
            commands(settings().firstFinishCommands(), player, time, millis);
        } else if (millis < previous.millis()) {
            plugin.messages().send(player, "parkour.personal-best", MessageService.p("time", time),
                    MessageService.p("improvement", TimeUtil.formatMillis(previous.millis() - millis)));
            commands(settings().personalBestCommands(), player, time, millis);
        } else {
            plugin.messages().send(player, "parkour.finished", MessageService.p("time", time),
                    MessageService.p("best", TimeUtil.formatMillis(previous.millis())));
        }
        if (settings().broadcastRecords() && (previousRecord == null || millis < previousRecord.millis())
                && data.rank(player.getUniqueId()) == 1) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(online, "parkour.record", MessageService.p("player", player.getName()), MessageService.p("time", time));
            }
        }
        refreshBoard();
    }

    private void commands(List<String> commands, Player player, String time, long millis) {
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{time}", time)
                    .replace("{millis}", String.valueOf(millis)));
        }
    }

    /**
     * Fell below the course: back to the last checkpoint.
     *
     * @param player player
     * @return where the runner goes (the move event's new destination)
     */
    public Location fall(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return player.getLocation();
        }
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector());
        play(player, settings().failSound(), 1f);
        return respawn(run.checkpoint());
    }

    /** Where a runner goes back to: on the plate of the last checkpoint, facing the next one. */
    private Location respawn(int checkpoint) {
        LobbyLayout.Parkour course = course();
        BlockPos plate = checkpoint < 0 ? course.start() : course.checkpoints().get(checkpoint);
        BlockPos next = checkpoint + 1 < course.checkpoints().size() ? course.checkpoints().get(checkpoint + 1) : course.finish();
        float yaw = (float) Math.toDegrees(Math.atan2(-(next.x() - plate.x()), next.z() - plate.z()));
        return new Point(plate.x() + 0.5, plate.y(), plate.z() + 0.5, yaw, 0).at(plugin.lobbyWorld().world());
    }

    /**
     * Hotbar "last checkpoint".
     *
     * @param player player
     */
    public void toCheckpoint(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run != null) {
            player.teleport(respawn(run.checkpoint()));
        }
    }

    /**
     * Hotbar "restart": back to the start plate with a fresh timer.
     *
     * @param player player
     */
    public void restart(Player player) {
        if (runs.containsKey(player.getUniqueId())) {
            player.teleport(respawn(-1));
            runs.put(player.getUniqueId(), new Run(System.currentTimeMillis(), -1));
            plugin.messages().actionBar(player, "parkour.restarted");
        }
    }

    /**
     * Hotbar "leave" (and quitting, teleports, state changes): ends the run without a time.
     *
     * @param player player
     * @param notify tell the player
     */
    public void cancel(Player player, boolean notify) {
        if (runs.containsKey(player.getUniqueId())) {
            end(player);
            if (notify) {
                plugin.messages().send(player, "parkour.cancelled");
            }
        }
    }

    private void end(Player player) {
        runs.remove(player.getUniqueId());
        if (player.isOnline()) {
            plugin.lobby().applyFlight(player);
            if (plugin.api().states().is(player, net.pvpserver.core.state.PlayerState.LOBBY, net.pvpserver.core.state.PlayerState.QUEUE)) {
                plugin.hotbar().give(player);
            }
        }
    }

    /** Timer in the action bar, and the time limit. Runs every few ticks while anyone is running. */
    public void tick() {
        if (runs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long limit = settings().maxMinutes() * 60_000L;
        int total = course() == null ? 0 : course().checkpoints().size();
        for (Map.Entry<UUID, Run> entry : runs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                runs.remove(entry.getKey());
                continue;
            }
            long elapsed = now - entry.getValue().started();
            if (elapsed > limit) {
                cancel(player, false);
                plugin.messages().send(player, "parkour.too-slow");
                continue;
            }
            plugin.messages().actionBar(player, "parkour.timer", MessageService.p("time", TimeUtil.formatMillis(elapsed)),
                    MessageService.p("checkpoint", entry.getValue().checkpoint() + 1), MessageService.p("total", total));
        }
    }

    /** Re-renders the best times hologram. */
    public void refreshBoard() {
        Component text = board();
        if (displays.textDisplay(BOARD) != null) {
            displays.update(BOARD, text);
        }
    }

    /** Spawns the best times hologram at its layout position. */
    public void spawnBoard() {
        Point at = plugin.lobbyWorld().layout().holograms().get(BOARD);
        if (at != null && course() != null) {
            displays.text(BOARD, at.at(plugin.lobbyWorld().world()), Display.Billboard.CENTER, 1f, 0, board());
        }
    }

    private Component board() {
        MessageService m = plugin.messages();
        Component text = m.get("parkour.board.title");
        List<LobbyData.Time> top = data.top(settings().leaderboardSize());
        int position = 1;
        for (LobbyData.Time time : top) {
            text = text.append(Component.newline()).append(m.get("parkour.board.line", MessageService.p("position", position++),
                    MessageService.p("player", time.name()), MessageService.p("time", TimeUtil.formatMillis(time.millis()))));
        }
        if (top.isEmpty()) {
            text = text.append(Component.newline()).append(m.get("parkour.board.empty"));
        }
        return text.append(Component.newline()).append(m.get("parkour.board.footer"));
    }

    /**
     * @param player player
     * @return formatted current run time, or "-"
     */
    public String currentTime(Player player) {
        Run run = runs.get(player.getUniqueId());
        return run == null ? "-" : TimeUtil.formatMillis(System.currentTimeMillis() - run.started());
    }

    /**
     * @param player player
     * @return formatted best time, or "-"
     */
    public String bestTime(Player player) {
        LobbyData.Time best = data.best(player.getUniqueId());
        return best == null ? "-" : TimeUtil.formatMillis(best.millis());
    }

    private static void play(Player player, String sound, float pitch) {
        if (!sound.isEmpty()) {
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 1f, pitch));
        }
    }

    /** Ends every run (reload, disable). */
    public void cancelAll() {
        for (UUID uuid : List.copyOf(runs.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                cancel(player, true);
            } else {
                runs.remove(uuid);
            }
        }
    }
}
