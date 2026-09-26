package net.pvpserver.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.scoreboard.SidebarProvider;
import net.pvpserver.core.scoreboard.SidebarTemplate;
import net.pvpserver.core.util.TimeUtil;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sidebars for LOBBY, QUEUE and EDITING states, driven by scoreboard.yml.
 */
public final class LobbyScoreboard implements SidebarProvider {

    private final PracticeApi api;
    private final MessageService messages;
    private final ConfigFile file;
    private final String section;
    private final Extras extras;
    private SidebarTemplate template;

    /** Extra placeholders and flags provided by lobby features (zone, parkour, eggs). */
    public interface Extras {
        /**
         * @param player player
         * @param flags flags to add to
         * @param resolvers placeholders to add to
         */
        void apply(Player player, Set<String> flags, List<TagResolver> resolvers);
    }

    /**
     * @param api practice api
     * @param messages lobby messages
     * @param file scoreboard.yml
     * @param section template section (lobby, queue, editing)
     */
    public LobbyScoreboard(PracticeApi api, MessageService messages, ConfigFile file, String section) {
        this(api, messages, file, section, (player, flags, resolvers) -> {
        });
    }

    /**
     * @param api practice api
     * @param messages lobby messages
     * @param file scoreboard.yml
     * @param section template section (lobby, queue, editing)
     * @param extras extra placeholders and flags
     */
    public LobbyScoreboard(PracticeApi api, MessageService messages, ConfigFile file, String section, Extras extras) {
        this.api = api;
        this.messages = messages;
        this.file = file;
        this.section = section;
        this.extras = extras;
        reload();
    }

    /** Re-reads the template. */
    public void reload() {
        template = SidebarTemplate.of(file.get().getConfigurationSection(section));
    }

    private TagResolver resolvers(Player player, Set<String> flags) {
        PlayerProfile profile = api.profiles().get(player);
        Optional<Party> party = api.parties().partyOf(player);
        party.ifPresent(p -> flags.add("party"));
        TagResolver.Builder builder = TagResolver.builder().resolvers(
                MessageService.p("online", api.counts().online()),
                MessageService.p("in_fight", api.counts().fighting()),
                MessageService.p("in_queue", api.counts().queued()),
                MessageService.p("in_ffa", api.counts().ffa()),
                MessageService.p("ping", player.getPing()),
                MessageService.p("player", player.getName()),
                MessageService.p("elo", profile == null ? api.stats().startingElo() : profile.globalElo()),
                MessageService.p("rank", api.ranks().rankOf(player).displayName()),
                MessageService.p("party_size", party.map(Party::size).orElse(0)),
                MessageService.p("party_max", party.map(Party::maxSize).orElse(0)),
                MessageService.p("party_leader", party.map(p -> nameOf(p)).orElse("-")));
        QueueBridge.QueueInfo info = api.bridges().get(QueueBridge.class).map(q -> q.info(player.getUniqueId())).orElse(null);
        if (info != null) {
            flags.add("queued");
            if (info.ranked()) {
                flags.add("ranked");
            }
            builder.resolvers(MessageService.c("queue_kit", messages.parse(info.kit())),
                    MessageService.p("queue_type", info.ranked() ? "Ranked" : "Unranked"),
                    MessageService.p("queue_mode", info.mode()),
                    MessageService.p("queue_time", TimeUtil.formatClock(info.waitedMillis())),
                    MessageService.p("queue_min", info.minElo()),
                    MessageService.p("queue_max", info.maxElo()));
        } else {
            builder.resolvers(MessageService.p("queue_kit", "-"), MessageService.p("queue_type", "-"), MessageService.p("queue_mode", "-"),
                    MessageService.p("queue_time", "00:00"), MessageService.p("queue_min", 0), MessageService.p("queue_max", 0));
        }
        List<TagResolver> more = new java.util.ArrayList<>();
        extras.apply(player, flags, more);
        builder.resolvers(more);
        return builder.build();
    }

    private String nameOf(Party party) {
        Player leader = org.bukkit.Bukkit.getPlayer(party.leader());
        return leader == null ? "?" : leader.getName();
    }

    @Override
    public Component title(Player player) {
        return template.title(messages, resolvers(player, new HashSet<>()));
    }

    @Override
    public List<Component> lines(Player player) {
        Set<String> flags = new HashSet<>();
        TagResolver resolver = resolvers(player, flags);
        return template.lines(messages, flags, resolver);
    }
}
