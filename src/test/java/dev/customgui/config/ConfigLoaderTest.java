package dev.customgui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
    @TempDir Path directory;

    @Test void loadsCustomItemsCommandsPermissionsAndLinks() throws Exception {
        write("config.yml", "config-version: 1\nsecurity:\n  max-batch-size: 64\n");
        write("recipes/items.yml", "recipes:\n  trade:\n    requirements:\n      - {type: item, material: STONE}\n    results:\n      - {type: give-item, material: DIAMOND}\n");
        write("menus/main.yml", "id: main\nrows: 1\npermission: menu.main\nopen-commands: [shop]\nitems:\n  next:\n    material: ARROW\n    slot: 0\n    actions:\n      left: ['[open-menu] second']\n");
        write("menus/second.yml", "id: second\nrows: 1\nitems:\n  trade:\n    material: DIAMOND\n    slot: 0\n    actions:\n      right: ['[recipe] trade all']\n");
        var snapshot = new ConfigLoader().load(directory.toFile(), 7);
        assertEquals(2, snapshot.menus().size());
        assertEquals("menu.main", snapshot.menus().get("main").permission());
        assertEquals(64, snapshot.maxBatchSize());
    }

    @Test void rejectsBrokenMenuLinks() throws Exception {
        write("config.yml", "config-version: 1\n");
        write("menus/main.yml", "id: main\nrows: 1\nitems:\n  bad:\n    material: STONE\n    slot: 0\n    actions:\n      click: ['[open-menu] missing']\n");
        Files.createDirectories(directory.resolve("recipes"));
        assertThrows(IllegalArgumentException.class, () -> new ConfigLoader().load(directory.toFile(), 1));
    }

    @Test void rejectsUnknownConfigVersion() throws Exception {
        write("config.yml", "config-version: 2\n");
        Files.createDirectories(directory.resolve("menus"));
        Files.createDirectories(directory.resolve("recipes"));
        assertThrows(IllegalArgumentException.class, () -> new ConfigLoader().load(directory.toFile(), 2));
    }

    private void write(String relative, String contents) throws Exception {
        Path path = directory.resolve(relative); Files.createDirectories(path.getParent()); Files.writeString(path, contents);
    }
}
