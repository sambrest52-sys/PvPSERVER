package net.pvpserver.lobby.world;

import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.Box;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@code [tag]} signs in an imported lobby into a layout. Tagged signs are replaced (by air, or by a pressure
 * plate or egg where that is what they mark). Positions stay in the coordinates the signs were read in.
 *
 * <pre>
 * [spawn]                      spawn; players face the way you faced when placing the sign
 * [npc &lt;id&gt;]                   NPC from npcs.yml ("kit editor" = kit-editor); it faces the sign's text side
 * [hologram &lt;id&gt;]              hologram (parkour, rules, links, welcome, ...)
 * [portal &lt;mode&gt;]              3 wide, 4 high portal with the sign at its bottom centre (ranked, unranked, ffa,
 *                              ffa &lt;arena&gt;, kit editor, stats, cosmetics, ...)
 * [pad] / [pad &lt;power&gt;]         launch pad pushing players the way you faced
 * [parkour start] [checkpoint &lt;n&gt;] [parkour finish]
 * [egg] / [egg &lt;id&gt;]            hidden egg (the sign becomes a dragon egg)
 * [button &lt;action&gt;]            makes the block below the sign clickable
 * [zone &lt;id&gt;] / [zone &lt;id&gt; &lt;radius&gt;]
 * [particles &lt;type&gt;]           ambient emitter from config.yml
 * [wall] / [wall &lt;columns&gt;]     leaderboard wall; the sign marks the top-left panel
 * [border &lt;size&gt;]  [void]
 * </pre>
 */
public final class TagLayout {

    /** Reads and writes blocks where the signs are. */
    public interface Blocks {
        /**
         * @param x x
         * @param y y
         * @param z z
         * @return block data string
         */
        String get(int x, int y, int z);

        /**
         * @param x x
         * @param y y
         * @param z z
         * @param block block data string
         */
        void set(int x, int y, int z, String block);
    }

    /**
     * A tag found on a sign.
     *
     * @param text tag text (lower case, single spaces)
     * @param x sign x
     * @param y sign y
     * @param z sign z
     */
    public record SignTag(String text, int x, int y, int z) {
    }

    /**
     * @param layout layout (spawn may be the fallback when there was no [spawn] sign)
     * @param used tags that were understood
     * @param notes human readable notes: unknown tags, missing pieces
     * @param hasSpawn whether a [spawn] sign was found
     */
    public record Result(LobbyLayout layout, int used, List<String> notes, boolean hasSpawn) {
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern ROTATION = Pattern.compile("rotation=(\\d+)");
    private static final Pattern FACING = Pattern.compile("facing=(north|south|east|west)");

    private TagLayout() {
    }

    /**
     * @param blocks block access
     * @param tags sign tags
     * @param fallbackSpawn spawn when there is no [spawn] sign
     * @param voidY void height when there is no [void] sign
     * @param border border when there is no [border] sign (may be null)
     * @return result
     */
    public static Result build(Blocks blocks, List<SignTag> tags, Point fallbackSpawn, int voidY, LobbyLayout.Border border) {
        List<String> notes = new ArrayList<>();
        Point spawn = null;
        Map<String, Point> npcs = new LinkedHashMap<>();
        Map<String, Point> holograms = new LinkedHashMap<>();
        List<LobbyLayout.Portal> portals = new ArrayList<>();
        List<LobbyLayout.LaunchPad> pads = new ArrayList<>();
        List<LobbyLayout.Button> buttons = new ArrayList<>();
        List<LobbyLayout.Egg> eggs = new ArrayList<>();
        List<LobbyLayout.Zone> zones = new ArrayList<>();
        List<LobbyLayout.Emitter> emitters = new ArrayList<>();
        LobbyLayout.Wall wall = null;
        BlockPos start = null;
        BlockPos finish = null;
        TreeMap<Integer, BlockPos> numbered = new TreeMap<>();
        List<BlockPos> unnumbered = new ArrayList<>();
        int used = 0;

        for (SignTag tag : tags) {
            String[] words = tag.text().trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (words.length == 0 || words[0].isEmpty()) {
                continue;
            }
            String state = blocks.get(tag.x(), tag.y(), tag.z());
            float textYaw = textYaw(state);
            float lookYaw = wrap(textYaw + 180);
            BlockPos at = new BlockPos(tag.x(), tag.y(), tag.z());
            String rest = join(words, 1);
            String replacement = "minecraft:air";
            boolean understood = true;
            switch (words[0]) {
                case "spawn" -> spawn = new Point(at.x() + 0.5, at.y(), at.z() + 0.5, lookYaw, 0);
                case "npc" -> {
                    if (rest.isEmpty()) {
                        understood = false;
                    } else {
                        npcs.put(rest.replace(' ', '-'), new Point(at.x() + 0.5, at.y(), at.z() + 0.5, textYaw, 0));
                    }
                }
                case "hologram", "holo" -> {
                    if (rest.isEmpty()) {
                        understood = false;
                    } else {
                        holograms.put(rest.replace(' ', '-'), Point.of(at.x() + 0.5, at.y() + 1.2, at.z() + 0.5));
                    }
                }
                case "portal" -> {
                    String id = rest.isEmpty() ? "portal-" + (portals.size() + 1) : words[1];
                    portals.add(new LobbyLayout.Portal(id, Box.of(at.add(-1, 0, -1), at.add(1, 3, 1)), action(rest), color(id)));
                }
                case "pad", "launch", "launchpad" -> {
                    double power = number(words, 2.0);
                    double rad = Math.toRadians(lookYaw);
                    pads.add(new LobbyLayout.LaunchPad(at, round(-Math.sin(rad) * power), round(Math.max(0.6, power * 0.5)),
                            round(Math.cos(rad) * power)));
                    replacement = "minecraft:heavy_weighted_pressure_plate[power=0]";
                }
                case "parkour", "start", "checkpoint", "finish", "end" -> {
                    String kind = words[0].equals("parkour") ? (words.length > 1 ? words[1] : "") : words[0];
                    switch (kind) {
                        case "start" -> {
                            start = at;
                            replacement = "minecraft:light_weighted_pressure_plate[power=0]";
                        }
                        case "checkpoint", "cp" -> {
                            Integer n = integer(words);
                            if (n == null) {
                                unnumbered.add(at);
                            } else {
                                numbered.put(n, at);
                            }
                            replacement = "minecraft:light_weighted_pressure_plate[power=0]";
                        }
                        case "finish", "end" -> {
                            finish = at;
                            replacement = "minecraft:heavy_weighted_pressure_plate[power=0]";
                        }
                        default -> understood = false;
                    }
                }
                case "egg" -> {
                    eggs.add(new LobbyLayout.Egg(rest.isEmpty() ? "egg-" + (eggs.size() + 1) : rest.replace(' ', '-'), at));
                    replacement = "minecraft:dragon_egg";
                }
                case "button", "click" -> {
                    if (rest.isEmpty()) {
                        understood = false;
                    } else {
                        buttons.add(new LobbyLayout.Button(at.add(0, -1, 0), action(rest)));
                    }
                }
                case "zone" -> {
                    if (rest.isEmpty()) {
                        understood = false;
                    } else {
                        double radius = number(words, 12);
                        String id = join(stripNumbers(words), 1).replace(' ', '-');
                        zones.add(new LobbyLayout.Zone(id, at.x() + 0.5, at.z() + 0.5, radius));
                    }
                }
                case "particles", "particle", "emitter" -> {
                    if (rest.isEmpty()) {
                        understood = false;
                    } else {
                        emitters.add(new LobbyLayout.Emitter(rest.replace(' ', '-'), Point.of(at.x() + 0.5, at.y() + 0.5, at.z() + 0.5)));
                    }
                }
                case "wall", "leaderboard", "leaderboards" -> {
                    int columns = (int) number(words, 5);
                    wall = new LobbyLayout.Wall(new Point(at.x() + 0.5, at.y(), at.z() + 0.5, textYaw, 0), Math.max(1, Math.min(16, columns)),
                            4, 4);
                }
                case "border" -> {
                    double size = number(words, 0);
                    if (size >= 16) {
                        border = new LobbyLayout.Border(at.x() + 0.5, at.z() + 0.5, size);
                    } else {
                        understood = false;
                    }
                }
                case "void" -> voidY = at.y();
                default -> understood = false;
            }
            if (understood) {
                used++;
                blocks.set(at.x(), at.y(), at.z(), replacement);
            } else {
                notes.add("unknown tag [" + tag.text() + "] at " + at.format());
            }
        }

        List<BlockPos> checkpoints = new ArrayList<>(numbered.values());
        if (!unnumbered.isEmpty()) {
            // Without numbers, follow the course: always go to the nearest remaining checkpoint.
            BlockPos current = checkpoints.isEmpty() ? start : checkpoints.get(checkpoints.size() - 1);
            List<BlockPos> remaining = new ArrayList<>(unnumbered);
            while (!remaining.isEmpty()) {
                BlockPos from = current == null ? remaining.get(0) : current;
                BlockPos next = remaining.stream().min(Comparator.comparingDouble(p -> distance(p, from))).orElseThrow();
                remaining.remove(next);
                checkpoints.add(next);
                current = next;
            }
        }
        LobbyLayout.Parkour parkour = null;
        if (start != null && finish != null) {
            int lowest = Math.min(start.y(), finish.y());
            for (BlockPos checkpoint : checkpoints) {
                lowest = Math.min(lowest, checkpoint.y());
            }
            parkour = new LobbyLayout.Parkour(start, checkpoints, finish, lowest - 4);
        } else if (start != null || finish != null || !checkpoints.isEmpty()) {
            notes.add("the parkour needs both a [parkour start] and a [parkour finish] sign");
        }
        boolean hasSpawn = spawn != null;
        if (!hasSpawn) {
            spawn = fallbackSpawn;
            notes.add("no [spawn] sign, spawn set to " + fallbackSpawn.format() + " (move it with /lobby set spawn)");
        }
        LobbyLayout layout = new LobbyLayout(spawn, voidY, border, npcs, holograms, portals, pads, buttons, parkour, eggs, zones,
                emitters, wall);
        return new Result(layout, used, notes, hasSpawn);
    }

    /**
     * @param spec mode words, e.g. "ranked", "ffa nodebuff", "kit editor"
     * @return action id
     */
    static String action(String spec) {
        String[] words = spec.trim().split("\\s+");
        return switch (words[0]) {
            case "ranked" -> "queue-ranked";
            case "unranked" -> "queue-unranked";
            case "ffa" -> words.length > 1 ? "ffa:" + words[1] : "ffa";
            case "party" -> "party-create";
            case "" -> "spawn";
            default -> spec.trim().replace(' ', '-');
        };
    }

    private static String color(String id) {
        return switch (id) {
            case "ranked" -> "#FFC83D";
            case "unranked" -> "#3DD6FF";
            case "ffa" -> "#FF4040";
            default -> "#B388FF";
        };
    }

    /**
     * @param state sign block data
     * @return yaw the sign's text faces (0 = south)
     */
    static float textYaw(String state) {
        Matcher rotation = ROTATION.matcher(state);
        if (rotation.find()) {
            return wrap(Integer.parseInt(rotation.group(1)) * 22.5f);
        }
        Matcher facing = FACING.matcher(state);
        if (facing.find()) {
            return switch (facing.group(1)) {
                case "north" -> 180f;
                case "east" -> -90f;
                case "west" -> 90f;
                default -> 0f;
            };
        }
        return 0f;
    }

    private static float wrap(float yaw) {
        float wrapped = yaw % 360;
        if (wrapped > 180) {
            wrapped -= 360;
        } else if (wrapped <= -180) {
            wrapped += 360;
        }
        return wrapped;
    }

    private static String join(String[] words, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < words.length; i++) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(words[i]);
        }
        return out.toString();
    }

    private static String[] stripNumbers(String[] words) {
        return java.util.Arrays.stream(words).filter(w -> !NUMBER.matcher(w).matches()).toArray(String[]::new);
    }

    private static double number(String[] words, double fallback) {
        for (int i = 1; i < words.length; i++) {
            if (NUMBER.matcher(words[i]).matches()) {
                return Double.parseDouble(words[i]);
            }
        }
        return fallback;
    }

    private static Integer integer(String[] words) {
        for (int i = 1; i < words.length; i++) {
            if (words[i].matches("\\d+")) {
                return Integer.parseInt(words[i]);
            }
        }
        return null;
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
