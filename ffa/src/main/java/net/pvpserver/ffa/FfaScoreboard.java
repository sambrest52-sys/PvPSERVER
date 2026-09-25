package net.pvpserver.ffa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.scoreboard.SidebarProvider;
import net.pvpserver.core.scoreboard.SidebarTemplate;
import net.pvpserver.core.stats.KitStats;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FFA sidebar from scoreboard.yml.
 */
public final class FfaScoreboard implements SidebarProvider {

    private final PvPFFA plugin;
    private final ConfigFile file;
    private SidebarTemplate template;

    /**
     * @param plugin FFA plugin
     * @param file scoreboard.yml
     */
    public FfaScoreboard(PvPFFA plugin, ConfigFile file) {
        this.plugin = plugin;
        this.file = file;
        reload();
    }

    /** Re-reads the template. */
    public void reload() {
        template = SidebarTemplate.of(file.get().getConfigurationSection("ffa"));
    }

    private TagResolver resolvers(Player player, Set<String> flags) {
        FfaArena arena = plugin.ffa().arenaOf(player);
        FfaStats session = plugin.ffa().stats(player);
        PlayerProfile profile = plugin.api().profiles().get(player);
        KitStats kitStats = profile == null || arena == null ? null : profile.stats(arena.kit().id());
        long tag = plugin.api().combat().tags().remaining(player);
        if (tag > 0) {
            flags.add("tagged");
        }
        if (arena != null && arena.ranked()) {
            flags.add("ranked");
        }
        return TagResolver.resolver(
                MessageService.p("arena", arena == null ? "-" : MessageService.mini().stripTags(arena.displayName())),
                MessageService.p("players", arena == null ? 0 : arena.players().size()),
                MessageService.p("kills", session.kills()),
                MessageService.p("deaths", session.deaths()),
                MessageService.p("streak", session.streak()),
                MessageService.p("total_kills", kitStats == null ? 0 : kitStats.ffaKills()),
                MessageService.p("kdr", kitStats == null ? "0.00" : kitStats.kdr()),
                MessageService.p("rating", kitStats == null ? 0 : kitStats.ffaElo()),
                MessageService.p("best_streak", kitStats == null ? 0 : kitStats.ffaBestStreak()),
                MessageService.p("combat", String.format(Locale.ROOT, "%.1f", tag / 1000.0)),
                MessageService.p("ping", player.getPing()));
    }

    @Override
    public Component title(Player player) {
        return template.title(plugin.messages(), resolvers(player, new HashSet<>()));
    }

    @Override
    public List<Component> lines(Player player) {
        Set<String> flags = new HashSet<>();
        TagResolver resolver = resolvers(player, flags);
        return template.lines(plugin.messages(), flags, resolver);
    }
}
