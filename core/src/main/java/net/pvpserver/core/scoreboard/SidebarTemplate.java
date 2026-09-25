package net.pvpserver.core.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.message.MessageService;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sidebar layout from YAML ({@code title} + {@code lines}). A line starting with {@code ?flag } is only shown when the
 * provider passes that flag, e.g. {@code "?party <gray>Party: <white><party_size>"}.
 */
public final class SidebarTemplate {

    private final String title;
    private final List<String> lines;

    /**
     * @param title MiniMessage title
     * @param lines MiniMessage lines
     */
    public SidebarTemplate(String title, List<String> lines) {
        this.title = title;
        this.lines = lines;
    }

    /**
     * @param section section with {@code title} and {@code lines}
     * @return template (empty when section is missing)
     */
    public static SidebarTemplate of(ConfigurationSection section) {
        if (section == null) {
            return new SidebarTemplate("", List.of());
        }
        return new SidebarTemplate(section.getString("title", ""), section.getStringList("lines"));
    }

    /**
     * @param messages parser
     * @param resolvers placeholders
     * @return title component
     */
    public Component title(MessageService messages, TagResolver resolvers) {
        return messages.parse(title, resolvers);
    }

    /**
     * @param messages parser
     * @param flags enabled conditional flags
     * @param resolvers placeholders
     * @return rendered lines
     */
    public List<Component> lines(MessageService messages, Set<String> flags, TagResolver resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String text = line;
            if (text.startsWith("?")) {
                int space = text.indexOf(' ');
                String flag = space < 0 ? text.substring(1) : text.substring(1, space);
                boolean negate = flag.startsWith("!");
                if (negate) {
                    flag = flag.substring(1);
                }
                if (flags.contains(flag) == negate) {
                    continue;
                }
                text = space < 0 ? "" : text.substring(space + 1);
            }
            out.add(messages.parse(text, resolvers));
        }
        return out;
    }
}
