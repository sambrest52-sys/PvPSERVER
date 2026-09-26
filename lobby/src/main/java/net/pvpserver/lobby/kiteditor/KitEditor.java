package net.pvpserver.lobby.kiteditor;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.api.bridge.KitEditorBridge;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-place kit editor. The player gets the kit's items (tagged with their definition slot) and rearranges them in
 * their own inventory while a small control menu (save / reset / cancel) is open. Closing the menu saves.
 */
public final class KitEditor implements KitEditorBridge, Listener {

    private final PvPLobby plugin;
    private final Map<UUID, Kit> sessions = new ConcurrentHashMap<>();

    /**
     * @param plugin lobby plugin
     */
    public KitEditor(PvPLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openKitEditor(Player player) {
        if (!plugin.api().states().is(player, PlayerState.LOBBY)) {
            plugin.messages().send(player, "kit-editor.busy");
            return;
        }
        new SelectMenu(player).open();
    }

    /**
     * Starts editing a kit.
     *
     * @param player player
     * @param kit kit
     */
    public void start(Player player, Kit kit) {
        if (!kit.editable()) {
            plugin.messages().send(player, "kit-editor.not-editable");
            return;
        }
        sessions.put(player.getUniqueId(), kit);
        plugin.api().states().set(player, PlayerState.EDITING);
        plugin.api().kits().giveForEditing(player, kit);
        plugin.messages().send(player, "kit-editor.started", MessageService.c("kit", kit.name()));
        new ControlMenu(player, kit).open();
    }

    private void finish(Player player, boolean save) {
        Kit kit = sessions.remove(player.getUniqueId());
        if (kit == null) {
            return;
        }
        if (save) {
            plugin.api().kits().saveLayoutFromInventory(player, kit);
            plugin.messages().send(player, "kit-editor.saved", MessageService.c("kit", kit.name()));
        } else {
            plugin.messages().send(player, "kit-editor.cancelled");
        }
        plugin.lobby().sendToLobby(player);
    }

    /**
     * @param player player
     * @return whether the player is editing
     */
    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (editing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private final class SelectMenu extends Menu {
        SelectMenu(Player viewer) {
            super(viewer);
        }

        @Override
        protected Component title() {
            return plugin.menus().title("kit-editor");
        }

        @Override
        protected int rows() {
            return 3;
        }

        @Override
        protected void build() {
            int slot = 10;
            for (Kit kit : plugin.api().kits().all()) {
                if (!kit.editable()) {
                    continue;
                }
                if (slot % 9 == 8) {
                    slot += 2;
                }
                if (slot >= 27) {
                    break;
                }
                boolean custom = !plugin.api().kits().layoutFor(viewer, kit).isIdentity();
                var item = ItemBuilder.of(kit.icon()).lore(kit.lore(plugin.menus().lore(custom ? "kit-editor-custom" : "kit-editor-default"))).build();
                set(slot++, new Button(item, (player, click) -> {
                    if (click.isRightClick() && custom) {
                        plugin.api().kits().saveLayout(player, kit, null);
                        plugin.messages().send(player, "kit-editor.reset", MessageService.c("kit", kit.name()));
                        refresh();
                        return;
                    }
                    player.closeInventory();
                    start(player, kit);
                }));
            }
            fill(plugin.menus().filler());
        }
    }

    private final class ControlMenu extends Menu {
        private final Kit kit;
        private boolean handled;

        ControlMenu(Player viewer, Kit kit) {
            super(viewer);
            this.kit = kit;
        }

        @Override
        protected Component title() {
            return plugin.menus().title("kit-editor-session", MessageService.c("kit", kit.name()));
        }

        @Override
        protected int rows() {
            return 1;
        }

        @Override
        public boolean allowBottomClicks() {
            return true;
        }

        @Override
        protected void build() {
            set(0, Button.display(ItemBuilder.of(kit.icon()).lore(plugin.menus().lore("kit-editor-help")).build()));
            set(3, new Button(plugin.menus().item("kit-editor.save", Material.LIME_DYE), (p, c) -> {
                handled = true;
                p.closeInventory();
                finish(p, true);
            }));
            set(5, new Button(plugin.menus().item("kit-editor.reset", Material.YELLOW_DYE), (p, c) -> {
                plugin.api().kits().saveLayout(p, kit, null);
                plugin.api().kits().giveForEditing(p, kit);
                plugin.messages().send(p, "kit-editor.reset", MessageService.c("kit", kit.name()));
            }));
            set(8, new Button(plugin.menus().item("kit-editor.cancel", Material.RED_DYE), (p, c) -> {
                handled = true;
                p.closeInventory();
                finish(p, false);
            }));
            fill(plugin.menus().filler());
        }

        @Override
        public void onClose() {
            if (handled) {
                return;
            }
            handled = true;
            // Closing with ESC saves; run next tick so the inventory state is final.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline() && editing(viewer)) {
                    finish(viewer, true);
                }
            });
        }
    }

    /** @return kits being edited */
    public List<UUID> editors() {
        return List.copyOf(sessions.keySet());
    }
}
