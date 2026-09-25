package net.pvpserver.smoke;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.RenderType;
import org.mockbukkit.mockbukkit.scoreboard.ObjectiveMock;
import org.mockbukkit.mockbukkit.scoreboard.ScoreMock;
import org.mockbukkit.mockbukkit.scoreboard.ScoreboardManagerMock;
import org.mockbukkit.mockbukkit.scoreboard.ScoreboardMock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Scoreboard mocks that support the Paper number-format and custom-name APIs used by the sidebar.
 */
final class PracticeScoreboards {

    private PracticeScoreboards() {
    }

    static final class Manager extends ScoreboardManagerMock {
        @Override
        public ScoreboardMock getNewScoreboard() {
            return new Board();
        }
    }

    static final class Board extends ScoreboardMock {
        @Override
        public ObjectiveMock registerNewObjective(String name, Criteria criteria, Component displayName, RenderType renderType) {
            Objective objective = new Objective(this, name, displayName, criteria, renderType);
            objectives().put(name, objective);
            return objective;
        }

        @SuppressWarnings("unchecked")
        private Map<String, ObjectiveMock> objectives() {
            try {
                Field field = ScoreboardMock.class.getDeclaredField("objectives");
                field.setAccessible(true);
                return (Map<String, ObjectiveMock>) field.get(this);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static final class Objective extends ObjectiveMock {
        private final Map<String, Score> scores = new HashMap<>();
        private NumberFormat format;

        Objective(ScoreboardMock board, String name, Component displayName, Criteria criteria, RenderType renderType) {
            super(board, name, displayName, criteria, renderType);
        }

        @Override
        public NumberFormat numberFormat() {
            return format;
        }

        @Override
        public void numberFormat(NumberFormat format) {
            this.format = format;
        }

        @Override
        public ScoreMock getScore(String entry) {
            return scores.computeIfAbsent(entry, e -> new Score(this, e));
        }
    }

    static final class Score extends ScoreMock {
        private Component customName;
        private int value;
        private boolean set;

        Score(ObjectiveMock objective, String entry) {
            super(objective, entry);
        }

        @Override
        public Component customName() {
            return customName;
        }

        @Override
        public void customName(Component customName) {
            this.customName = customName;
        }

        @Override
        public int getScore() {
            return value;
        }

        @Override
        public void setScore(int score) {
            this.value = score;
            this.set = true;
        }

        @Override
        public boolean isScoreSet() {
            return set;
        }

        @Override
        public void resetScore() {
            set = false;
            value = 0;
        }
    }
}
