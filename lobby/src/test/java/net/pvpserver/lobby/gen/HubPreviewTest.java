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
        // The layout.yml a server writes for the default hub (world floor at y 64), for reviewing and the docs.
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        net.pvpserver.lobby.layout.LayoutParser.write(HubGenerator.generate(1337).worldLayout(64), yaml);
        File layout = new File("target/previews/layout.yml");
        yaml.save(layout);
        assertTrue(layout.length() > 1000);
    }
}
