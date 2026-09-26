package dev.stonertp.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class PluginTestBase {
    protected ServerMock server;
    protected StoneRTP plugin;
    protected WorldMock world;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        world = addWorld("world");
        beforePluginLoad();
        plugin = MockBukkit.load(StoneRTP.class);
        plugin.getUpdateChecker().stop();
    }

    protected void beforePluginLoad() {
    }

    protected PlayerMock addPlayer() {
        PlayerMock player = new AsyncTeleportPlayerMock(server, "Player" + (++playerCounter));
        server.addPlayer(player);
        return player;
    }

    private int playerCounter;

    protected WorldMock addWorld(String name) {
        WorldMock created = new AsyncChunkWorldMock();
        created.setName(name);
        server.addWorld(created);
        return created;
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    protected File dataFile(String name) {
        return new File(plugin.getDataFolder(), name);
    }

    protected void setConfig(Map<String, Object> values) {
        File file = dataFile("config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        values.forEach(yaml::set);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        plugin.reload();
        plugin.getUpdateChecker().stop();
    }

    protected void setConfig(String path, Object value) {
        setConfig(Map.of(path, value));
    }

    protected static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    protected static List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            messages.add(plain(next));
        }
        return messages;
    }

    protected static boolean anyContains(List<String> messages, String fragment) {
        return messages.stream().anyMatch(message -> message.contains(fragment));
    }
}
