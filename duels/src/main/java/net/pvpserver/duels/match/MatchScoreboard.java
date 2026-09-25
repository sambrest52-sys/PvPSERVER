package net.pvpserver.duels.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.scoreboard.SidebarProvider;
import net.pvpserver.core.scoreboard.SidebarTemplate;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.duels.PvPDuels;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sidebars for MATCH and SPECTATING states from scoreboard.yml ({@code match} and {@code spectating} sections).
 */
public final class MatchScoreboard implements SidebarProvider {

    private final PvPDuels plugin;
    private final ConfigFile file;
    private final boolean spectator;
    private SidebarTemplate template;

    /**
     * @param plugin duels plugin
     * @param file scoreboard.yml
     * @param spectator whether this provider is for spectators
     */
    public MatchScoreboard(PvPDuels plugin, ConfigFile file, boolean spectator) {
        this.plugin = plugin;
        this.file = file;
        this.spectator = spectator;
        reload();
    }

    /** Re-reads the template. */
    public void reload() {
        template = SidebarTemplate.of(file.get().getConfigurationSection(spectator ? "spectating" : "match"));
    }

    private Match match(Player player) {
        return spectator ? plugin.spectators().spectatedMatch(player) : plugin.matches().matchOf(player.getUniqueId());
    }

    private TagResolver resolvers(Player player, Match match, Set<String> flags) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolvers(MessageService.p("ping", player.getPing()), MessageService.p("cps", plugin.api().checks().cps(player)));
        if (match == null) {
            flags.add("waiting");
            return builder.resolvers(MessageService.p("opponent", "-"), MessageService.p("opponent_ping", 0),
                    MessageService.p("duration", "00:00"), MessageService.p("kit", "-"), MessageService.p("match", "-"),
                    MessageService.p("hits", 0), MessageService.p("opponent_hits", 0), MessageService.p("combo", 0),
                    MessageService.p("score", "-"), MessageService.p("round", 1), MessageService.p("spectators", 0),
                    MessageService.p("team_alive", 0), MessageService.p("enemy_alive", 0), MessageService.p("hits_needed", 0)).build();
        }
        MatchParticipant self = match.participant(player.getUniqueId());
        List<MatchParticipant> opponents = self == null ? List.of() : match.opponentsOf(self.uuid());
        MatchParticipant opponent = opponents.size() == 1 ? opponents.get(0) : null;
        Player opponentPlayer = opponent == null ? null : Bukkit.getPlayer(opponent.uuid());
        if (match.state() == MatchState.STARTING) {
            flags.add("waiting");
        }
        if (opponent != null) {
            flags.add("duel");
        } else {
            flags.add("team");
        }
        if (match.kit().rules().boxing()) {
            flags.add("boxing");
        }
        if (match.kit().rules().bridge()) {
            flags.add("bridge");
        }
        if (match.roundsToWin() > 1) {
            flags.add("rounds");
        }
        if (match.ranked()) {
            flags.add("ranked");
        }
        int teamPoints = self == null ? 0 : self.team().points();
        int enemyPoints = opponents.stream().map(MatchParticipant::team).distinct().mapToInt(MatchTeam::points).max().orElse(0);
        String score = String.join(" - ", match.teams().stream()
                .map(t -> String.valueOf(match.kit().rules().bridge() ? t.points() : t.roundsWon())).toList());
        builder.resolvers(
                MessageService.p("opponent", opponent == null ? "-" : opponent.name()),
                MessageService.p("opponent_ping", opponentPlayer == null ? 0 : opponentPlayer.getPing()),
                MessageService.p("duration", TimeUtil.formatClock(match.durationMillis())),
                MessageService.c("kit", match.kit().name()),
                MessageService.p("match", match.description()),
                MessageService.p("hits", match.kit().rules().boxing() ? teamPoints : self == null ? 0 : self.hits()),
                MessageService.p("opponent_hits", match.kit().rules().boxing() ? enemyPoints : opponent == null ? 0 : opponent.hits()),
                MessageService.p("hits_needed", match.kit().rules().boxingHits()),
                MessageService.p("combo", self == null ? 0 : self.currentCombo()),
                MessageService.p("score", score),
                MessageService.p("round", match.round()),
                MessageService.p("spectators", match.spectators().size()),
                MessageService.p("team_alive", self == null ? 0 : self.team().aliveCount()),
                MessageService.p("enemy_alive", opponents.stream().filter(MatchParticipant::alive).count()));
        return builder.build();
    }

    @Override
    public Component title(Player player) {
        return template.title(plugin.messages(), resolvers(player, match(player), new HashSet<>()));
    }

    @Override
    public List<Component> lines(Player player) {
        Set<String> flags = new HashSet<>();
        TagResolver resolver = resolvers(player, match(player), flags);
        return template.lines(plugin.messages(), flags, resolver);
    }
}
