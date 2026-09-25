package net.pvpserver.core.command;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Registers {@link BaseCommand}s with Paper's command lifecycle event.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    /**
     * Registers commands for the plugin. Must be called during {@code onEnable}.
     *
     * @param plugin owning plugin
     * @param commands commands
     */
    public static void register(JavaPlugin plugin, List<? extends BaseCommand> commands) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (BaseCommand command : commands) {
                event.registrar().register(command.label(), command.description(), command.aliases(), command);
            }
        });
    }
}
