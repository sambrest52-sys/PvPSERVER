package net.pvpserver.core.hotbar;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Clickable, locked hotbar items (lobby, queue, party, spectator items). Items carry an action id in their PDC;
 * right-clicking runs the handler registered for that id by any practice plugin.
 */
public final class HotbarService implements Listener {

    private final NamespacedKey actionKey;
    private final Map<String, Consumer<Player>> actions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    /**
     * @param plugin core plugin
     */
    public HotbarService(JavaPlugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "hotbar_action");
    }

    /**
     * @param id action id referenced by hotbar config
     * @param handler handler
     */
    public void register(String id, Consumer<Player> handler) {
        actions.put(id, handler);
    }

    /**
     * @param item base item
     * @param actionId action id
     * @return tagged copy
     */
    public ItemStack tag(ItemStack item, String actionId) {
        ItemStack copy = item.clone();
        copy.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionId));
        return copy;
    }

    /**
     * @param item item
     * @return action id or null
     */
    public String actionOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        String action = actionOf(event.getItem());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            run(event.getPlayer(), action);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String action = actionOf(event.getPlayer().getInventory().getItemInMainHand());
        if (action != null) {
            event.setCancelled(true);
            run(event.getPlayer(), action);
        }
    }

    private void run(Player player, String action) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < 250) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        Consumer<Player> handler = actions.get(action);
        if (handler != null) {
            handler.accept(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (actionOf(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (actionOf(event.getMainHandItem()) != null || actionOf(event.getOffHandItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (actionOf(event.getCurrentItem()) != null || actionOf(event.getCursor()) != null) {
            event.setCancelled(true);
        }
        if (event.getHotbarButton() >= 0 && actionOf(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> actionOf(item) != null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
