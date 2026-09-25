package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.InventorySnapshot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only view of an {@link InventorySnapshot}: storage, armor, offhand plus health, food, effects and match stats.
 */
public final class SnapshotMenu extends Menu {

    private final PvPDuels plugin;
    private final InventorySnapshot snapshot;

    /**
     * @param plugin duels plugin
     * @param viewer viewer
     * @param snapshot snapshot
     */
    public SnapshotMenu(PvPDuels plugin, Player viewer, InventorySnapshot snapshot) {
        super(viewer);
        this.plugin = plugin;
        this.snapshot = snapshot;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString("titles.inventory", "<dark_gray><player>'s Inventory"),
                MessageService.p("player", snapshot.name()));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        ItemStack[] contents = snapshot.contents();
        // Inventory rows first (9-35) then the hotbar (0-8) in the fourth row, like the player inventory.
        for (int i = 9; i < 36 && i < contents.length; i++) {
            if (contents[i] != null) {
                set(i - 9, Button.display(contents[i]));
            }
        }
        for (int i = 0; i < 9 && i < contents.length; i++) {
            if (contents[i] != null) {
                set(27 + i, Button.display(contents[i]));
            }
        }
        ItemStack[] armor = snapshot.armor();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].getType().isAir()) {
                set(36 + (3 - i), Button.display(armor[i]));
            }
        }
        if (snapshot.offhand() != null && !snapshot.offhand().getType().isAir()) {
            set(40, Button.display(snapshot.offhand()));
        }
        TagResolver resolvers = TagResolver.resolver(
                MessageService.p("health", String.format(Locale.ROOT, "%.1f", snapshot.health() / 2.0)),
                MessageService.p("food", snapshot.food()),
                MessageService.p("hits", snapshot.hits()),
                MessageService.p("combo", snapshot.longestCombo()),
                MessageService.p("potions", snapshot.healthPotions()),
                MessageService.p("thrown", snapshot.potionsThrown()),
                MessageService.p("missed", snapshot.potionsMissed()));
        set(45, Button.display(ItemBuilder.of(Material.GLISTERING_MELON_SLICE)
                .name(plugin.messages().get("inventory.health", resolvers)).build()));
        set(46, Button.display(ItemBuilder.of(Material.COOKED_BEEF)
                .name(plugin.messages().get("inventory.food", resolvers)).build()));
        List<Component> effectLore = new ArrayList<>();
        for (PotionEffect effect : snapshot.effects()) {
            String duration = effect.isInfinite() ? "∞" : net.pvpserver.core.util.TimeUtil.formatClock(effect.getDuration() * 50L);
            effectLore.add(plugin.messages().get("inventory.effect-line",
                    MessageService.p("effect", effect.getType().getKey().getKey().replace('_', ' ')),
                    MessageService.p("level", effect.getAmplifier() + 1), MessageService.p("duration", duration)));
        }
        if (effectLore.isEmpty()) {
            effectLore.add(plugin.messages().get("inventory.no-effects"));
        }
        set(47, Button.display(ItemBuilder.of(Material.BREWING_STAND)
                .name(plugin.messages().get("inventory.effects")).lore(effectLore).build()));
        set(48, Button.display(ItemBuilder.of(Material.SPLASH_POTION)
                .name(plugin.messages().get("inventory.potions", resolvers))
                .lore(plugin.messages().getList("inventory.potions-lore", resolvers)).hideFlags().build()));
        set(49, Button.display(ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(plugin.messages().get("inventory.stats", resolvers))
                .lore(plugin.messages().getList("inventory.stats-lore", resolvers)).hideFlags().build()));
    }
}
