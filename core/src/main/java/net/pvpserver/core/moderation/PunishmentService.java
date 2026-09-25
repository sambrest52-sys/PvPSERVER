package net.pvpserver.core.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.storage.repository.PunishmentRepository;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Issues and enforces bans, mutes, kicks and warnings. Bans are checked in pre-login (async); mutes are cached per
 * online player for the chat hot path.
 */
public final class PunishmentService implements Listener {

    private final PunishmentRepository repository;
    private final MessageService messages;
    private final StaffService staff;
    private final Logger logger;
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();
    private boolean publicBroadcast;

    /**
     * @param repository storage
     * @param messages messages
     * @param staff staff alerts
     * @param logger logger
     */
    public PunishmentService(PunishmentRepository repository, MessageService messages, StaffService staff, Logger logger) {
        this.repository = repository;
        this.messages = messages;
        this.staff = staff;
        this.logger = logger;
    }

    /**
     * @param publicBroadcast whether punishments are announced to everyone (otherwise staff only)
     */
    public void configure(boolean publicBroadcast) {
        this.publicBroadcast = publicBroadcast;
    }

    /**
     * Issues a punishment.
     *
     * @param issuer staff member or console
     * @param target target id
     * @param targetName target name
     * @param type type
     * @param durationMillis duration; {@link Long#MAX_VALUE} or {@link Punishment#PERMANENT} for permanent
     * @param reason reason
     * @param silent only notify staff
     * @return stored punishment
     */
    public CompletableFuture<Punishment> punish(CommandSender issuer, UUID target, String targetName, PunishmentType type,
                                                long durationMillis, String reason, boolean silent) {
        long now = System.currentTimeMillis();
        long expires;
        if (!type.lasting()) {
            expires = 0;
        } else if (durationMillis == Long.MAX_VALUE || durationMillis == Punishment.PERMANENT) {
            expires = Punishment.PERMANENT;
        } else {
            expires = now + durationMillis;
        }
        UUID issuerId = issuer instanceof Player player ? player.getUniqueId() : null;
        Punishment punishment = new Punishment(0, target, targetName, type, reason, issuerId, issuer.getName(), now, expires,
                type.lasting(), null, 0, null);
        CompletableFuture<Punishment> result = new CompletableFuture<>();
        repository.insert(punishment).whenComplete((saved, error) -> Tasks.sync(() -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not store punishment", error);
                messages.send(issuer, "command.error");
                result.completeExceptionally(error);
                return;
            }
            enforce(saved);
            announce(saved, silent);
            result.complete(saved);
        }));
        return result;
    }

    private void enforce(Punishment p) {
        Player online = Bukkit.getPlayer(p.target());
        if (online == null) {
            return;
        }
        switch (p.type()) {
            case BAN -> online.kick(screen("moderation.ban-screen", p));
            case KICK -> online.kick(screen("moderation.kick-screen", p));
            case MUTE -> {
                mutes.put(p.target(), p);
                messages.send(online, "moderation.muted-notify", resolvers(p));
            }
            case WARN -> {
                messages.send(online, "moderation.warned-notify", resolvers(p));
                messages.title(online, "moderation.warned-title", 5, 60, 10, resolvers(p));
            }
        }
    }

    private void announce(Punishment p, boolean silent) {
        String key = "moderation.announce." + p.type().name().toLowerCase(java.util.Locale.ROOT)
                + (p.type().lasting() && !p.permanent() ? "-temp" : "");
        TagResolver[] resolvers = resolvers(p);
        if (publicBroadcast && !silent) {
            Component message = messages.get(key, resolvers);
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
            Bukkit.getConsoleSender().sendMessage(message);
        } else {
            staff.alert(messages.get("moderation.silent-prefix").append(messages.get(key, resolvers)));
        }
    }

    /**
     * Removes active punishments of a type.
     *
     * @param issuer remover
     * @param target target
     * @param targetName name
     * @param type type (BAN or MUTE)
     * @param reason reason
     * @return rows changed
     */
    public CompletableFuture<Integer> pardon(CommandSender issuer, UUID target, String targetName, PunishmentType type, String reason) {
        return repository.deactivate(target, type, issuer.getName(), reason).whenComplete((count, error) -> Tasks.sync(() -> {
            if (error != null) {
                messages.send(issuer, "command.error");
                return;
            }
            if (type == PunishmentType.MUTE) {
                mutes.remove(target);
                Player online = Bukkit.getPlayer(target);
                if (online != null && count > 0) {
                    messages.send(online, "moderation.unmuted-notify");
                }
            }
            String key = count > 0 ? "moderation.pardoned" : "moderation.nothing-to-pardon";
            staff.alert(key, MessageService.p("player", targetName), MessageService.p("staff", issuer.getName()),
                    MessageService.p("type", type.name().toLowerCase(java.util.Locale.ROOT)));
            if (!(issuer instanceof Player player && player.hasPermission(StaffService.ALERTS))) {
                messages.send(issuer, key, MessageService.p("player", targetName), MessageService.p("staff", issuer.getName()),
                        MessageService.p("type", type.name().toLowerCase(java.util.Locale.ROOT)));
            }
        }));
    }

    /**
     * @param target player
     * @param limit rows
     * @return history
     */
    public CompletableFuture<List<Punishment>> history(UUID target, int limit) {
        return repository.history(target, limit);
    }

    /**
     * @param player player
     * @return active mute if any
     */
    public Optional<Punishment> activeMute(Player player) {
        Punishment mute = mutes.get(player.getUniqueId());
        if (mute != null && !mute.inForce(System.currentTimeMillis())) {
            mutes.remove(player.getUniqueId());
            return Optional.empty();
        }
        return Optional.ofNullable(mute);
    }

    /**
     * @param p punishment
     * @return placeholders: player, staff, reason, duration, expires, type, id
     */
    public TagResolver[] resolvers(Punishment p) {
        String duration = !p.type().lasting() ? "-" : p.permanent() ? "permanent" : TimeUtil.formatDuration(p.expiresAt() - p.createdAt());
        String remaining = !p.type().lasting() ? "-" : p.permanent() ? "never" : TimeUtil.formatDuration(p.expiresAt() - System.currentTimeMillis());
        return new TagResolver[]{MessageService.p("player", p.targetName()), MessageService.p("staff", p.issuerName()),
                MessageService.p("reason", p.reason()), MessageService.p("duration", duration), MessageService.p("expires", remaining),
                MessageService.p("type", p.type().name()), MessageService.p("id", p.id())};
    }

    private Component screen(String key, Punishment p) {
        List<Component> lines = messages.getList(key, resolvers(p));
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(i));
        }
        return result;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            Optional<Punishment> ban = repository.findActiveBlocking(event.getUniqueId(), PunishmentType.BAN, now);
            if (ban.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen("moderation.ban-screen", ban.get()));
                return;
            }
            repository.findActiveBlocking(event.getUniqueId(), PunishmentType.MUTE, now)
                    .ifPresent(mute -> mutes.put(event.getUniqueId(), mute));
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Punishment lookup failed for " + event.getName(), e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mutes.remove(event.getPlayer().getUniqueId());
    }
}
