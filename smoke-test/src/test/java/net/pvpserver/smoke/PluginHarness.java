package net.pvpserver.smoke;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.MockBukkitURLClassLoader;

import java.io.File;
import java.io.InputStream;
import java.util.jar.JarFile;

/**
 * Loads plugin jars like Paper does for dependent plugins: every jar gets its own class loader (resources resolve
 * inside the jar first), and gamemode plugins use PvPCore's loader as parent so they can link against it.
 */
final class PluginHarness {

    private final ServerMock server;
    private final File pluginFolder;
    private final File dataRoot;
    private ClassLoader coreLoader;

    PluginHarness(ServerMock server, File pluginFolder, File dataRoot) {
        this.server = server;
        this.pluginFolder = pluginFolder;
        this.dataRoot = dataRoot;
    }

    JavaPlugin load(String jarName) throws Exception {
        File jar = new File(pluginFolder, jarName);
        PluginDescriptionFile description;
        try (JarFile file = new JarFile(jar); InputStream in = file.getInputStream(file.getEntry("plugin.yml"))) {
            description = new PluginDescriptionFile(in);
        }
        ClassLoader parent = coreLoader == null ? getClass().getClassLoader() : coreLoader;
        File dataFolder = new File(dataRoot, description.getName());
        MockBukkitURLClassLoader loader = new MockBukkitURLClassLoader(jar, parent, server, description, dataFolder);
        if (coreLoader == null) {
            coreLoader = loader;
        }
        Class<?> main = loader.loadClass(description.getMainClass());
        JavaPlugin plugin = (JavaPlugin) main.getConstructor().newInstance();
        server.getPluginManager().registerLoadedPlugin(plugin);
        // Paper registers plugin.yml permissions (with their defaults) when loading a plugin.
        for (org.bukkit.permissions.Permission permission : description.getPermissions()) {
            if (server.getPluginManager().getPermission(permission.getName()) == null) {
                server.getPluginManager().addPermission(permission);
            }
        }
        plugin.onLoad();
        server.getPluginManager().enablePlugin(plugin);
        return plugin;
    }
}
