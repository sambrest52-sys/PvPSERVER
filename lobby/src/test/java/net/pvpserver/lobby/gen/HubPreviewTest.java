package net.pvpserver.lobby.gen;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the lobby previews into lobby/target/previews on every build, so the docs images can be refreshed.
 */
class HubPreviewTest {

    @Test
    void writesPreviews() throws IOException {
        for (File file : HubPreviewRenderer.write(new File("target/previews"), 1337)) {
            assertTrue(file.length() > 10_000, file + " was written");
        }
    }
}
