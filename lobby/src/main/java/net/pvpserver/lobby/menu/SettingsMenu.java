package net.pvpserver.lobby.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.profile.TimeOfDay;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Toggles every {@link Setting}, cycles time of day and links to cosmetics.
 */
public final class SettingsMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final PvPLobby plugin;

    /**
     * @param plugin lobby plugin
     * @param viewer viewer
     */
    public SettingsMenu(PvPLobby plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.menus().title("settings");
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        PlayerProfile profile = plugin.api().profiles().get(viewer);
        if (profile == null) {
            return;
        }
        MenuConfig menus = plugin.menus();
        int index = 0;
        for (Setting setting : Setting.values()) {
            boolean value = profile.settings().is(setting);
            var item = ItemBuilder.of(menus.item("settings." + setting.key(), Material.PAPER))
                    .lore(menus.lore(value ? "setting-enabled" : "setting-disabled"))
                    .glow(value)
                    .build();
            set(SLOTS[index++], new Button(item, (player, click) -> {
                boolean now = profile.settings().toggle(setting);
                profile.markDirty();
                plugin.applySetting(player, setting);
                plugin.messages().send(player, now ? "settings.enabled" : "settings.disabled",
                        MessageService.p("setting", plainName(menus, setting)));
                refresh();
            }));
        }
        TimeOfDay time = profile.settings().timeOfDay();
        var timeItem = ItemBuilder.of(menus.item("settings.time", Material.CLOCK))
                .lore(menus.lore("time", MessageService.p("time", time.name().toLowerCase(java.util.Locale.ROOT))))
                .build();
        set(SLOTS[index++], new Button(timeItem, (player, click) -> {
            profile.settings().timeOfDay(time.next());
            profile.markDirty();
            player.setPlayerTime(profile.settings().timeOfDay().ticks(), false);
            refresh();
        }));
        set(SLOTS[index], new Button(menus.item("settings.cosmetics", Material.NETHER_STAR),
                (player, click) -> new CosmeticsMenu(plugin, player).open()));
        fill(menus.filler());
    }

    private static String plainName(MenuConfig menus, Setting setting) {
        String raw = menus.string("items.settings." + setting.key() + ".name", setting.key());
        return MessageService.mini().stripTags(raw);
    }
}
