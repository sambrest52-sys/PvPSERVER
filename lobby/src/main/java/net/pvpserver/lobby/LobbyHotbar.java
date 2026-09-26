package net.pvpserver.lobby;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.gui.ItemTemplates;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.core.state.PlayerState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the lobby hotbars (lobby, queue, party leader, party member) from hotbar.yml.
 */
public final class LobbyHotbar {

    private final PracticeApi api;
    private final ConfigFile file;
    private final MessageService messages;
    private final Map<String, Map<Integer, ItemStack>> layouts = new HashMap<>();
    private Function<Player, String> override = player -> null;

    /**
     * @param api practice api
     * @param file hotbar.yml
     * @param messages lobby messages
     */
    public LobbyHotbar(PracticeApi api, ConfigFile file, MessageService messages) {
        this.api = api;
        this.file = file;
        this.messages = messages;
        reload();
    }

    /** Re-reads hotbar.yml. */
    public void reload() {
        file.reload();
        layouts.clear();
        ConfigurationSection root = file.get().getConfigurationSection("layouts");
        if (root == null) {
            return;
        }
        for (String layout : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(layout);
            if (section == null) {
                continue;
            }
            Map<Integer, ItemStack> items = new HashMap<>();
            for (String slotKey : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(slotKey);
                if (item == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(slotKey);
                    String action = item.getString("action", "none");
                    ItemStack stack = ItemTemplates.build(item, Material.PAPER, messages);
                    items.put(slot, api.hotbar().tag(stack, action));
                } catch (NumberFormatException ignored) {
                    // slot keys must be numbers
                }
            }
            layouts.put(layout, items);
        }
    }

    /**
     * @param chooser picks a layout before the normal rules (e.g. "parkour" during a run), or returns null
     */
    public void override(Function<Player, String> chooser) {
        this.override = chooser;
    }

    /**
     * Gives the layout matching the player's lobby situation.
     *
     * @param player player
     */
    public void give(Player player) {
        String layout = override.apply(player);
        Optional<Party> party = api.parties().partyOf(player);
        if (layout != null && layouts.containsKey(layout)) {
            // chosen by the override
        } else if (api.states().is(player, PlayerState.QUEUE)) {
            layout = party.isPresent() ? "party-queue" : "queue";
        } else if (party.isPresent()) {
            layout = party.get().isLeader(player.getUniqueId()) ? "party-leader" : "party-member";
        } else {
            layout = "lobby";
        }
        Map<Integer, ItemStack> items = layouts.getOrDefault(layout, layouts.getOrDefault("lobby", Map.of()));
        player.getInventory().clear();
        items.forEach((slot, item) -> player.getInventory().setItem(slot, item.clone()));
        player.getInventory().setHeldItemSlot(file.get().getInt("held-slot", 0));
        player.updateInventory();
    }
}
