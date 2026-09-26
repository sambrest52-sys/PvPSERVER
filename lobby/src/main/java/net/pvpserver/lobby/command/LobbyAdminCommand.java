package net.pvpserver.lobby.command;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.SubCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.feature.LobbyData;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import net.pvpserver.lobby.world.LobbyMarker;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * /lobby: without arguments (or without permission) it sends you to spawn; admins get reload, regenerate, import and
 * the tools that edit layout.yml in game.
 */
final class LobbyAdminCommand extends BaseCommand {

    private static final String ADMIN = "pvp.admin.lobby";

    private final PvPLobby plugin;
    private final java.util.function.Consumer<Player> spawn;

    LobbyAdminCommand(PvPLobby plugin, java.util.function.Consumer<Player> spawn) {
        super(plugin.messages(), "lobby", List.of(), "pvp.command.spawn", "Lobby: spawn, reload, regenerate, import, set", "", true);
        this.plugin = plugin;
        this.spawn = spawn;
        sub(new Sub("reload", "reload", "Reload config.yml, npcs.yml and layout.yml", false, 0, (s, a) -> reload(s), null));
        sub(new Sub("regenerate", "regenerate [seed]", "Rebuild the generated hub (replaces imports)", false, 0, this::regenerate, null));
        sub(new Sub("import", "import <file|folder>", "Load a lobby from plugins/PvPLobby/imports", false, 1,
                (s, a) -> plugin.importer().importLobby(s, a[0]), (s, a) -> a.length == 1 ? plugin.importer().candidates() : List.of()));
        sub(new Sub("imports", "imports", "List importable lobbies", false, 0, (s, a) -> imports(s), null));
        sub(new Sub("info", "info", "Show what the lobby holds", false, 0, (s, a) -> info(s), null));
        sub(new Sub("set", "set <spawn|npc|hologram|wall|zone> [id] [radius]", "Move something to where you stand", true, 1,
                (s, a) -> set((Player) s, a), (s, a) -> setSuggestions(a)));
        sub(new Sub("remove", "remove <npc|hologram|zone|pad|egg|button> [id]", "Remove something from the layout", true, 1,
                (s, a) -> remove((Player) s, a), (s, a) -> a.length == 1 ? List.of("npc", "hologram", "zone", "pad", "egg", "button")
                : a.length == 2 ? ids(a[0]) : List.of()));
        sub(new Sub("pad", "pad [power]", "Make the block you stand on a launch pad towards where you look", true, 0,
                (s, a) -> pad((Player) s, a), null));
        sub(new Sub("egg", "egg [id]", "Make the block you look at a hidden egg", true, 0, (s, a) -> egg((Player) s, a), null));
        sub(new Sub("button", "button <action>", "Make the block you look at run an action", true, 1,
                (s, a) -> button((Player) s, a), (s, a) -> a.length == 1 ? List.of("kit-editor", "stats", "leaderboards", "cosmetics",
                "settings", "queue-ranked", "queue-unranked", "ffa", "parkour", "message:", "command:") : List.of()));
        sub(new Sub("parkour", "parkour <start|checkpoint|finish|clear|top|reset <player>>", "Edit the parkour course", false, 1,
                this::parkour, (s, a) -> a.length == 1 ? List.of("start", "checkpoint", "finish", "clear", "top", "reset")
                : a.length == 2 && a[0].equalsIgnoreCase("reset") ? Suggest.players(s) : List.of()));
    }

    @Override
    protected void onCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length > 0 && player.hasPermission(ADMIN)) {
            sendHelp(sender);
            return;
        }
        spawn.accept(player);
    }

    private MessageService m() {
        return plugin.messages();
    }

    private LobbyLayout layout() {
        return plugin.lobbyWorld().layout();
    }

    private void save(LobbyLayout layout) {
        plugin.lobbyWorld().layouts().save(layout);
        plugin.lobbyWorld().applyWorldSettings();
        plugin.features().spawnAll();
    }

    private boolean inLobbyWorld(Player player) {
        if (!player.getWorld().equals(plugin.lobbyWorld().world())) {
            m().send(player, "lobby.wrong-world", MessageService.p("world", plugin.lobbyWorld().world().getName()));
            return false;
        }
        return true;
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        List<String> warnings = plugin.reloadLobby();
        m().send(sender, "lobby.reloaded", MessageService.p("ms", System.currentTimeMillis() - start),
                MessageService.p("entities", plugin.features().displays().size()));
        warnings.stream().limit(10).forEach(w -> m().send(sender, "lobby.reload-warning", MessageService.p("warning", w)));
    }

    private void regenerate(CommandSender sender, String[] args) {
        long seed = plugin.settings().world().seed();
        if (args.length > 0) {
            try {
                seed = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                seed = args[0].hashCode();
            }
        }
        if (plugin.lobbyWorld().rebuilding()) {
            m().send(sender, "lobby.busy");
            return;
        }
        long chosen = seed;
        long start = System.currentTimeMillis();
        m().send(sender, "lobby.regenerating", MessageService.p("seed", chosen));
        plugin.lobbyWorld().regenerate(chosen).whenComplete((v, error) -> net.pvpserver.core.util.Tasks.sync(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                m().send(sender, "lobby.regenerate-failed", MessageService.p("reason", String.valueOf(cause.getMessage())));
            } else {
                m().send(sender, "lobby.regenerated", MessageService.p("seed", chosen), MessageService.p("ms", System.currentTimeMillis() - start));
            }
        }));
    }

    private void imports(CommandSender sender) {
        List<String> names = plugin.importer().candidates();
        if (names.isEmpty()) {
            m().send(sender, "lobby.imports-empty", MessageService.p("folder", "plugins/PvPLobby/imports"));
        } else {
            m().send(sender, "lobby.imports-list", MessageService.p("files", String.join(", ", names)));
        }
    }

    private void info(CommandSender sender) {
        LobbyLayout layout = layout();
        LobbyMarker marker = plugin.lobbyWorld().marker();
        String source = marker == null ? "custom world" : switch (marker.source()) {
            case "generated" -> "generated hub (seed " + marker.detail() + ")";
            case "schematic" -> "imported schematic " + marker.detail();
            default -> "imported world " + marker.detail();
        };
        m().send(sender, "lobby.info", MessageService.p("world", plugin.lobbyWorld().world().getName()), MessageService.p("source", source),
                MessageService.p("spawn", layout.spawn().format()),
                MessageService.p("npcs", plugin.features().npcs().ids().size() + "/" + plugin.npcDefinitions().size()),
                MessageService.p("holograms", layout.holograms().size()), MessageService.p("portals", layout.portals().size()),
                MessageService.p("pads", layout.pads().size()), MessageService.p("buttons", layout.buttons().size()),
                MessageService.p("eggs", layout.eggs().size()), MessageService.p("zones", layout.zones().size()),
                MessageService.p("emitters", plugin.features().ambient().size()),
                MessageService.p("parkour", layout.parkour() == null ? "none" : (layout.parkour().checkpoints().size() + " checkpoints")),
                MessageService.p("wall", plugin.features().wall().boards().size()),
                MessageService.p("entities", plugin.features().displays().size()),
                MessageService.p("runners", plugin.features().parkour().runners()));
    }

    private List<String> setSuggestions(String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "npc", "hologram", "wall", "zone");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "npc" -> new ArrayList<>(plugin.npcDefinitions().keySet());
                case "hologram" -> {
                    List<String> ids = new ArrayList<>(layout().holograms().keySet());
                    ids.add("parkour");
                    yield ids;
                }
                case "zone" -> new ArrayList<>(plugin.settings().zones().names().keySet());
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> ids(String kind) {
        LobbyLayout layout = layout();
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "npc" -> new ArrayList<>(layout.npcs().keySet());
            case "hologram" -> new ArrayList<>(layout.holograms().keySet());
            case "zone" -> layout.zones().stream().map(LobbyLayout.Zone::id).toList();
            case "egg" -> layout.eggs().stream().map(LobbyLayout.Egg::id).toList();
            default -> List.of();
        };
    }

    private void set(Player player, String[] args) {
        if (!inLobbyWorld(player)) {
            return;
        }
        LobbyLayout layout = layout();
        Point here = Point.from(player.getLocation());
        String kind = args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (kind) {
            case "spawn" -> save(layout.withSpawn(here));
            case "npc", "hologram" -> {
                if (id.isEmpty()) {
                    usage(player, "set " + kind + " <id>");
                    return;
                }
                Map<String, Point> npcs = new LinkedHashMap<>(layout.npcs());
                Map<String, Point> holograms = new LinkedHashMap<>(layout.holograms());
                if (kind.equals("npc")) {
                    npcs.put(id, here);
                } else {
                    holograms.put(id, here.add(0, 1.5, 0).facing(0, 0));
                }
                save(layout.withPlacements(npcs, holograms));
                if (kind.equals("npc") && !plugin.npcDefinitions().containsKey(id)) {
                    m().send(player, "lobby.npc-undefined", MessageService.p("id", id));
                }
            }
            case "wall" -> {
                LobbyLayout.Wall old = layout.wall();
                // Stand where the top-left panel goes, looking the way the panels should face.
                Point at = here.add(0, 1.5, 0).facing(Math.round(player.getLocation().getYaw() / 90f) * 90f, 0);
                save(layout.withWall(new LobbyLayout.Wall(at, old == null ? 5 : old.columns(), old == null ? 4 : old.spacingX(),
                        old == null ? 4 : old.spacingY())));
            }
            case "zone" -> {
                if (id.isEmpty()) {
                    usage(player, "set zone <id> [radius]");
                    return;
                }
                double radius = args.length > 2 ? parse(args[2], 12) : 12;
                List<LobbyLayout.Zone> zones = new ArrayList<>(layout.zones());
                zones.removeIf(zone -> zone.id().equals(id));
                zones.add(new LobbyLayout.Zone(id, here.x(), here.z(), radius));
                save(new LobbyLayout(layout.spawn(), layout.voidY(), layout.border(), layout.npcs(), layout.holograms(), layout.portals(),
                        layout.pads(), layout.buttons(), layout.parkour(), layout.eggs(), zones, layout.emitters(), layout.wall()));
            }
            default -> {
                usage(player, "set <spawn|npc|hologram|wall|zone> [id] [radius]");
                return;
            }
        }
        m().send(player, "lobby.set-done", MessageService.p("what", (kind + " " + id).trim()), MessageService.p("where", here.format()));
    }

    private void remove(Player player, String[] args) {
        LobbyLayout layout = layout();
        String kind = args[0].toLowerCase(Locale.ROOT);
        String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        BlockPos near = BlockPos.of(player.getLocation());
        boolean removed;
        switch (kind) {
            case "npc", "hologram" -> {
                Map<String, Point> npcs = new LinkedHashMap<>(layout.npcs());
                Map<String, Point> holograms = new LinkedHashMap<>(layout.holograms());
                removed = (kind.equals("npc") ? npcs.remove(id) : holograms.remove(id)) != null;
                if (removed) {
                    save(layout.withPlacements(npcs, holograms));
                }
            }
            case "zone" -> {
                List<LobbyLayout.Zone> zones = new ArrayList<>(layout.zones());
                removed = zones.removeIf(zone -> zone.id().equals(id));
                if (removed) {
                    save(new LobbyLayout(layout.spawn(), layout.voidY(), layout.border(), layout.npcs(), layout.holograms(),
                            layout.portals(), layout.pads(), layout.buttons(), layout.parkour(), layout.eggs(), zones, layout.emitters(),
                            layout.wall()));
                }
            }
            case "pad" -> {
                List<LobbyLayout.LaunchPad> pads = new ArrayList<>(layout.pads());
                removed = pads.removeIf(pad -> distance(pad.at(), near) <= 3);
                if (removed) {
                    save(layout.withTriggers(layout.portals(), pads, layout.buttons(), layout.parkour(), layout.eggs()));
                }
            }
            case "egg" -> {
                List<LobbyLayout.Egg> eggs = new ArrayList<>(layout.eggs());
                removed = eggs.removeIf(egg -> egg.id().equals(id) || (id.isEmpty() && distance(egg.at(), near) <= 3));
                if (removed) {
                    save(layout.withTriggers(layout.portals(), layout.pads(), layout.buttons(), layout.parkour(), eggs));
                }
            }
            case "button" -> {
                List<LobbyLayout.Button> buttons = new ArrayList<>(layout.buttons());
                removed = buttons.removeIf(button -> distance(button.at(), near) <= 3);
                if (removed) {
                    save(layout.withTriggers(layout.portals(), layout.pads(), buttons, layout.parkour(), layout.eggs()));
                }
            }
            default -> {
                usage(player, "remove <npc|hologram|zone|pad|egg|button> [id]");
                return;
            }
        }
        m().send(player, removed ? "lobby.removed" : "lobby.not-found", MessageService.p("what", (kind + " " + id).trim()));
    }

    private static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    private void pad(Player player, String[] args) {
        if (!inLobbyWorld(player)) {
            return;
        }
        double power = args.length > 0 ? parse(args[0], 2) : 2;
        power = Math.max(0.3, Math.min(4, power));
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1e-6) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(power);
        LobbyLayout layout = layout();
        List<LobbyLayout.LaunchPad> pads = new ArrayList<>(layout.pads());
        BlockPos at = BlockPos.of(player.getLocation());
        pads.removeIf(pad -> pad.at().equals(at));
        pads.add(new LobbyLayout.LaunchPad(at, round(direction.getX()), round(Math.max(0.6, power * 0.5)), round(direction.getZ())));
        save(layout.withTriggers(layout.portals(), pads, layout.buttons(), layout.parkour(), layout.eggs()));
        m().send(player, "lobby.set-done", MessageService.p("what", "launch pad"), MessageService.p("where", at.format()));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private Block target(Player player) {
        try {
            Block block = player.getTargetBlockExact(6);
            if (block != null && !block.getType().isAir()) {
                return block;
            }
        } catch (RuntimeException ignored) {
            // fall through to the block below the player's feet
        }
        return player.getLocation().getBlock().getRelative(0, -1, 0);
    }

    private void egg(Player player, String[] args) {
        if (!inLobbyWorld(player)) {
            return;
        }
        LobbyLayout layout = layout();
        BlockPos at = BlockPos.of(target(player));
        String id = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "egg-" + (layout.eggs().size() + 1);
        List<LobbyLayout.Egg> eggs = new ArrayList<>(layout.eggs());
        eggs.removeIf(egg -> egg.id().equals(id) || egg.at().equals(at));
        eggs.add(new LobbyLayout.Egg(id, at));
        save(layout.withTriggers(layout.portals(), layout.pads(), layout.buttons(), layout.parkour(), eggs));
        m().send(player, "lobby.set-done", MessageService.p("what", "egg " + id), MessageService.p("where", at.format()));
    }

    private void button(Player player, String[] args) {
        if (!inLobbyWorld(player)) {
            return;
        }
        LobbyLayout layout = layout();
        BlockPos at = BlockPos.of(target(player));
        String action = String.join(" ", args);
        List<LobbyLayout.Button> buttons = new ArrayList<>(layout.buttons());
        buttons.removeIf(button -> button.at().equals(at));
        buttons.add(new LobbyLayout.Button(at, action));
        save(layout.withTriggers(layout.portals(), layout.pads(), buttons, layout.parkour(), layout.eggs()));
        m().send(player, "lobby.set-done", MessageService.p("what", "button " + action), MessageService.p("where", at.format()));
    }

    private void parkour(CommandSender sender, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "top" -> {
                List<LobbyData.Time> top = plugin.features().data().top(plugin.settings().parkour().leaderboardSize());
                m().send(sender, "parkour.top-header");
                if (top.isEmpty()) {
                    m().send(sender, "parkour.top-empty");
                }
                for (int i = 0; i < top.size(); i++) {
                    m().send(sender, "parkour.top-line", MessageService.p("position", i + 1), MessageService.p("player", top.get(i).name()),
                            MessageService.p("time", TimeUtil.formatMillis(top.get(i).millis())));
                }
                return;
            }
            case "reset" -> {
                if (args.length < 2) {
                    usage(sender, "parkour reset <player>");
                    return;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                plugin.features().data().reset(target.getUniqueId());
                plugin.features().parkour().refreshBoard();
                m().send(sender, "lobby.progress-reset", MessageService.p("player", args[1]));
                return;
            }
            default -> {
                // editing below
            }
        }
        if (!(sender instanceof Player player)) {
            m().send(sender, "command.player-only");
            return;
        }
        if (!inLobbyWorld(player)) {
            return;
        }
        LobbyLayout layout = layout();
        LobbyLayout.Parkour old = layout.parkour();
        BlockPos at = BlockPos.of(player.getLocation());
        BlockPos start = old == null ? null : old.start();
        BlockPos finish = old == null ? null : old.finish();
        List<BlockPos> checkpoints = old == null ? new ArrayList<>() : new ArrayList<>(old.checkpoints());
        switch (action) {
            case "start" -> start = at;
            case "checkpoint" -> checkpoints.add(at);
            case "finish" -> finish = at;
            case "clear" -> {
                start = null;
                finish = null;
                checkpoints.clear();
            }
            default -> {
                usage(sender, "parkour <start|checkpoint|finish|clear|top|reset <player>>");
                return;
            }
        }
        LobbyLayout.Parkour course = null;
        if (start != null && finish != null) {
            int lowest = Math.min(start.y(), finish.y());
            for (BlockPos checkpoint : checkpoints) {
                lowest = Math.min(lowest, checkpoint.y());
            }
            course = new LobbyLayout.Parkour(start, checkpoints, finish, lowest - 4);
        } else if (start != null || finish != null) {
            // Keep a half-built course around until both ends exist.
            course = new LobbyLayout.Parkour(start == null ? at : start, checkpoints, finish == null ? at : finish, at.y() - 4);
        }
        save(layout.withTriggers(layout.portals(), layout.pads(), layout.buttons(), course, layout.eggs()));
        m().send(sender, "lobby.set-done", MessageService.p("what", "parkour " + action), MessageService.p("where", at.format()));
    }

    private static double parse(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void usage(CommandSender sender, String usage) {
        m().send(sender, "command.usage", MessageService.p("usage", "/lobby " + usage));
    }

    private static final class Sub extends SubCommand {
        private final BiConsumer<CommandSender, String[]> action;
        private final BiFunction<CommandSender, String[], List<String>> suggester;

        Sub(String name, String usage, String description, boolean playerOnly, int minArgs, BiConsumer<CommandSender, String[]> action,
            BiFunction<CommandSender, String[], List<String>> suggester) {
            super(name, List.of(), ADMIN, usage, description, playerOnly, minArgs);
            this.action = action;
            this.suggester = suggester;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            action.accept(sender, args);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            return suggester == null ? List.of() : new ArrayList<>(suggester.apply(sender, args));
        }
    }

    @Override
    protected List<String> onSuggest(CommandSender sender, String[] args) {
        return List.of();
    }
}
