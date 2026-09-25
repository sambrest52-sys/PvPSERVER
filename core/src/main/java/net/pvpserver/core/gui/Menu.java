package net.pvpserver.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Chest GUI bound to one viewer. Subclasses populate buttons in {@link #build()}; clicks are dispatched by
 * {@link MenuListener}. Menus are cheap and rebuilt on {@link #refresh()}.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player viewer;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Inventory inventory;

    /**
     * @param viewer player the menu is for
     */
    protected Menu(Player viewer) {
        this.viewer = viewer;
    }

    /** @return inventory title */
    protected abstract Component title();

    /** @return number of rows (1-6) */
    protected abstract int rows();

    /** Populates buttons with {@link #set(int, Button)}. */
    protected abstract void build();

    /**
     * @param slot slot
     * @param button button
     */
    protected void set(int slot, Button button) {
        if (slot >= 0 && slot < rows() * 9) {
            buttons.put(slot, button);
        }
    }

    /**
     * @param slot slot
     * @param item item
     * @param action action
     */
    protected void set(int slot, ItemStack item, Button.ClickAction action) {
        set(slot, new Button(item, action));
    }

    /**
     * Fills empty slots with a filler pane.
     *
     * @param material filler material
     */
    protected void fill(Material material) {
        ItemStack filler = ItemBuilder.of(material).name(Component.space()).hideFlags().build();
        for (int i = 0; i < rows() * 9; i++) {
            buttons.putIfAbsent(i, Button.display(filler));
        }
    }

    /** Opens (or re-opens) the menu for the viewer. Main thread only. */
    public void open() {
        buttons.clear();
        build();
        inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows())) * 9, title());
        render();
        viewer.openInventory(inventory);
    }

    /** Rebuilds the buttons in place without closing the inventory. */
    public void refresh() {
        if (inventory == null) {
            open();
            return;
        }
        buttons.clear();
        build();
        inventory.clear();
        render();
    }

    private void render() {
        for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
            if (entry.getKey() < inventory.getSize()) {
                inventory.setItem(entry.getKey(), entry.getValue().item());
            }
        }
    }

    /**
     * Called by the listener for clicks inside the top inventory.
     *
     * @param event click event (already cancelled unless {@link #allowBottomClicks()})
     */
    void handleClick(InventoryClickEvent event) {
        Button button = buttons.get(event.getRawSlot());
        if (button != null && button.action() != null) {
            ClickType click = event.getClick();
            // Inventory changes (open/close) are unsafe inside the click event; run on the next tick.
            Bukkit.getScheduler().runTask(net.pvpserver.core.util.Tasks.plugin(), () -> {
                if (viewer.isOnline()) {
                    button.action().click(viewer, click);
                }
            });
        }
    }

    /**
     * @return whether the viewer may move items in their own inventory while the menu is open (kit editor)
     */
    public boolean allowBottomClicks() {
        return false;
    }

    /** Called when the inventory closes. */
    public void onClose() {
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory == null ? Bukkit.createInventory(this, 9) : inventory;
    }

    /** @return viewer */
    public Player viewer() {
        return viewer;
    }
}
