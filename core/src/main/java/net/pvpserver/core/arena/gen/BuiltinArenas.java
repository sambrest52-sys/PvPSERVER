package net.pvpserver.core.arena.gen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The procedurally generated arena set shipped with the plugin. Every arena is built from code (no schematic files),
 * deterministically, so {@code /arena generate} always recreates the same map.
 */
public final class BuiltinArenas {

    /**
     * Built-in arenas by name, in menu order. Bump {@link #VERSION} when a generator changes so installs can
     * regenerate their copies.
     */
    private static final Map<String, Supplier<GeneratedArena>> ARENAS;

    /** Generator version recorded next to generated arenas. */
    public static final int VERSION = 2;

    static {
        Map<String, Supplier<GeneratedArena>> map = new LinkedHashMap<>();
        map.put("colosseum", StandardArenas::colosseum);
        map.put("mossy_ruins", StandardArenas::mossyRuins);
        map.put("frozen_lake", StandardArenas::frozenLake);
        map.put("nether_keep", StandardArenas::netherKeep);
        map.put("sky_temple", StandardArenas::skyTemple);
        map.put("sumo_dojo", SumoArenas::dojo);
        map.put("sumo_lotus", SumoArenas::lotus);
        map.put("sumo_skyring", SumoArenas::skyRing);
        map.put("sumo_islet", SumoArenas::islet);
        map.put("boxing_ring", BoxingArenas::championship);
        map.put("boxing_gym", BoxingArenas::gym);
        map.put("boxing_rooftop", BoxingArenas::rooftop);
        map.put("uhc_plains", BuildUhcArenas::plains);
        map.put("uhc_taiga", BuildUhcArenas::taiga);
        map.put("uhc_mesa", BuildUhcArenas::mesa);
        map.put("bridge_classic", BridgeArenas::classic);
        map.put("bridge_ruins", BridgeArenas::ruins);
        map.put("bridge_nether", BridgeArenas::nether);
        map.put("spleef_classic", SpleefArenas::classic);
        map.put("spleef_layers", SpleefArenas::layers);
        map.put("spleef_lava", SpleefArenas::lavaPit);
        ARENAS = Collections.unmodifiableMap(map);
    }

    private BuiltinArenas() {
    }

    /** @return built-in arena names in order */
    public static Set<String> names() {
        return ARENAS.keySet();
    }

    /**
     * @param name arena name
     * @return a freshly generated arena
     * @throws IllegalArgumentException for an unknown name
     */
    public static GeneratedArena generate(String name) {
        Supplier<GeneratedArena> supplier = ARENAS.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown built-in arena: " + name);
        }
        return supplier.get();
    }
}
