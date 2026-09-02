package dev.customgui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
    @TempDir Path root;

    @Test void writesBacksUpAndRestores() throws Exception {
        Path file = root.resolve("menus/main.yml"); Files.createDirectories(file.getParent()); Files.writeString(file, "old");
        var store = new AtomicFileStore(root);
        var backup = store.write(file, "new");
        assertEquals("new", Files.readString(file));
        store.restore(backup);
        assertEquals("old", Files.readString(file));
    }

    @Test void removesNewFileOnRestoreAndRejectsEscapes() throws Exception {
        var store = new AtomicFileStore(root); Path file = root.resolve("menus/new.yml");
        var backup = store.write(file, "draft"); store.restore(backup);
        assertFalse(Files.exists(file));
        assertThrows(IllegalArgumentException.class, () -> store.write(root.resolve("../outside.yml"), "x"));
    }
}
