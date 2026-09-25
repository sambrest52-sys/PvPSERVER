package net.pvpserver.core.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.storage.repository.ReportRepository;
import net.pvpserver.core.util.Tasks;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player reports with a per-reporter cooldown and clickable staff alerts.
 */
public final class ReportService {

    private final ReportRepository repository;
    private final MessageService messages;
    private final StaffService staff;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private long cooldownMillis = 60_000;

    /**
     * @param repository storage
     * @param messages messages
     * @param staff staff alerts
     */
    public ReportService(ReportRepository repository, MessageService messages, StaffService staff) {
        this.repository = repository;
        this.messages = messages;
        this.staff = staff;
    }

    /**
     * @param cooldownSeconds cooldown between reports per player
     */
    public void configure(int cooldownSeconds) {
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    /**
     * Files a report.
     *
     * @param reporter reporter
     * @param target reported player
     * @param reason reason
     */
    public void report(Player reporter, Player target, String reason) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(reporter.getUniqueId());
        if (last != null && now - last < cooldownMillis && !reporter.hasPermission("pvp.staff")) {
            messages.send(reporter, "report.cooldown", MessageService.p("seconds", (cooldownMillis - (now - last)) / 1000 + 1));
            return;
        }
        cooldowns.put(reporter.getUniqueId(), now);
        Report report = new Report(0, reporter.getUniqueId(), reporter.getName(), target.getUniqueId(), target.getName(), reason, now);
        repository.insert(report).whenComplete((saved, error) -> Tasks.sync(() -> {
            if (error != null) {
                messages.send(reporter, "command.error");
                return;
            }
            messages.send(reporter, "report.sent", MessageService.p("player", target.getName()));
            Component alert = messages.get("report.alert", MessageService.p("reporter", reporter.getName()),
                    MessageService.p("player", target.getName()), MessageService.p("reason", reason))
                    .clickEvent(ClickEvent.runCommand("/spectate " + target.getName()))
                    .hoverEvent(messages.get("report.alert-hover", MessageService.p("player", target.getName())));
            staff.alert(alert);
        }));
    }

    /**
     * @param limit rows
     * @return recent reports
     */
    public CompletableFuture<List<Report>> recent(int limit) {
        return repository.recent(limit);
    }
}
