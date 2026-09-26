package net.pvpserver.lobby.feature;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.NpcDefinition;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin-native NPCs: a player-shaped {@link Mannequin} (skin, armour, held items) with a text display above it
 * whose live counts refresh on a timer. Left or right clicking runs the NPC's action; NPCs cannot be hurt, pushed or
 * dressed. No Citizens or other NPC plugin is involved.
 */
public final class NpcService implements Listener {

    private static final double NAME_HEIGHT = 2.05;

    private final PvPLobby plugin;
    private final Displays displays;
    private final Map<UUID, String> byEntity = new HashMap<>();
    private final Map<String, Mannequin> bodies = new LinkedHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    /**
     * @param plugin lobby plugin
     * @param displays display manager
     */
    public NpcService(PvPLobby plugin, Displays displays) {
        this.plugin = plugin;
        this.displays = displays;
    }

    /** Spawns every NPC that has both a definition in npcs.yml and a position in layout.yml. */
    public void spawnAll() {
        despawnAll();
        if (!plugin.settings().npcs().enabled()) {
            return;
        }
        World world = plugin.lobbyWorld().world();
        Map<String, Point> positions = plugin.lobbyWorld().layout().npcs();
        for (NpcDefinition definition : plugin.npcDefinitions().values()) {
            Point point = positions.get(definition.id());
            if (point == null) {
                continue;
            }
            if (!displays.reserve(1)) {
                plugin.getLogger().warning("NPC " + definition.id() + " skipped: performance.max-entities reached");
                continue;
            }
            Location at = point.at(world);
            displays.ticket(at);
            Mannequin body = world.spawn(at, Mannequin.class, npc -> dress(npc, definition));
            bodies.put(definition.id(), body);
            byEntity.put(body.getUniqueId(), definition.id());
            displays.text("npc:" + definition.id(), at.clone().add(0, NAME_HEIGHT, 0), Display.Billboard.CENTER, 1f, 0,
                    render(definition, LiveStats.empty()));
        }
    }

    private void dress(Mannequin npc, NpcDefinition definition) {
        displays.tag(npc, "npc");
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setGravity(false);
        npc.setImmovable(true);
        npc.setCollidable(false);
        npc.setCustomNameVisible(false);
        npc.setDescription(null);
        npc.setGlowing(definition.glowing());
        String skin = definition.skin();
        try {
            if (skin.startsWith("name:")) {
                npc.setProfile(ResolvableProfile.resolvableProfile().name(skin.substring(5).trim()).build());
            } else if (skin.startsWith("texture:")) {
                String[] parts = skin.substring(8).trim().split(";", 2);
                ProfileProperty textures = parts.length == 2 ? new ProfileProperty("textures", parts[0], parts[1])
                        : new ProfileProperty("textures", parts[0]);
                npc.setProfile(ResolvableProfile.resolvableProfile().addProperty(textures).build());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("NPC " + definition.id() + ": could not apply skin " + skin + " (" + e.getMessage() + ")");
        }
        for (Map.Entry<EquipmentSlot, NpcDefinition.Item> entry : definition.equipment().entrySet()) {
            try {
                ItemStack stack = new ItemStack(entry.getValue().material());
                if (entry.getValue().color() != null && stack.getItemMeta() instanceof LeatherArmorMeta) {
                    stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(entry.getValue().color()));
                }
                npc.getEquipment().setItem(entry.getKey(), stack);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("NPC " + definition.id() + ": cannot hold " + entry.getValue().material());
            }
        }
    }

    /**
     * Renders an NPC's hologram (safe off the main thread).
     *
     * @param definition NPC
     * @param stats counts
     * @return text
     */
    public Component render(NpcDefinition definition, LiveStats stats) {
        List<String> lines = definition.hologram();
        if (lines.isEmpty()) {
            return Component.empty();
        }
        return plugin.messages().parse(String.join("<newline>", lines), stats.resolver());
    }

    /** Turns NPC heads towards the nearest player in range. */
    public void lookAtPlayers() {
        double range = plugin.settings().npcs().lookRange();
        for (Mannequin body : bodies.values()) {
            if (!body.isValid()) {
                continue;
            }
            Location at = body.getLocation();
            Player nearest = null;
            double best = range * range;
            for (Player player : at.getNearbyPlayers(range)) {
                double d = player.getLocation().distanceSquared(at);
                if (d < best) {
                    best = d;
                    nearest = player;
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                double dx = eye.getX() - at.getX();
                double dy = eye.getY() - (at.getY() + 1.62);
                double dz = eye.getZ() - at.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                body.setRotation(yaw, pitch);
            }
        }
    }

    /** @return whether an NPC body was removed by something else (e.g. /kill) */
    public boolean anyInvalid() {
        return bodies.values().stream().anyMatch(body -> !body.isValid());
    }

    /** Removes NPC bodies (their text displays go with {@link Displays#clear}). */
    public void despawnAll() {
        bodies.values().forEach(Entity::remove);
        bodies.clear();
        byEntity.clear();
    }

    /**
     * @param id NPC id
     * @return spawned body or null
     */
    public Mannequin body(String id) {
        return bodies.get(id);
    }

    /** @return spawned NPC ids */
    public java.util.Set<String> ids() {
        return bodies.keySet();
    }

    private boolean click(Player player, Entity entity) {
        String id = byEntity.get(entity.getUniqueId());
        if (id == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().npcs().clickCooldownMillis()) {
            return true;
        }
        lastClick.put(player.getUniqueId(), now);
        NpcDefinition definition = plugin.npcDefinitions().get(id);
        if (definition != null) {
            plugin.features().actions().run(player, definition.action());
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND ? click(event.getPlayer(), event.getRightClicked())
                : byEntity.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (byEntity.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (click(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (displays.isLobbyEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (displays.isLobbyEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Forgets a player's click cooldown.
     *
     * @param uuid player
     */
    public void forget(UUID uuid) {
        lastClick.remove(uuid);
    }
}
