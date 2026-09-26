package dev.stonertp.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLifecycleTest extends PluginTestBase {

    @Test
    void pluginEnablesAndRegistersCommands() {
        assertTrue(plugin.isEnabled());
        assertNotNull(server.getPluginCommand("rtp"));
        assertNotNull(server.getPluginCommand("stonertp"));
        assertNotNull(server.getPluginCommand("back"));
    }
}
