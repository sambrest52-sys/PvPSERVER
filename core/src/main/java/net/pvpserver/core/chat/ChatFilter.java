package net.pvpserver.core.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Word filter. Blocked words are matched case-insensitively on word boundaries and either censored or cause the
 * message to be blocked. Also detects links when link blocking is enabled.
 */
public final class ChatFilter {

    private static final Pattern LINK = Pattern.compile("(?i)\\b((https?://)?[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|net|org|gg|io|me|co|xyz|club|us|uk|de|ru|tk)(/\\S*)?)\\b");

    private final List<Pattern> patterns = new ArrayList<>();
    private boolean block;
    private boolean blockLinks;
    private String replacement = "***";

    /**
     * @param words filtered words (regex allowed)
     * @param block block the whole message instead of censoring
     * @param blockLinks block domain-looking text
     * @param replacement censor replacement
     */
    public void configure(List<String> words, boolean block, boolean blockLinks, String replacement) {
        patterns.clear();
        for (String word : words) {
            if (!word.isBlank()) {
                patterns.add(Pattern.compile("(?i)\\b" + word.trim().toLowerCase(Locale.ROOT) + "\\b"));
            }
        }
        this.block = block;
        this.blockLinks = blockLinks;
        this.replacement = replacement;
    }

    /**
     * @param message raw message
     * @return filter result
     */
    public Result apply(String message) {
        if (blockLinks && LINK.matcher(message).find()) {
            return new Result(true, message, "link");
        }
        String output = message;
        boolean matched = false;
        for (Pattern pattern : patterns) {
            var matcher = pattern.matcher(output);
            if (matcher.find()) {
                matched = true;
                if (block) {
                    return new Result(true, message, "word");
                }
                output = matcher.replaceAll(replacement);
            }
        }
        return new Result(false, output, matched ? "censored" : null);
    }

    /**
     * @param blocked whether the message must not be sent
     * @param message possibly censored message
     * @param reason reason code or null
     */
    public record Result(boolean blocked, String message, String reason) {
    }
}
