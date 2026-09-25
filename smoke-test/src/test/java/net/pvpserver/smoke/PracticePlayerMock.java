package net.pvpserver.smoke;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * PlayerMock with no-op implementations for methods MockBukkit leaves unimplemented.
 */
class PracticePlayerMock extends PlayerMock {

    PracticePlayerMock(ServerMock server, String name) {
        super(server, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void resetCooldown() {
        // attack cooldown is irrelevant in the mock
    }
}
