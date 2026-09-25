package net.pvpserver.core.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * A sub command of a {@link BaseCommand} (e.g. {@code /party invite}).
 */
public abstract class SubCommand {

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final String usage;
    private final String description;
    private final boolean playerOnly;
    private final int minArgs;

    /**
     * @param name primary name
     * @param aliases alternative names
     * @param permission required permission or null
     * @param usage usage after the root label, e.g. {@code invite <player>}
     * @param description one line help text
     * @param playerOnly whether consoles are rejected
     * @param minArgs minimum argument count (excluding the sub command name)
     */
    protected SubCommand(String name, List<String> aliases, String permission, String usage, String description,
                         boolean playerOnly, int minArgs) {
        this.name = name;
        this.aliases = aliases;
        this.permission = permission;
        this.usage = usage;
        this.description = description;
        this.playerOnly = playerOnly;
        this.minArgs = minArgs;
    }

    /**
     * Executes with validated permission, sender type and argument count.
     *
     * @param sender sender
     * @param args arguments after the sub command name
     */
    public abstract void execute(CommandSender sender, String[] args);

    /**
     * @param sender sender
     * @param args arguments after the sub command name (last may be partial/empty)
     * @return suggestions
     */
    public List<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }

    /** @return primary name */
    public String name() {
        return name;
    }

    /** @return aliases */
    public List<String> aliases() {
        return aliases;
    }

    /** @return permission or null */
    public String permission() {
        return permission;
    }

    /** @return usage text */
    public String usage() {
        return usage;
    }

    /** @return description */
    public String description() {
        return description;
    }

    /** @return whether console is rejected */
    public boolean playerOnly() {
        return playerOnly;
    }

    /** @return minimum arguments */
    public int minArgs() {
        return minArgs;
    }

    /**
     * @param label typed label
     * @return whether the label selects this sub command
     */
    public boolean matches(String label) {
        if (name.equalsIgnoreCase(label)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }
}
