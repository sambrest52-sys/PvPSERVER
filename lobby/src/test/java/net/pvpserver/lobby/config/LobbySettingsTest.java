package net.pvpserver.lobby.config;

import net.kyori.adventure.bossbar.BossBar;
import net.pvpserver.core.stats.StatField;
import org.bukkit.Particle;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled config.yml and npcs.yml load cleanly, and broken values fall back with a warning instead of failing.
 */
class LobbySettingsTest {

    static YamlConfiguration bundled(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(LobbySettingsTest.class.getResourceAsStream("/" + name), name), StandardCharsets.UTF_8));
    }

    static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void bundledConfigLoadsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        LobbySettings settings = LobbySettings.read(bundled("config.yml"), warnings);
        assertEquals(List.of(), warnings);

        assertEquals("pvp_lobby", settings.world().name());
        assertEquals(LobbySettings.WorldMode.GENERATED, settings.world().mode());
        assertEquals(64, settings.world().floorY());
        assertEquals(6000, settings.world().time());
        assertEquals(List.of(StatField.ELO, StatField.WINS, StatField.BEST_WIN_STREAK), settings.wall().stats());
        assertEquals(0xB0141420, settings.wall().background());
        assertEquals(BossBar.Color.YELLOW, settings.bossbar().color());
        assertTrue(settings.bossbar().tips().size() >= 5, "a handful of tips ship by default");
        assertEquals(3, settings.announcements().messages().size());
        assertTrue(settings.welcome().titleEnabled());
        assertEquals("entity.player.levelup", settings.welcome().sound());
        assertEquals(Particle.CLOUD, settings.pads().particle());
        assertEquals(List.of("pvp.cosmetic.trail.emerald", "pvp.cosmetic.joineffect.totem"), settings.eggs().completePermissions());
        assertEquals(10, settings.zones().names().size());
        assertEquals(120, settings.performance().maxEntities());
    }

    @Test
    void everyBundledEmitterTypeResolves() {
        List<String> warnings = new ArrayList<>();
        Map<String, LobbySettings.EmitterType> emitters = LobbySettings.read(bundled("config.yml"), warnings).ambient().emitters();
        assertEquals(List.of(), warnings);
        for (String type : List.of("fountain", "mist", "leaves", "spores", "sparkle", "enchant", "smoke", "flame", "soul", "notes",
                "glow", "ash", "drip")) {
            assertNotNull(emitters.get(type), type);
            assertEquals(Void.class, emitters.get(type).particle().getDataType(), type + " needs no extra data");
        }
        assertEquals(Particle.CHERRY_LEAVES, emitters.get("leaves").particle());
        assertEquals(2.5, emitters.get("leaves").offsetX());
    }

    @Test
    void invalidValuesFallBackWithWarnings() throws InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        LobbySettings settings = LobbySettings.read(yaml("""
                world: {name: "bad world!", mode: sideways, floor-y: 9000, time: nope}
                launch-pads: {sound: "Not A Sound", particle: GLITTER}
                leaderboard-wall: {stats: [ELO, SPEED], background: "#12"}
                bossbar: {color: TURQUOISE}
                npcs: {look-range: 500}
                ambient:
                  emitters:
                    good: {particle: flame, count: 3, offset: "1 2 3"}
                    unknown: {particle: SPARKLES}
                    needs-block: {particle: FALLING_DUST}
                    dust: {particle: DUST, color: "#FF0000"}
                    bad-offset: {particle: NOTE, offset: "1 2"}
                """), warnings);

        assertEquals("pvp_lobby", settings.world().name());
        assertEquals(LobbySettings.WorldMode.GENERATED, settings.world().mode());
        assertEquals(64, settings.world().floorY());
        assertEquals(6000, settings.world().time());
        assertEquals("entity.firework_rocket.launch", settings.pads().sound());
        assertEquals(Particle.CLOUD, settings.pads().particle());
        assertEquals(List.of(StatField.ELO), settings.wall().stats());
        assertEquals(BossBar.Color.YELLOW, settings.bossbar().color());
        assertEquals(10, settings.npcs().lookRange());

        Map<String, LobbySettings.EmitterType> emitters = settings.ambient().emitters();
        assertEquals(Particle.FLAME, emitters.get("good").particle());
        assertEquals(3, emitters.get("good").count());
        assertEquals(2, emitters.get("good").offsetY());
        assertNull(emitters.get("unknown"));
        assertNull(emitters.get("needs-block"));
        assertEquals(255, emitters.get("dust").color().getRed());
        assertEquals(0, emitters.get("bad-offset").offsetX());

        for (String expected : List.of("world.name", "world.mode", "world.floor-y", "world.time", "launch-pads.sound",
                "launch-pads.particle", "leaderboard-wall.stats", "leaderboard-wall.background", "bossbar.color", "npcs.look-range",
                "ambient.emitters.unknown", "ambient.emitters.needs-block", "ambient.emitters.bad-offset")) {
            assertTrue(warnings.stream().anyMatch(w -> w.startsWith(expected)), "warning for " + expected + " in " + warnings);
        }
    }

    @Test
    void anOldConfigWithoutTheNewSectionsReadsTheBundledDefaults() throws InvalidConfigurationException {
        // A 1.1 config.yml merged with the bundled defaults, as ConfigFile loads it.
        YamlConfiguration old = yaml("""
                spawn: ""
                tune-world: true
                void-y: 0
                double-jump: {enabled: false}
                """);
        old.setDefaults(bundled("config.yml"));
        old.options().copyDefaults(true);
        List<String> warnings = new ArrayList<>();
        LobbySettings settings = LobbySettings.read(old, warnings);
        assertEquals(List.of(), warnings);
        LobbySettings defaults = LobbySettings.read(bundled("config.yml"), new ArrayList<>());
        assertEquals(defaults.ambient(), settings.ambient(), "emitter types come from the defaults");
        assertEquals(defaults.zones(), settings.zones(), "zone names come from the defaults");
        assertEquals(defaults.bossbar(), settings.bossbar());
        assertEquals(defaults.welcome(), settings.welcome());
        assertFalse(settings.doubleJump().enabled(), "values in the file still win");
        assertEquals("<gold><bold>Ranked Hall", settings.zones().names().get("ranked"));
    }

    @Test
    void emptyConfigUsesDefaults() throws InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        LobbySettings settings = LobbySettings.read(yaml(""), warnings);
        assertEquals(List.of(), warnings);
        assertEquals("pvp_lobby", settings.world().name());
        assertTrue(settings.parkour().enabled());
        assertEquals(10, settings.parkour().leaderboardSize());
        assertFalse(settings.ambient().emitters().containsKey("fountain"), "no emitter types without a config");
    }

    @Test
    void bundledNpcsLoadWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        Map<String, NpcDefinition> npcs = NpcDefinition.read(bundled("npcs.yml"), warnings);
        assertEquals(List.of(), warnings);
        assertEquals(List.of("ranked", "unranked", "ffa", "kit-editor", "stats", "leaderboards", "cosmetics", "party", "info"),
                List.copyOf(npcs.keySet()));
        assertEquals("queue-ranked", npcs.get("ranked").action());
        assertTrue(npcs.get("ranked").hologram().stream().anyMatch(line -> line.contains("<ranked_queued>")));
        assertTrue(npcs.get("ffa").hologram().stream().anyMatch(line -> line.contains("<ffa>")));
        NpcDefinition.Item chest = npcs.get("cosmetics").equipment().get(EquipmentSlot.CHEST);
        assertEquals(0x8E, chest.color().getRed());
        assertEquals(0x44, chest.color().getGreen());
        for (NpcDefinition npc : npcs.values()) {
            assertFalse(npc.hologram().isEmpty(), npc.id() + " has a hologram");
            assertFalse(npc.equipment().isEmpty(), npc.id() + " has equipment");
        }
    }

    @Test
    void brokenNpcEntriesAreSkippedOrRepaired() throws InvalidConfigurationException {
        List<String> warnings = new ArrayList<>();
        Map<String, NpcDefinition> npcs = NpcDefinition.read(yaml("""
                npcs:
                  no-action: {hologram: ["hi"]}
                  odd:
                    action: stats
                    skin: "Notch"
                    equipment: {hat: DIAMOND_HELMET, boots: NOT_A_BLOCK, main-hand: "LEATHER_HELMET #zz", off-hand: SHIELD}
                """), warnings);
        assertEquals(List.of("odd"), List.copyOf(npcs.keySet()));
        NpcDefinition odd = npcs.get("odd");
        assertEquals("", odd.skin());
        assertEquals(Map.of(EquipmentSlot.OFF_HAND, new NpcDefinition.Item(org.bukkit.Material.SHIELD, null)), odd.equipment());
        assertEquals(5, warnings.size(), warnings.toString());
    }
}
