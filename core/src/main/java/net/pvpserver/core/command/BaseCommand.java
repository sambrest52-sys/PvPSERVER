package net.pvpserver.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.pvpserver.core.message.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Root command registered through Paper's Brigadier {@link BasicCommand} bridge. Handles permission checks,
 * player-only checks, usage messages, sub command dispatch and tab completion.
 */
public abstract class BaseCommand implements BasicCommand {

    private final String label;
    private final List<String> aliases;
    private final String permission;
    private final String description;
    private final String usage;
    private final boolean playerOnly;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    protected final MessageService messages;

    /**
     * @param messages message catalogue for errors/usage
     * @param label root label
     * @param aliases aliases
     * @param permission root permission (null = everyone)
     * @param description description shown in /help
     * @param usage usage after the label for the root (used when there are no sub commands)
     * @param playerOnly whether console is rejected for the root handler
     */
    protected BaseCommand(MessageService messages, String label, List<String> aliases, String permission, String description,
                          String usage, boolean playerOnly) {
        this.messages = messages;
        this.label = label;
        this.aliases = aliases;
        this.permission = permission;
        this.description = description;
        this.usage = usage;
        this.playerOnly = playerOnly;
    }

    /**
     * @param subCommand sub command to add
     */
    protected void sub(SubCommand subCommand) {
        subCommands.put(subCommand.name().toLowerCase(Locale.ROOT), subCommand);
    }

    @Override
    public final void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (permission != null && !sender.hasPermission(permission)) {
            messages.send(sender, "command.no-permission");
            return;
        }
        if (!subCommands.isEmpty() && args.length > 0) {
            SubCommand sub = find(args[0]);
            if (sub != null) {
                String[] rest = Arrays.copyOfRange(args, 1, args.length);
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                    messages.send(sender, "command.no-permission");
                    return;
                }
                if (sub.playerOnly() && !(sender instanceof Player)) {
                    messages.send(sender, "command.player-only");
                    return;
                }
                if (rest.length < sub.minArgs()) {
                    messages.send(sender, "command.usage", MessageService.p("usage", "/" + label + " " + sub.usage()));
                    return;
                }
                try {
                    sub.execute(sender, rest);
                } catch (RuntimeException e) {
                    messages.send(sender, "command.error");
                    throw e;
                }
                return;
            }
        }
        if (playerOnly && !(sender instanceof Player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        try {
            onCommand(sender, args);
        } catch (RuntimeException e) {
            messages.send(sender, "command.error");
            throw e;
        }
    }

    /**
     * Root handler, called when no sub command matched. Default shows help.
     *
     * @param sender sender
     * @param args arguments
     */
    protected void onCommand(CommandSender sender, String[] args) {
        sendHelp(sender);
    }

    /**
     * Sends the usage line or the sub command list.
     *
     * @param sender receiver
     */
    protected void sendHelp(CommandSender sender) {
        if (subCommands.isEmpty()) {
            messages.send(sender, "command.usage", MessageService.p("usage", "/" + label + (usage.isEmpty() ? "" : " " + usage)));
            return;
        }
        messages.send(sender, "command.help-header", MessageService.p("command", label));
        for (SubCommand sub : subCommands.values()) {
            if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                messages.send(sender, "command.help-entry", MessageService.p("usage", "/" + label + " " + sub.usage()),
                        MessageService.p("description", sub.description()));
            }
        }
    }

    /**
     * Sends a usage message for the root command.
     *
     * @param sender receiver
     */
    protected void usage(CommandSender sender) {
        messages.send(sender, "command.usage", MessageService.p("usage", "/" + label + (usage.isEmpty() ? "" : " " + usage)));
    }

    @Override
    public final Collection<String> suggest(CommandSourceStack source, String[] rawArgs) {
        CommandSender sender = source.getSender();
        String[] args = rawArgs.length == 0 ? new String[]{""} : rawArgs;
        if (!subCommands.isEmpty()) {
            if (args.length == 1) {
                List<String> names = new ArrayList<>();
                for (SubCommand sub : subCommands.values()) {
                    if (sub.permission() == null || sender.hasPermission(sub.permission())) {
                        names.add(sub.name());
                    }
                }
                names.addAll(onSuggest(sender, args));
                return Suggest.filter(names, args[0]);
            }
            SubCommand sub = find(args[0]);
            if (sub != null) {
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                    return List.of();
                }
                String[] rest = Arrays.copyOfRange(args, 1, args.length);
                return Suggest.filter(sub.suggest(sender, rest), rest[rest.length - 1]);
            }
        }
        return Suggest.filter(onSuggest(sender, args), args[args.length - 1]);
    }

    /**
     * Root tab completion (no sub command matched).
     *
     * @param sender sender
     * @param args arguments (last may be partial)
     * @return suggestions (filtered by the caller)
     */
    protected List<String> onSuggest(CommandSender sender, String[] args) {
        return List.of();
    }

    private SubCommand find(String name) {
        SubCommand direct = subCommands.get(name.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        for (SubCommand sub : subCommands.values()) {
            if (sub.matches(name)) {
                return sub;
            }
        }
        return null;
    }

    @Override
    public @Nullable String permission() {
        return permission;
    }

    /** @return root label */
    public String label() {
        return label;
    }

    /** @return aliases */
    public List<String> aliases() {
        return aliases;
    }

    /** @return description */
    public String description() {
        return description;
    }

    /** @return registered sub commands */
    public Collection<SubCommand> subCommands() {
        return subCommands.values();
    }
}
