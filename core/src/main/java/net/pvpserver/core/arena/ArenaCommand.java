package net.pvpserver.core.arena;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.SubCommand;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * {@code /arena} admin command: create/edit/save arenas, set spawns, goals, build limits, tags and manage the pool.
 */
public final class ArenaCommand extends BaseCommand {

    private static final String PERMISSION = "pvp.admin.arena";

    private final ArenaService arenas;
    private final ArenaEditor editor;
    private final ArenaImporter importer;

    /**
     * @param messages messages
     * @param arenas arena service
     * @param editor editor
     * @param importer schematic/world importer
     */
    public ArenaCommand(MessageService messages, ArenaService arenas, ArenaEditor editor, ArenaImporter importer) {
        super(messages, "arena", List.of("arenas"), PERMISSION, "Create and manage arenas", "", false);
        this.arenas = arenas;
        this.editor = editor;
        this.importer = importer;

        sub(new Sub("list", "list", "List arenas", false, 0, (s, a) -> list(s)));
        sub(new Sub("info", "info <arena>", "Show arena details", false, 1, (s, a) -> info(s, a[0])));
        sub(new Sub("status", "status", "Show pool statistics", false, 0, (s, a) -> status(s)));
        sub(new Sub("create", "create <name>", "Start creating an arena", true, 1, (s, a) -> editor.create((Player) s, a[0])));
        sub(new Sub("edit", "edit <arena>", "Paste an arena into the editor world", true, 1, (s, a) -> editor.edit((Player) s, a[0])));
        sub(new Sub("wand", "wand", "Get the selection wand", true, 0, (s, a) -> editor.giveWand((Player) s)));
        sub(new Sub("pos1", "pos1", "Set corner 1 at your feet", true, 0, (s, a) -> session(s, se -> {
            se.pos1 = ((Player) s).getLocation().getBlock().getLocation();
            posSet(s, "1", se.pos1);
        })));
        sub(new Sub("pos2", "pos2", "Set corner 2 at your feet", true, 0, (s, a) -> session(s, se -> {
            se.pos2 = ((Player) s).getLocation().getBlock().getLocation();
            posSet(s, "2", se.pos2);
        })));
        sub(new Sub("setspawn", "setspawn <a|b|spectator>", "Set a spawn at your position", true, 1, (s, a) -> session(s, se -> {
            var loc = ((Player) s).getLocation();
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "a", "1" -> se.spawnA = loc;
                case "b", "2" -> se.spawnB = loc;
                case "spectator", "spec" -> se.spectator = loc;
                default -> {
                    usageOf(s, "setspawn <a|b|spectator>");
                    return;
                }
            }
            messages.send(s, "arena.spawn-set", MessageService.p("spawn", a[0]));
        })));
        sub(new Sub("setgoal", "setgoal <a|b>", "Set a bridge goal centre (goal A is defended by team A)", true, 1, (s, a) -> session(s, se -> {
            var loc = ((Player) s).getLocation();
            if (a[0].equalsIgnoreCase("a")) {
                se.goalA = loc;
            } else if (a[0].equalsIgnoreCase("b")) {
                se.goalB = loc;
            } else {
                usageOf(s, "setgoal <a|b>");
                return;
            }
            messages.send(s, "arena.goal-set", MessageService.p("goal", a[0]));
        })));
        sub(new Sub("goalradius", "goalradius <radius>", "Set the goal radius", true, 1, (s, a) -> session(s, se -> {
            se.goalRadius = parseDouble(a[0], 1.6);
            messages.send(s, "arena.value-set", MessageService.p("key", "goal radius"), MessageService.p("value", se.goalRadius));
        })));
        sub(new Sub("buildlimit", "buildlimit [y]", "Set the max build height (default: your Y)", true, 0, (s, a) -> session(s, se -> {
            se.buildLimitY = a.length > 0 ? (int) parseDouble(a[0], ((Player) s).getLocation().getBlockY()) : ((Player) s).getLocation().getBlockY();
            messages.send(s, "arena.value-set", MessageService.p("key", "build limit"), MessageService.p("value", se.buildLimitY));
        })));
        sub(new Sub("voidy", "voidy [y]", "Set the fall/void Y (default: your Y)", true, 0, (s, a) -> session(s, se -> {
            se.voidY = a.length > 0 ? (int) parseDouble(a[0], ((Player) s).getLocation().getBlockY()) : ((Player) s).getLocation().getBlockY();
            messages.send(s, "arena.value-set", MessageService.p("key", "void Y"), MessageService.p("value", se.voidY));
        })));
        sub(new Sub("tag", "tag <add|remove> <tag>", "Edit arena tags (standard, build, sumo, bridge...)", true, 2, (s, a) -> session(s, se -> {
            String tag = a[1].toLowerCase(Locale.ROOT);
            if (a[0].equalsIgnoreCase("add")) {
                se.tags.add(tag);
            } else {
                se.tags.remove(tag);
            }
            messages.send(s, "arena.value-set", MessageService.p("key", "tags"), MessageService.p("value", String.join(", ", se.tags)));
        })));
        sub(new Sub("displayname", "displayname <MiniMessage name>", "Set the display name", true, 1, (s, a) -> session(s, se -> {
            se.displayName = String.join(" ", a);
            messages.send(s, "arena.value-set", MessageService.p("key", "display name"), MessageService.p("value", se.displayName));
        })));
        sub(new Sub("icon", "icon", "Use the item in your hand as icon", true, 0, (s, a) -> session(s, se -> {
            Material type = ((Player) s).getInventory().getItemInMainHand().getType();
            if (type.isAir()) {
                messages.send(s, "arena.hold-item");
                return;
            }
            se.icon = type;
            messages.send(s, "arena.value-set", MessageService.p("key", "icon"), MessageService.p("value", type.name()));
        })));
        sub(new Sub("save", "save", "Capture the selection and save the arena", true, 0, (s, a) -> editor.save((Player) s)));
        sub(new Sub("cancel", "cancel", "Discard your edit session", true, 0, (s, a) -> editor.cancel((Player) s)));
        sub(new Sub("enable", "enable <arena>", "Enable an arena", false, 1, (s, a) -> toggle(s, a[0], true)));
        sub(new Sub("disable", "disable <arena>", "Disable an arena", false, 1, (s, a) -> toggle(s, a[0], false)));
        sub(new Sub("delete", "delete <arena>", "Delete an arena and its template", false, 1, (s, a) -> {
            if (arenas.delete(a[0])) {
                messages.send(s, "arena.deleted", MessageService.p("arena", a[0]));
            } else {
                messages.send(s, "arena.not-found", MessageService.p("arena", a[0]));
            }
        }));
        sub(new Sub("tp", "tp <arena>", "Teleport to a free copy of an arena", true, 1, (s, a) -> teleport((Player) s, a[0])));
        sub(new Sub("buildarea", "buildarea <1|2|clear>", "Limit block placing to a box (corners at your feet)", true, 1, (s, a) -> session(s, se -> {
            Player player = (Player) s;
            switch (a[0].toLowerCase(java.util.Locale.ROOT)) {
                case "1" -> {
                    se.buildArea1 = player.getLocation().getBlock().getLocation();
                    messages.send(s, "arena.build-area-set", MessageService.p("corner", "1"), MessageService.p("location", ArenaEditor.format(se.buildArea1)));
                }
                case "2" -> {
                    se.buildArea2 = player.getLocation().getBlock().getLocation();
                    messages.send(s, "arena.build-area-set", MessageService.p("corner", "2"), MessageService.p("location", ArenaEditor.format(se.buildArea2)));
                }
                case "clear" -> {
                    se.buildArea = null;
                    se.buildArea1 = null;
                    se.buildArea2 = null;
                    messages.send(s, "arena.build-area-cleared");
                }
                default -> usageOf(s, "buildarea <1|2|clear>");
            }
        })));
        sub(new Sub("import", "import [file] [name] [save]", "Import a .schem/.schematic/.arena from plugins/PvPCore/imports", true, 0, (s, a) -> {
            if (a.length == 0) {
                importer.list(s);
                return;
            }
            boolean save = a.length > 1 && a[a.length - 1].equalsIgnoreCase("save");
            String name = a.length > 2 || (a.length == 2 && !save) ? a[1] : null;
            importer.importSchematic((Player) s, a[0], name, save);
        }));
        sub(new Sub("importworld", "importworld <folder>", "Load a world folder from plugins/PvPCore/imports to capture arenas from", true, 1,
                (s, a) -> importer.importWorld((Player) s, a[0])));
        sub(new Sub("generate", "generate <arena|all>", "Regenerate built-in arenas from code", false, 1, (s, a) -> {
            java.util.Set<String> builtins = net.pvpserver.core.arena.gen.BuiltinArenas.names();
            List<String> names = a[0].equalsIgnoreCase("all") ? List.of() : List.of(a[0].toLowerCase(java.util.Locale.ROOT));
            if (!names.isEmpty() && !builtins.contains(names.get(0))) {
                messages.send(s, "arena.unknown-builtin", MessageService.p("arena", a[0]), MessageService.p("list", String.join(", ", builtins)));
                return;
            }
            messages.send(s, "arena.generating", MessageService.p("count", names.isEmpty() ? builtins.size() : 1));
            arenas.regenerateBuiltins(names).whenComplete((done, error) -> Tasks.sync(() -> {
                if (error != null) {
                    messages.send(s, "arena.save-failed");
                    return;
                }
                messages.send(s, "arena.generated", MessageService.p("arenas", String.join(", ", done)));
            }));
        }));
    }

    private void list(CommandSender sender) {
        messages.send(sender, "arena.list-header", MessageService.p("count", arenas.arenas().size()));
        for (Arena arena : arenas.arenas()) {
            messages.send(sender, "arena.list-entry", MessageService.p("arena", arena.name()),
                    MessageService.p("tags", String.join(", ", arena.tags())),
                    MessageService.p("status", !arena.enabled() ? "disabled" : arenas.template(arena.name()) == null ? "no template"
                            : !arena.complete() ? "incomplete" : "ready"));
        }
    }

    private void info(CommandSender sender, String name) {
        Arena arena = arenas.arena(name);
        if (arena == null) {
            messages.send(sender, "arena.not-found", MessageService.p("arena", name));
            return;
        }
        ArenaTemplate template = arenas.template(arena.name());
        messages.send(sender, "arena.info", MessageService.p("arena", arena.name()),
                MessageService.p("tags", String.join(", ", arena.tags())),
                MessageService.p("size", template == null ? "?" : template.sizeX() + "x" + template.sizeY() + "x" + template.sizeZ()),
                MessageService.p("build_limit", arena.buildLimit()), MessageService.p("void_y", arena.voidY()),
                MessageService.p("enabled", arena.enabled()),
                MessageService.p("goals", arena.goalA() != null && arena.goalB() != null ? "yes" : "no"),
                MessageService.p("build_area", arena.buildArea() == null ? "whole arena" : arena.buildArea().serialize()));
    }

    private void status(CommandSender sender) {
        long inUse = arenas.instances().stream().filter(i -> i.state() == ArenaInstance.State.IN_USE).count();
        messages.send(sender, "arena.status", MessageService.p("instances", arenas.instances().size()),
                MessageService.p("in_use", inUse), MessageService.p("idle", arenas.idleCount()),
                MessageService.p("pending", arenas.pendingRequests()), MessageService.p("paste_jobs", arenas.paster().pending()));
    }

    private void toggle(CommandSender sender, String name, boolean enabled) {
        if (arenas.setEnabled(name, enabled)) {
            messages.send(sender, enabled ? "arena.enabled" : "arena.disabled", MessageService.p("arena", name));
        } else {
            messages.send(sender, "arena.not-found", MessageService.p("arena", name));
        }
    }

    private void teleport(Player player, String name) {
        Arena arena = arenas.arena(name);
        if (arena == null) {
            messages.send(player, "arena.not-found", MessageService.p("arena", name));
            return;
        }
        arenas.acquire(a -> a.name().equals(arena.name()), arena.name()).whenComplete((instance, error) -> Tasks.sync(() -> {
            if (error != null) {
                messages.send(player, "arena.not-available", MessageService.p("arena", name));
                return;
            }
            player.teleport(instance.spectatorSpawn());
            messages.send(player, "arena.teleported", MessageService.p("arena", arena.name()));
            // Give the copy back after a minute so the pool is not starved by admins looking around.
            Tasks.later(() -> arenas.release(instance), 20L * 60);
        }));
    }

    private void session(CommandSender sender, java.util.function.Consumer<ArenaEditSession> action) {
        ArenaEditSession session = editor.session((Player) sender);
        if (session == null) {
            messages.send(sender, "arena.no-session");
            return;
        }
        action.accept(session);
    }

    private void posSet(CommandSender sender, String pos, org.bukkit.Location location) {
        messages.send(sender, "arena.pos-set", MessageService.p("pos", pos), MessageService.p("location", ArenaEditor.format(location)));
    }

    private void usageOf(CommandSender sender, String usage) {
        messages.send(sender, "command.usage", MessageService.p("usage", "/arena " + usage));
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private final class Sub extends SubCommand {
        private final BiConsumer<CommandSender, String[]> action;

        Sub(String name, String usage, String description, boolean playerOnly, int minArgs, BiConsumer<CommandSender, String[]> action) {
            super(name, List.of(), PERMISSION, usage, description, playerOnly, minArgs);
            this.action = action;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            action.accept(sender, args);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            if (args.length != 1 && !(name().equals("tag") && args.length == 2)) {
                return List.of();
            }
            return switch (name()) {
                case "info", "edit", "enable", "disable", "delete", "tp" -> arenas.arenas().stream().map(Arena::name).toList();
                case "setspawn" -> List.of("a", "b", "spectator");
                case "setgoal" -> List.of("a", "b");
                case "tag" -> args.length == 1 ? List.of("add", "remove") : List.of("standard", "build", "sumo", "boxing", "bridge", "spleef");
                case "buildarea" -> List.of("1", "2", "clear");
                case "import" -> importer.candidates().stream().filter(n -> !n.endsWith("/")).toList();
                case "importworld" -> importer.candidates().stream().filter(n -> n.endsWith("/")).map(n -> n.substring(0, n.length() - 1)).toList();
                case "generate" -> {
                    List<String> names = new java.util.ArrayList<>(net.pvpserver.core.arena.gen.BuiltinArenas.names());
                    names.add(0, "all");
                    yield names;
                }
                default -> List.of();
            };
        }
    }
}
