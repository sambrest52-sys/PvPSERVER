package net.pvpserver.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed registry of bridge implementations contributed by gamemode plugins (see {@code api.bridge}).
 */
public final class BridgeRegistry {

    private final Map<Class<?>, Object> bridges = new ConcurrentHashMap<>();

    /**
     * @param type bridge interface
     * @param implementation implementation
     * @param <T> bridge type
     */
    public <T> void register(Class<T> type, T implementation) {
        bridges.put(type, implementation);
    }

    /**
     * @param type bridge interface
     * @param <T> bridge type
     */
    public <T> void unregister(Class<T> type) {
        bridges.remove(type);
    }

    /**
     * @param type bridge interface
     * @param <T> bridge type
     * @return implementation if a plugin provides it
     */
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable(type.cast(bridges.get(type)));
    }
}
