package net.pvpserver.core.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.Counts;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.moderation.StaffService;
import net.pvpserver.core.rank.RankService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Tab list header/footer (with live counts and ping) plus rank-prefixed, rank-ordered player list names.
 */
public final class TabService implements Listener {

    private final MessageService messages;
    private final RankService ranks;
    private final Counts counts;
    private StaffService staff;
    private List<String> header = List.of();
    private List<String> footer = List.of();
    private String nameFormat = "<prefix><name>";
    private boolean enabled = true;

    /**
     * @param messages messages
     * @param ranks ranks
     * @param counts counters
     */
    public TabService(MessageService messages, RankService ranks, Counts counts) {
        this.messages = messages;
        this.ranks = ranks;
        this.counts = counts;
    }

    /**
     * @param staff staff service (vanish markers)
     */
    public void staff(StaffService staff) {
        this.staff = staff;
    }

    /**
     * @param section {@code tab} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        header = section.getStringList("header");
        footer = section.getStringList("footer");
        nameFormat = section.getString("name-format", "<prefix><name>");
    }

    /**
     * @param intervalTicks header/footer refresh period
     */
    public void start(int intervalTicks) {
        Tasks.timer(this::refreshAll, 40L, Math.max(20, intervalTicks));
    }

    private void refreshAll() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendHeaderFooter(player);
        }
    }

    private void sendHeaderFooter(Player player) {
        TagResolver resolver = TagResolver.resolver(
                MessageService.p("online", counts.online()),
                MessageService.p("max", Bukkit.getMaxPlayers()),
                MessageService.p("ping", player.getPing()),
                MessageService.p("tps", counts.tpsFormatted()),
                MessageService.p("in_queue", counts.queued()),
                MessageService.p("in_fight", counts.fighting()),
                MessageService.p("in_ffa", counts.ffa()));
        player.sendPlayerListHeaderAndFooter(join(header, resolver), join(footer, resolver));
    }

    private Component join(List<String> lines, TagResolver resolver) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(messages.parse(lines.get(i), resolver));
        }
        return result;
    }

    /**
     * Updates the player's list name and order (call after rank changes and vanish toggles).
     *
     * @param player player
     */
    public void updateName(Player player) {
        if (!enabled) {
            return;
        }
        Component vanished = staff != null && staff.isVanished(player) ? messages.get("staff.vanish-tab-marker") : Component.empty();
        player.playerListName(vanished.append(messages.parse(nameFormat,
                MessageService.c("prefix", ranks.prefix(player)),
                MessageService.c("name", ranks.coloredName(player)))));
        player.setPlayerListOrder(Math.max(0, ranks.weight(player)));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        updateName(event.getPlayer());
        if (enabled) {
            sendHeaderFooter(event.getPlayer());
        }
    }
}
