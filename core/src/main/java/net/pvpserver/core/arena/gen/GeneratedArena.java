package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.Arena;
import net.pvpserver.core.arena.ArenaTemplate;
import net.pvpserver.core.arena.RelativeBox;
import net.pvpserver.core.arena.RelativePosition;
import net.pvpserver.core.arena.TemplateBuilder;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * A built-in arena produced by the generator: its blocks plus the definition written to arenas.yml.
 *
 * @param name arena id
 * @param displayName MiniMessage display name
 * @param icon menu icon material name
 * @param tags arena tags (matched against kit {@code arena-tags})
 * @param spawnA team A spawn
 * @param spawnB team B spawn
 * @param spectator spectator spawn
 * @param buildLimit maximum relative y for placing blocks
 * @param voidY relative y below which players count as fallen
 * @param goalA goal defended by team A (bridge) or null
 * @param goalB goal defended by team B (bridge) or null
 * @param goalRadius goal radius
 * @param buildArea build area or null for the whole footprint
 * @param blocks template contents
 */
public record GeneratedArena(String name, String displayName, String icon, List<String> tags, RelativePosition spawnA,
                             RelativePosition spawnB, RelativePosition spectator, int buildLimit, int voidY,
                             RelativePosition goalA, RelativePosition goalB, double goalRadius, RelativeBox buildArea,
                             TemplateBuilder blocks) {

    /** @return the arena definition */
    public Arena toArena() {
        Material material = Material.matchMaterial(icon);
        return new Arena(name, displayName, material == null ? Material.GRASS_BLOCK : material, true, Set.copyOf(tags),
                spawnA, spawnB, spectator, buildLimit, voidY, goalA, goalB, goalRadius, buildArea);
    }

    /** @return the block template */
    public ArenaTemplate template() {
        return blocks.build();
    }

    /**
     * Fluent construction of a generated arena.
     */
    public static final class Builder {
        private final String name;
        private final TemplateBuilder blocks;
        private String displayName;
        private String icon = "GRASS_BLOCK";
        private List<String> tags = List.of("standard");
        private RelativePosition spawnA;
        private RelativePosition spawnB;
        private RelativePosition spectator;
        private int buildLimit = 12;
        private int voidY = -6;
        private RelativePosition goalA;
        private RelativePosition goalB;
        private double goalRadius = 1.6;
        private RelativeBox buildArea;

        /**
         * @param name arena id
         * @param blocks template contents
         */
        public Builder(String name, TemplateBuilder blocks) {
            this.name = name;
            this.blocks = blocks;
            this.displayName = "<white>" + name;
        }

        public Builder display(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
            return this;
        }

        public Builder tags(String... tags) {
            this.tags = List.of(tags);
            return this;
        }

        public Builder spawns(RelativePosition a, RelativePosition b) {
            this.spawnA = a;
            this.spawnB = b;
            return this;
        }

        public Builder spectator(RelativePosition spectator) {
            this.spectator = spectator;
            return this;
        }

        public Builder limits(int buildLimit, int voidY) {
            this.buildLimit = buildLimit;
            this.voidY = voidY;
            return this;
        }

        public Builder goals(RelativePosition a, RelativePosition b, double radius) {
            this.goalA = a;
            this.goalB = b;
            this.goalRadius = radius;
            return this;
        }

        public Builder buildArea(RelativeBox area) {
            this.buildArea = area;
            return this;
        }

        /** @return the arena (connects fences, panes and walls first) */
        public GeneratedArena build() {
            blocks.connectShapes();
            return new GeneratedArena(name, displayName, icon, tags, spawnA, spawnB, spectator, buildLimit, voidY, goalA,
                    goalB, goalRadius, buildArea, blocks);
        }
    }
}
