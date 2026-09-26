package net.pvpserver.lobby.feature;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every display entity the lobby spawns (hologram text, NPC name plates, leaderboard panels, floating icons). They
 * are non-persistent and tagged, so a crash never leaves duplicates behind, and they stay under a hard cap. Text
 * changes are queued and applied a few per tick, so refreshing dozens of holograms never lands in a single tick.
 */
public final class Displays {

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Map<String, TextDisplay> texts = new LinkedHashMap<>();
    private final Map<String, ItemDisplay> items = new LinkedHashMap<>();
    private final Map<String, Component> current = new LinkedHashMap<>();
    private final Map<String, Component> pending = new LinkedHashMap<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private int cap = 120;
    private int perTick = 8;
    private int refused;

    /**
     * @param plugin lobby plugin
     */
    public Displays(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "lobby_entity");
    }

    /** @return PDC key marking lobby entities */
    public NamespacedKey key() {
        return key;
    }

    /**
     * @param maxEntities entity cap (NPC bodies count through {@link #reserve})
     * @param updatesPerTick text updates applied per tick
     */
    public void limits(int maxEntities, int updatesPerTick) {
        this.cap = maxEntities;
        this.perTick = Math.max(1, updatesPerTick);
    }

    private int reserved;

    /**
     * Counts entities spawned elsewhere (NPC bodies) against the cap.
     *
     * @param count entities
     * @return whether they fit
     */
    public boolean reserve(int count) {
        if (size() + count > cap) {
            refused += count;
            return false;
        }
        reserved += count;
        return true;
    }

    /** @return spawned entities including reservations */
    public int size() {
        return texts.size() + items.size() + reserved;
    }

    /** @return entities that were not spawned because of the cap since the last clear */
    public int refused() {
        return refused;
    }

    /** Removes every lobby entity in the world (including leftovers from a crash). */
    public void clear(World world) {
        texts.values().forEach(Entity::remove);
        items.values().forEach(Entity::remove);
        texts.clear();
        items.clear();
        current.clear();
        pending.clear();
        queue.clear();
        reserved = 0;
        refused = 0;
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Tags an entity as a lobby entity.
     *
     * @param entity entity
     * @param kind kind (npc, text, item)
     */
    public void tag(Entity entity, String kind) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind);
        entity.setPersistent(false);
    }

    /**
     * @param entity entity
     * @return whether the lobby spawned it
     */
    public boolean isLobbyEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /**
     * Spawns a text display.
     *
     * @param id unique id
     * @param at location
     * @param billboard billboard (CENTER for holograms, FIXED for panels)
     * @param scale text scale
     * @param background ARGB background, or 0 for the default
     * @param text initial text
     * @return display, or null when over the cap or the chunk is not loaded
     */
    public TextDisplay text(String id, Location at, Display.Billboard billboard, float scale, int background, Component text) {
        remove(id);
        if (size() >= cap) {
            refused++;
            return null;
        }
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
            tag(spawned, "text");
            spawned.setBillboard(billboard);
            spawned.setShadowed(true);
            spawned.setSeeThrough(false);
            spawned.setLineWidth(260);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            if (background != 0) {
                spawned.setDefaultBackground(false);
                spawned.setBackgroundColor(Color.fromARGB(background));
            }
            if (scale != 1f) {
                spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                        new AxisAngle4f()));
            }
            spawned.text(text);
        });
        texts.put(id, display);
        current.put(id, text);
        return display;
    }

    /**
     * Spawns a floating item that spins slowly (client-side interpolation, one update every few seconds).
     *
     * @param id unique id
     * @param at location
     * @param item item
     * @param scale size
     * @return display or null when over the cap
     */
    public ItemDisplay item(String id, Location at, ItemStack item, float scale) {
        ItemDisplay old = items.remove(id);
        if (old != null) {
            old.remove();
        }
        if (size() >= cap) {
            refused++;
            return null;
        }
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            tag(spawned, "item");
            spawned.setItemStack(item);
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                    new AxisAngle4f()));
        });
        items.put(id, display);
        return display;
    }

    /**
     * Turns every floating item half a turn further, interpolated over {@code ticks}.
     *
     * @param angle new angle in radians
     * @param ticks interpolation duration
     */
    public void spinItems(float angle, int ticks) {
        for (ItemDisplay display : items.values()) {
            if (!display.isValid()) {
                continue;
            }
            Transformation old = display.getTransformation();
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(ticks);
            display.setTransformation(new Transformation(old.getTranslation(), new AxisAngle4f(angle, 0, 1, 0), old.getScale(),
                    new AxisAngle4f()));
        }
    }

    /**
     * Queues a text change; applied within the next ticks unless it equals what is shown.
     *
     * @param id display id
     * @param text new text
     */
    public void update(String id, Component text) {
        if (!texts.containsKey(id) || Objects.equals(current.get(id), text)) {
            pending.remove(id);
            return;
        }
        if (pending.put(id, text) == null) {
            queue.add(id);
        }
    }

    /** Applies up to the per-tick budget of queued text changes. Called every tick. */
    public void tick() {
        for (int i = 0; i < perTick && !queue.isEmpty(); i++) {
            String id = queue.poll();
            Component text = pending.remove(id);
            TextDisplay display = texts.get(id);
            if (text != null && display != null && display.isValid()) {
                display.text(text);
                current.put(id, text);
            }
        }
    }

    /** @return queued text changes */
    public int queued() {
        return queue.size();
    }

    /**
     * @param id display id
     * @return spawned display or null
     */
    public TextDisplay textDisplay(String id) {
        return texts.get(id);
    }

    /** @return ids of spawned text displays */
    public List<String> textIds() {
        return new ArrayList<>(texts.keySet());
    }

    /**
     * @param id display id
     * @return current text
     */
    public Component currentText(String id) {
        return current.get(id);
    }

    /**
     * Removes one text display.
     *
     * @param id id
     */
    public void remove(String id) {
        TextDisplay display = texts.remove(id);
        if (display != null) {
            display.remove();
        }
        current.remove(id);
        pending.remove(id);
    }

    /** @return plugin */
    JavaPlugin plugin() {
        return plugin;
    }
}
