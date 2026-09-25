package net.pvpserver.lobby.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.gui.ItemTemplates;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Access to menus.yml: titles, filler and item templates.
 */
public final class MenuConfig {

    private final ConfigFile file;
    private final MessageService messages;

    /**
     * @param file menus.yml
     * @param messages lobby messages (parser)
     */
    public MenuConfig(ConfigFile file, MessageService messages) {
        this.file = file;
        this.messages = messages;
    }

    /** Re-reads menus.yml. */
    public void reload() {
        file.reload();
    }

    /**
     * @param menu menu id
     * @param resolvers placeholders
     * @return title
     */
    public Component title(String menu, TagResolver... resolvers) {
        return messages.parse(file.get().getString("titles." + menu, menu), resolvers);
    }

    /** @return filler material */
    public Material filler() {
        return ItemTemplates.material(file.get().getString("filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * @param path item path under {@code items}
     * @param fallback fallback material
     * @param resolvers placeholders
     * @return item
     */
    public ItemStack item(String path, Material fallback, TagResolver... resolvers) {
        return ItemTemplates.build(file.get(), "items." + path, fallback, messages, resolvers);
    }

    /**
     * @param path lore list path under {@code lore}
     * @param resolvers placeholders
     * @return parsed lore lines
     */
    public List<Component> lore(String path, TagResolver... resolvers) {
        return file.get().getStringList("lore." + path).stream().map(line -> messages.parse(line, resolvers)).toList();
    }

    /**
     * @param path string path
     * @param fallback fallback
     * @return raw string
     */
    public String string(String path, String fallback) {
        return file.get().getString(path, fallback);
    }

    /** @return lobby messages */
    public MessageService messages() {
        return messages;
    }
}
