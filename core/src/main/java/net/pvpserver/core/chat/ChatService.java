package net.pvpserver.core.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.moderation.Punishment;
import net.pvpserver.core.moderation.PunishmentService;
import net.pvpserver.core.moderation.StaffService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.rank.RankService;
import net.pvpserver.core.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formatted chat, filter, slow mode, global mute, ignore lists, private messages and social spy.
 */
public final class ChatService implements Listener {

    private static final MiniMessage PLAYER_FORMATTING = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow()))
            .build();

    private final MessageService messages;
    private final ProfileService profiles;
    private final RankService ranks;
    private final PunishmentService punishments;
    private final StaffService staff;
    private final ChatFilter filter = new ChatFilter();
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();
    private volatile String format = "<prefix><name><gray>: <white><message>";
    private volatile int slowSeconds;
    private volatile boolean globalMute;
    private volatile boolean blockDuplicates = true;

    /**
     * @param messages messages
     * @param profiles profiles
     * @param ranks ranks
     * @param punishments mutes
     * @param staff staff chat
     */
    public ChatService(MessageService messages, ProfileService profiles, RankService ranks, PunishmentService punishments, StaffService staff) {
        this.messages = messages;
        this.profiles = profiles;
        this.ranks = ranks;
        this.punishments = punishments;
        this.staff = staff;
    }

    /**
     * @param section {@code chat} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        format = section.getString("format", format);
        slowSeconds = section.getInt("slow-mode-seconds", 0);
        blockDuplicates = section.getBoolean("block-duplicates", true);
        ConfigurationSection f = section.getConfigurationSection("filter");
        if (f != null) {
            filter.configure(f.getStringList("words"), f.getString("action", "CENSOR").equalsIgnoreCase("BLOCK"),
                    f.getBoolean("block-links", true), f.getString("replacement", "***"));
        }
    }

    /**
     * @param seconds slow mode seconds (0 = off)
     */
    public void slowMode(int seconds) {
        this.slowSeconds = Math.max(0, seconds);
    }

    /** @return slow mode seconds */
    public int slowMode() {
        return slowSeconds;
    }

    /**
     * @param muted whether only staff may talk
     */
    public void globalMute(boolean muted) {
        this.globalMute = muted;
    }

    /** @return whether chat is globally muted */
    public boolean globalMute() {
        return globalMute;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (staff.inStaffChat(player) && player.hasPermission("pvp.staff.chat")) {
            event.setCancelled(true);
            staff.staffChat(player.getName(), raw);
            return;
        }
        Optional<Punishment> mute = punishments.activeMute(player);
        if (mute.isPresent()) {
            event.setCancelled(true);
            messages.send(player, "moderation.muted-chat", punishments.resolvers(mute.get()));
            return;
        }
        boolean bypass = player.hasPermission("pvp.chat.bypass");
        if (globalMute && !bypass) {
            event.setCancelled(true);
            messages.send(player, "chat.global-muted");
            return;
        }
        long now = System.currentTimeMillis();
        if (slowSeconds > 0 && !bypass) {
            Long last = lastMessage.get(player.getUniqueId());
            if (last != null && now - last < slowSeconds * 1000L) {
                event.setCancelled(true);
                messages.send(player, "chat.slow-mode", MessageService.p("seconds", TimeUtil.formatDuration(slowSeconds * 1000L - (now - last))));
                return;
            }
        }
        if (blockDuplicates && !bypass && raw.equalsIgnoreCase(lastText.get(player.getUniqueId()))
                && now - lastMessage.getOrDefault(player.getUniqueId(), 0L) < 5000) {
            event.setCancelled(true);
            messages.send(player, "chat.duplicate");
            return;
        }
        ChatFilter.Result result = bypass ? new ChatFilter.Result(false, raw, null) : filter.apply(raw);
        if (result.blocked()) {
            event.setCancelled(true);
            messages.send(player, "chat.filtered");
            staff.alert("chat.filter-alert", MessageService.p("player", player.getName()), MessageService.p("message", raw));
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        lastText.put(player.getUniqueId(), raw);

        Component body = player.hasPermission("pvp.chat.color")
                ? PLAYER_FORMATTING.deserialize(result.message())
                : Component.text(result.message());
        // Viewers who ignore the sender do not receive the message.
        event.viewers().removeIf(audience -> audience instanceof Player viewer && ignores(viewer, player));
        Component prefix = ranks.prefix(player);
        Component name = ranks.coloredName(player);
        Component formatted = messages.parse(format, MessageService.c("prefix", prefix), MessageService.c("name", name),
                MessageService.c("message", body));
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> formatted));
    }

    /**
     * @param viewer viewer
     * @param sender sender
     * @return whether the viewer ignores the sender
     */
    public boolean ignores(Player viewer, Player sender) {
        PlayerProfile profile = profiles.get(viewer);
        return profile != null && profile.ignored().contains(sender.getUniqueId()) && !sender.hasPermission("pvp.staff");
    }

    /**
     * Sends a private message, honouring settings, ignores and mutes.
     *
     * @param from sender
     * @param to recipient
     * @param text message
     */
    public void privateMessage(Player from, Player to, String text) {
        if (from == to) {
            messages.send(from, "chat.msg-self");
            return;
        }
        Optional<Punishment> mute = punishments.activeMute(from);
        if (mute.isPresent()) {
            messages.send(from, "moderation.muted-chat", punishments.resolvers(mute.get()));
            return;
        }
        PlayerProfile target = profiles.get(to);
        boolean staffSender = from.hasPermission("pvp.staff");
        if (!staffSender && target != null && (!target.settings().is(Setting.PRIVATE_MESSAGES) || target.ignored().contains(from.getUniqueId()))) {
            messages.send(from, "chat.msg-disabled", MessageService.p("player", to.getName()));
            return;
        }
        if (!staffSender && !from.canSee(to)) {
            messages.send(from, "command.player-not-found", MessageService.p("player", to.getName()));
            return;
        }
        ChatFilter.Result result = from.hasPermission("pvp.chat.bypass") ? new ChatFilter.Result(false, text, null) : filter.apply(text);
        if (result.blocked()) {
            messages.send(from, "chat.filtered");
            return;
        }
        messages.send(from, "chat.msg-to", MessageService.p("player", to.getName()), MessageService.p("message", result.message()));
        messages.send(to, "chat.msg-from", MessageService.p("player", from.getName()), MessageService.p("message", result.message()));
        PlayerProfile sender = profiles.get(from);
        if (sender != null) {
            sender.lastMessaged(to.getUniqueId());
        }
        if (target != null) {
            target.lastMessaged(from.getUniqueId());
        }
        Component spy = messages.get("chat.social-spy", MessageService.p("from", from.getName()), MessageService.p("to", to.getName()),
                MessageService.p("message", result.message()));
        for (UUID spyId : socialSpy) {
            Player spyPlayer = Bukkit.getPlayer(spyId);
            if (spyPlayer != null && spyPlayer != from && spyPlayer != to) {
                spyPlayer.sendMessage(spy);
            }
        }
    }

    /**
     * @param player staff member
     * @return new social spy state
     */
    public boolean toggleSocialSpy(Player player) {
        if (socialSpy.remove(player.getUniqueId())) {
            return false;
        }
        socialSpy.add(player.getUniqueId());
        return true;
    }

    /**
     * Clears chat for everyone without the bypass permission.
     *
     * @param by who cleared it
     */
    public void clearChat(String by) {
        Component blank = Component.text(" ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("pvp.chat.bypass")) {
                for (int i = 0; i < 100; i++) {
                    player.sendMessage(blank);
                }
            }
        }
        Audience everyone = Audience.audience(Bukkit.getOnlinePlayers());
        messages.send(everyone, "chat.cleared", MessageService.p("staff", by));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastMessage.remove(id);
        lastText.remove(id);
        socialSpy.remove(id);
    }
}
