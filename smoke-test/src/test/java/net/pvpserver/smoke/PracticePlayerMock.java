package net.pvpserver.smoke;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PlayerMock with no-op implementations for methods MockBukkit leaves unimplemented, and records of the Adventure
 * titles and boss bars it would show (MockBukkit drops those silently).
 */
class PracticePlayerMock extends PlayerMock {

    private final Set<BossBar> bossBars = new LinkedHashSet<>();
    private String lastTitle = "";

    PracticePlayerMock(ServerMock server, String name) {
        super(server, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void resetCooldown() {
        // attack cooldown is irrelevant in the mock
    }

    @Override
    public void showTitle(Title title) {
        lastTitle = PlainTextComponentSerializer.plainText().serialize(title.title()) + " | "
                + PlainTextComponentSerializer.plainText().serialize(title.subtitle());
    }

    @Override
    public void showBossBar(BossBar bar) {
        bossBars.add(bar);
    }

    @Override
    public void hideBossBar(BossBar bar) {
        bossBars.remove(bar);
    }

    /** @return "title | subtitle" of the last title shown, or "" */
    String lastTitle() {
        return lastTitle;
    }

    /** @return boss bars currently shown */
    Set<BossBar> bossBars() {
        return bossBars;
    }
}
