package dev.stonertp.plugin.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class YamlFiles {
    private YamlFiles() {
    }

    // YamlConfiguration#save truncates first, so a crash mid-write would leave an empty file; a rename is all-or-nothing.
    public static void saveAtomically(YamlConfiguration yaml, File file) throws IOException {
        Path target = file.toPath();
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
