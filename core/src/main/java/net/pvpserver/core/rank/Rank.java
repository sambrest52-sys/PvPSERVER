package net.pvpserver.core.rank;

import java.util.List;

/**
 * Built-in rank from ranks.yml.
 *
 * @param id identifier
 * @param displayName name shown in menus
 * @param prefix MiniMessage prefix for chat and tab
 * @param nameColor MiniMessage colour tag applied to names (e.g. {@code <green>})
 * @param weight sort weight, higher is more important (tab order)
 * @param permissions granted permissions ({@code *} and {@code node.*} wildcards expand to registered permissions)
 * @param inherits parent rank id or null
 * @param staff whether the rank counts as staff (staff chat, alerts)
 */
public record Rank(String id, String displayName, String prefix, String nameColor, int weight, List<String> permissions,
                   String inherits, boolean staff) {
}
