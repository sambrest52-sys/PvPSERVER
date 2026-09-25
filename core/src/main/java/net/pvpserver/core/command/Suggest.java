package net.pvpserver.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Tab completion helpers.
 */
public final class Suggest {

    private Suggest() {
    }

    /**
     * @param options candidates
     * @param prefix typed prefix
     * @return candidates starting with the prefix (case-insensitive), max 100
     */
    public static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
                if (result.size() >= 100) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Online player names the sender can see (vanished staff are hidden from non-staff).
     *
     * @param sender sender
     * @return names
     */
    public static List<String> players(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player viewer) || viewer.canSee(player)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    /** @return common duration examples */
    public static List<String> durations() {
        return List.of("30m", "1h", "6h", "1d", "7d", "30d", "perm");
    }
}
