package net.pvpserver.core.kit;

import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.storage.repository.KitLayoutRepository;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads kits from kits.yml, applies them to players (respecting saved layouts) and stores layouts.
 */
public final class KitService implements Reloadable {

    private final JavaPlugin plugin;
    private final ConfigFile file;
    private final ProfileService profiles;
    private final KitLayoutRepository layouts;
    private final KitItemParser parser;
    private final NamespacedKey originKey;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    /**
     * @param plugin core plugin
     * @param profiles profile service (layouts are cached on profiles)
     * @param layouts layout repository
     */
    public KitService(JavaPlugin plugin, ProfileService profiles, KitLayoutRepository layouts) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, "kits.yml");
        this.profiles = profiles;
        this.layouts = layouts;
        this.parser = new KitItemParser(plugin.getLogger(), new NamespacedKey(plugin, KitItemParser.SPECIAL_TAG));
        this.originKey = new NamespacedKey(plugin, "kit_origin");
        reload();
    }

    @Override
    public void reload() {
        file.reload();
        kits.clear();
        ConfigurationSection root = file.get().getConfigurationSection("kits");
        if (root == null) {
            plugin.getLogger().warning("kits.yml has no 'kits' section");
            return;
        }
        List<Kit> loaded = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                loaded.add(parseKit(id.toLowerCase(Locale.ROOT), section));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Failed to load kit " + id + ": " + e.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(Kit::order));
        loaded.forEach(kit -> kits.put(kit.id(), kit));
        plugin.getLogger().info("Loaded " + kits.size() + " kits");
    }

    private Kit parseKit(String id, ConfigurationSection section) {
        ItemStack[] contents = new ItemStack[36];
        for (Map<?, ?> entry : section.getMapList("items")) {
            Object slotValue = entry.get("slot");
            int slot = slotValue instanceof Number n ? n.intValue() : -1;
            ItemStack item = parser.parse(id, entry);
            if (item == null) {
                continue;
            }
            if (slot < 0 || slot >= 36) {
                slot = firstFree(contents);
            }
            if (slot >= 0) {
                contents[slot] = item;
            }
        }
        ConfigurationSection fill = section.getConfigurationSection("fill");
        if (fill != null) {
            ItemStack filler = parser.parse(id, fill);
            if (filler != null) {
                for (int i = 0; i < 36; i++) {
                    if (contents[i] == null) {
                        contents[i] = filler.clone();
                    }
                }
            }
        }
        ItemStack[] armor = new ItemStack[4];
        ConfigurationSection armorSection = section.getConfigurationSection("armor");
        if (armorSection != null) {
            String[] keys = {"helmet", "chestplate", "leggings", "boots"};
            for (int i = 0; i < 4; i++) {
                armor[i] = parser.parse(id, armorSection.getConfigurationSection(keys[i]));
            }
        }
        ItemStack offhand = parser.parse(id, section.getConfigurationSection("offhand"));
        ConfigurationSection iconSection = section.getConfigurationSection("icon");
        ItemStack icon = iconSection != null ? parser.parse(id, iconSection) : null;
        if (icon == null) {
            icon = new ItemStack(Material.IRON_SWORD);
        }
        String displayName = section.getString("display-name", "<white>" + id);
        icon = ItemBuilder.of(icon).name(MessageService.mini().deserialize(displayName)).hideFlags().build();
        List<PotionEffect> effects = parser.parseEffects(id, section.getStringList("effects"));
        Set<String> tags = lower(section.getStringList("arena-tags"));
        if (tags.isEmpty()) {
            tags.add("standard");
        }
        return new Kit(id, displayName, icon,
                section.getBoolean("enabled", true),
                section.getBoolean("ranked", true),
                section.getBoolean("unranked", true),
                section.getBoolean("ffa", false),
                section.getBoolean("editable", true),
                section.getInt("order", 100),
                section.getString("knockback", ""),
                tags,
                lower(section.getStringList("arenas")),
                KitRules.parse(section.getConfigurationSection("rules")),
                contents, armor, offhand, effects);
    }

    private static Set<String> lower(List<String> list) {
        Set<String> set = new HashSet<>();
        for (String s : list) {
            set.add(s.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    private static int firstFree(ItemStack[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param id kit id (case-insensitive)
     * @return kit if loaded and enabled
     */
    public Optional<Kit> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Kit kit = kits.get(id.toLowerCase(Locale.ROOT));
        return kit != null && kit.enabled() ? Optional.of(kit) : Optional.empty();
    }

    /** @return all enabled kits in menu order */
    public List<Kit> all() {
        return kits.values().stream().filter(Kit::enabled).toList();
    }

    /**
     * @param ranked ranked or unranked queue
     * @return kits available for that queue
     */
    public List<Kit> queueKits(boolean ranked) {
        return all().stream().filter(k -> ranked ? k.ranked() : k.unranked()).toList();
    }

    /** @return kits usable in FFA */
    public List<Kit> ffaKits() {
        return all().stream().filter(Kit::ffa).toList();
    }

    /** @return kit ids */
    public Collection<String> ids() {
        return all().stream().map(Kit::id).toList();
    }

    /**
     * Clears the player and gives the kit using their saved layout. Does not touch combat attributes; see
     * {@code CombatService#applyKit}.
     *
     * @param player player
     * @param kit kit
     */
    public void giveKit(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] arranged = layoutFor(player, kit).apply(kit.contents());
        inventory.setStorageContents(arranged);
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[3 - i] = kit.armor()[i] == null ? null : kit.armor()[i].clone();
        }
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(kit.offhand() == null ? null : kit.offhand().clone());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        for (PotionEffect effect : kit.effects()) {
            player.addPotionEffect(effect);
        }
        heal(player);
        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    /**
     * Gives the kit's definition contents with every storage item tagged with its origin slot, for the kit editor.
     *
     * @param player editor
     * @param kit kit
     */
    public void giveForEditing(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] arranged = new ItemStack[36];
        ItemStack[] tagged = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            if (kit.contents()[i] != null) {
                tagged[i] = ItemBuilder.of(kit.contents()[i]).tag(originKey, i).build();
            }
        }
        KitLayout layout = layoutFor(player, kit);
        ItemStack[] placed = layout.apply(tagged);
        System.arraycopy(placed, 0, arranged, 0, 36);
        inventory.setStorageContents(arranged);
        player.updateInventory();
    }

    /**
     * Reads the editor inventory back into a layout and saves it.
     *
     * @param player editor
     * @param kit kit
     */
    public void saveLayoutFromInventory(Player player, Kit kit) {
        int[] origins = new int[36];
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < 36; slot++) {
            origins[slot] = -1;
            ItemStack item = slot < storage.length ? storage[slot] : null;
            if (item != null && item.hasItemMeta()) {
                Integer origin = item.getItemMeta().getPersistentDataContainer().get(originKey, PersistentDataType.INTEGER);
                if (origin != null) {
                    origins[slot] = origin;
                }
            }
        }
        KitLayout layout = KitLayout.fromPositions(origins);
        saveLayout(player, kit, layout.isIdentity() ? null : layout);
    }

    /**
     * @param player player
     * @param kit kit
     * @param layout layout or null to reset to default
     */
    public void saveLayout(Player player, Kit kit, KitLayout layout) {
        PlayerProfile profile = profiles.get(player);
        String serialized = layout == null ? null : layout.serialize();
        if (profile != null) {
            if (serialized == null) {
                profile.kitLayouts().remove(kit.id());
            } else {
                profile.kitLayouts().put(kit.id(), serialized);
            }
        }
        layouts.save(player.getUniqueId(), kit.id(), serialized);
    }

    /**
     * @param player player
     * @param kit kit
     * @return saved layout or identity
     */
    public KitLayout layoutFor(Player player, Kit kit) {
        PlayerProfile profile = profiles.get(player);
        return KitLayout.parse(profile == null ? null : profile.kitLayouts().get(kit.id()));
    }

    /**
     * Restores health, food, fire, fall distance and saturation.
     *
     * @param player player
     */
    public static void heal(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20.0 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setAbsorptionAmount(0);
    }

    /**
     * Assigns a knockback profile to a kit, writes kits.yml and reloads kits.
     *
     * @param kitId kit id
     * @param profile knockback profile id
     */
    public void setKnockback(String kitId, String profile) {
        file.get().set("kits." + kitId.toLowerCase(Locale.ROOT) + ".knockback", profile);
        file.save();
        reload();
    }

    /** @return namespaced key for special item tags */
    public NamespacedKey specialKey() {
        return new NamespacedKey(plugin, KitItemParser.SPECIAL_TAG);
    }
}
