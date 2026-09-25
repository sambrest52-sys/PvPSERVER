package net.pvpserver.core.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterTest {

    @Test
    void censorsWholeWordsCaseInsensitively() {
        ChatFilter filter = new ChatFilter();
        filter.configure(List.of("badword"), false, false, "***");
        ChatFilter.Result result = filter.apply("this BadWord is bad but badwords is fine");
        assertFalse(result.blocked());
        assertEquals("this *** is bad but badwords is fine", result.message());
    }

    @Test
    void blockModeDropsMessage() {
        ChatFilter filter = new ChatFilter();
        filter.configure(List.of("badword"), true, false, "***");
        assertTrue(filter.apply("badword").blocked());
        assertFalse(filter.apply("clean message").blocked());
    }

    @Test
    void blocksLinksWhenEnabled() {
        ChatFilter filter = new ChatFilter();
        filter.configure(List.of(), false, true, "***");
        assertTrue(filter.apply("join play.otherserver.net now").blocked());
        assertTrue(filter.apply("https://example.com/x").blocked());
        assertFalse(filter.apply("gg well played 3.5 hearts").blocked());
    }
}
