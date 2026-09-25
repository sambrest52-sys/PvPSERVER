package net.pvpserver.core.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Objects;

/**
 * Per-player sidebar on a private scoreboard. Lines are score entries with Adventure custom names and a blank number
 * format, so only lines that actually changed are re-sent; there is no flicker and no 16/40 character limit.
 */
public final class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private final String[] entries = new String[MAX_LINES];
    private final Component[] current = new Component[MAX_LINES];
    private Objective health;
    private Component title;
    private int lineCount;
    private boolean visible = true;

    /**
     * Creates the scoreboard and assigns it to the player.
     *
     * @param player owner
     */
    public Sidebar(Player player) {
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("pvp_sidebar", Criteria.DUMMY, Component.empty(), RenderType.INTEGER);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.objective.numberFormat(NumberFormat.blank());
        for (int i = 0; i < MAX_LINES; i++) {
            entries[i] = "line_" + i;
        }
        player.setScoreboard(board);
    }

    /**
     * Updates title and lines, sending only differences.
     *
     * @param newTitle title
     * @param lines up to 15 lines (extra lines are ignored)
     */
    public void update(Component newTitle, List<Component> lines) {
        if (!Objects.equals(title, newTitle)) {
            title = newTitle;
            objective.displayName(newTitle);
        }
        int count = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < count; i++) {
            Component line = lines.get(i);
            int scoreValue = count - i;
            var score = objective.getScore(entries[i]);
            boolean lineAdded = i >= lineCount;
            if (lineAdded || !Objects.equals(current[i], line)) {
                score.customName(line);
                current[i] = line;
            }
            if (lineAdded || score.getScore() != scoreValue) {
                score.setScore(scoreValue);
            }
        }
        for (int i = count; i < lineCount; i++) {
            board.resetScores(entries[i]);
            current[i] = null;
        }
        lineCount = count;
    }

    /**
     * Shows or hides the sidebar (player setting).
     *
     * @param visible visibility
     */
    public void visible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        objective.setDisplaySlot(visible ? DisplaySlot.SIDEBAR : null);
    }

    /** @return whether the sidebar is shown */
    public boolean visible() {
        return visible;
    }

    /**
     * Toggles the health indicator below player names (used in matches whose kit enables it).
     *
     * @param enabled whether to show health
     */
    public void healthBelowName(boolean enabled) {
        if (enabled && health == null) {
            health = board.registerNewObjective("pvp_health", Criteria.HEALTH, Component.text("❤", NamedTextColor.RED), RenderType.HEARTS);
            health.setDisplaySlot(DisplaySlot.BELOW_NAME);
            for (Player online : Bukkit.getOnlinePlayers()) {
                health.getScore(online.getName()).setScore((int) Math.ceil(online.getHealth()));
            }
        } else if (!enabled && health != null) {
            health.unregister();
            health = null;
        }
    }

    /** @return underlying scoreboard (modules may add teams, e.g. for team colours) */
    public Scoreboard board() {
        return board;
    }
}
