package net.pvpserver.lobby.feature;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the actions NPCs, portals and buttons are configured with:
 * <ul>
 *   <li>any hotbar action: queue-ranked, queue-unranked, ffa, kit-editor, stats, leaderboards, cosmetics, settings,
 *       spectate, party-create, party-fight, party-info, party-leave</li>
 *   <li>{@code ffa:<arena>} join an FFA arena directly</li>
 *   <li>{@code parkour} teleport to the parkour start, {@code spawn} teleport to spawn</li>
 *   <li>{@code warp:<point>} teleport to an NPC or hologram point in layout.yml</li>
 *   <li>{@code message:<key>} send a messages.yml message</li>
 *   <li>{@code command:<command>} run a command as the player</li>
 *   <li>{@code console:<command>} run a command as the console ({@code <player>} is replaced)</li>
 * </ul>
 */
public final class LobbyActions {

    private final PvPLobby plugin;
    private final PracticeApi api;
    private final Set<String> warned = new HashSet<>();

    /**
     * @param plugin lobby plugin
     */
    public LobbyActions(PvPLobby plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
    }

    /**
     * @param player player
     * @param action action
     */
    public void run(Player player, String action) {
        String trimmed = action.trim();
        int colon = trimmed.indexOf(':');
        String type = (colon < 0 ? trimmed : trimmed.substring(0, colon)).toLowerCase(Locale.ROOT);
        String argument = colon < 0 ? "" : trimmed.substring(colon + 1).trim();
        LobbyLayout layout = plugin.lobbyWorld().layout();
        switch (type) {
            case "ffa" -> {
                if (argument.isEmpty()) {
                    hotbar(player, "ffa");
                } else {
                    player.performCommand("ffa " + argument);
                }
            }
            case "parkour" -> {
                if (layout.parkour() == null) {
                    plugin.messages().send(player, "parkour.none");
                } else {
                    Point start = layout.parkour().start().top().add(0, -1, 0);
                    player.teleport(start.facing(player.getLocation().getYaw(), 0).at(plugin.lobbyWorld().world()));
                }
            }
            case "spawn" -> player.teleport(plugin.lobbyWorld().spawn());
            case "warp" -> {
                Point point = layout.npcs().getOrDefault(argument, layout.holograms().get(argument));
                if (point == null) {
                    warn(action, "no npc or hologram point named " + argument);
                } else {
                    player.teleport(point.at(plugin.lobbyWorld().world()));
                }
            }
            case "message" -> plugin.messages().send(player, argument);
            case "command" -> player.performCommand(argument);
            case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), argument.replace("<player>", player.getName()));
            default -> hotbar(player, trimmed);
        }
    }

    private void hotbar(Player player, String id) {
        if (!api.hotbar().run(player, id)) {
            warn(id, "unknown action");
            api.messages().send(player, "general.feature-unavailable");
        }
    }

    private void warn(String action, String reason) {
        if (warned.add(action)) {
            plugin.getLogger().warning("Lobby action \"" + action + "\": " + reason);
        }
    }

    /**
     * @param action action
     * @return whether it can run (for /lobby info and config checks)
     */
    public boolean known(String action) {
        String type = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        return Set.of("ffa", "parkour", "spawn", "warp", "message", "command", "console").contains(type) || api.hotbar().has(action);
    }
}
