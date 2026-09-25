package net.pvpserver.core.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * MiniMessage-backed message catalogue. Every plugin owns one instance over its own {@code messages.yml};
 * lookups fall back to the core catalogue, which also provides the shared {@code <prefix>} and theme colour tags
 * ({@code <primary>}, {@code <secondary>}, {@code <accent>}, {@code <success>}, {@code <error>}, {@code <muted>}).
 */
public final class MessageService implements Reloadable {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ConfigFile file;
    private final MessageService parent;
    private TagResolver themeResolver = TagResolver.empty();

    /**
     * @param plugin owning plugin
     * @param resource resource name, usually {@code messages.yml}
     * @param parent fallback catalogue (core), or null for the core catalogue itself
     */
    public MessageService(JavaPlugin plugin, String resource, MessageService parent) {
        this.file = new ConfigFile(plugin, resource);
        this.parent = parent;
        reload();
    }

    @Override
    public void reload() {
        file.reload();
        if (parent == null) {
            List<TagResolver> resolvers = new ArrayList<>();
            ConfigurationSection colors = file.get().getConfigurationSection("theme");
            if (colors != null) {
                for (String key : colors.getKeys(false)) {
                    TextColor color = TextColor.fromHexString(colors.getString(key, "#FFFFFF"));
                    if (color != null) {
                        resolvers.add(TagResolver.resolver(key, Tag.styling(color)));
                    }
                }
            }
            resolvers.add(TagResolver.resolver("prefix", (args, ctx) ->
                    Tag.inserting(MINI.deserialize(file.get().getString("prefix", ""), TagResolver.resolver(resolversWithoutPrefix())))));
            this.themeResolver = TagResolver.resolver(resolvers);
        }
    }

    private List<TagResolver> resolversWithoutPrefix() {
        List<TagResolver> list = new ArrayList<>();
        ConfigurationSection colors = file.get().getConfigurationSection("theme");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                TextColor color = TextColor.fromHexString(colors.getString(key, "#FFFFFF"));
                if (color != null) {
                    list.add(TagResolver.resolver(key, Tag.styling(color)));
                }
            }
        }
        return list;
    }

    /** @return shared theme resolver ({@code <primary>}, {@code <prefix>} ...) */
    public TagResolver theme() {
        return parent != null ? parent.theme() : themeResolver;
    }

    /**
     * @param key message key
     * @return raw MiniMessage string or null when missing everywhere
     */
    public String raw(String key) {
        String value = file.get().getString(key);
        if (value == null && parent != null) {
            return parent.raw(key);
        }
        return value;
    }

    /**
     * @param key message key
     * @return raw MiniMessage list; single strings become a one-element list
     */
    public List<String> rawList(String key) {
        if (file.get().isList(key)) {
            return file.get().getStringList(key);
        }
        if (file.get().isString(key)) {
            return List.of(file.get().getString(key, ""));
        }
        return parent != null ? parent.rawList(key) : List.of();
    }

    /**
     * Parses arbitrary MiniMessage text with the theme and given placeholders.
     *
     * @param miniMessage text
     * @param resolvers placeholders
     * @return component with italics disabled (safe for item names)
     */
    public Component parse(String miniMessage, TagResolver... resolvers) {
        if (miniMessage == null) {
            return Component.empty();
        }
        // Caller placeholders come first so they win over theme tags of the same name (e.g. a rank <prefix>).
        return MINI.deserialize(miniMessage, TagResolver.resolver(TagResolver.resolver(resolvers), theme()))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * @param key message key
     * @param resolvers placeholders
     * @return parsed message, or a visible marker when missing
     */
    public Component get(String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw == null) {
            return Component.text("Missing message: " + key);
        }
        return parse(raw, resolvers);
    }

    /**
     * @param key list key
     * @param resolvers placeholders
     * @return parsed lines
     */
    public List<Component> getList(String key, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key)) {
            lines.add(parse(line, resolvers));
        }
        return lines;
    }

    /**
     * Sends a message; empty strings are treated as "disabled" and not sent.
     *
     * @param audience receiver
     * @param key message key
     * @param resolvers placeholders
     */
    public void send(Audience audience, String key, TagResolver... resolvers) {
        if (audience == null) {
            return;
        }
        List<String> lines = rawList(key);
        if (lines.isEmpty()) {
            audience.sendMessage(Component.text("Missing message: " + key));
            return;
        }
        for (String line : lines) {
            if (!line.isEmpty()) {
                audience.sendMessage(parse(line, resolvers));
            }
        }
    }

    /**
     * Sends a title using {@code <key>.title} and {@code <key>.subtitle}.
     *
     * @param audience receiver
     * @param key base key
     * @param fadeIn ticks
     * @param stay ticks
     * @param fadeOut ticks
     * @param resolvers placeholders
     */
    public void title(Audience audience, String key, int fadeIn, int stay, int fadeOut, TagResolver... resolvers) {
        String title = raw(key + ".title");
        String subtitle = raw(key + ".subtitle");
        if ((title == null || title.isEmpty()) && (subtitle == null || subtitle.isEmpty())) {
            return;
        }
        audience.showTitle(Title.title(parse(title == null ? "" : title, resolvers), parse(subtitle == null ? "" : subtitle, resolvers),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    /**
     * Sends an action bar message.
     *
     * @param audience receiver
     * @param key message key
     * @param resolvers placeholders
     */
    public void actionBar(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw != null && !raw.isEmpty()) {
            audience.sendActionBar(parse(raw, resolvers));
        }
    }

    /**
     * Shorthand for an unparsed (literal) placeholder.
     *
     * @param key tag name
     * @param value value, converted with {@link String#valueOf(Object)}
     * @return resolver
     */
    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /**
     * Shorthand for a component placeholder.
     *
     * @param key tag name
     * @param value component
     * @return resolver
     */
    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }

    /** @return the shared MiniMessage instance */
    public static MiniMessage mini() {
        return MINI;
    }

    /** @return backing configuration file */
    public ConfigFile file() {
        return file;
    }
}
