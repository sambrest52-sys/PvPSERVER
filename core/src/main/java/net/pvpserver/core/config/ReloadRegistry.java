package net.pvpserver.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ordered collection of {@link Reloadable}s from every practice plugin, triggered by {@code /pvpadmin reload}.
 */
public final class ReloadRegistry {

    private final List<Named> entries = new ArrayList<>();

    /**
     * @param name human readable name shown in the reload report
     * @param reloadable component
     */
    public void register(String name, Reloadable reloadable) {
        entries.add(new Named(name, reloadable));
    }

    /**
     * Removes all components registered under a name prefix (used when a gamemode plugin disables).
     *
     * @param prefix name prefix
     */
    public void unregisterPrefix(String prefix) {
        entries.removeIf(e -> e.name.startsWith(prefix));
    }

    /**
     * Reloads everything, reporting failures without aborting the rest.
     *
     * @param onError receives "name: message" for each failure
     * @return number of components reloaded successfully
     */
    public int reloadAll(Consumer<String> onError) {
        int ok = 0;
        for (Named entry : List.copyOf(entries)) {
            try {
                entry.reloadable.reload();
                ok++;
            } catch (Exception e) {
                onError.accept(entry.name + ": " + e.getMessage());
            }
        }
        return ok;
    }

    private record Named(String name, Reloadable reloadable) {
    }
}
