package dev.customgui.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

public final class AtomicFileStore {
    private final Path root;
    private final Path backups;
    public AtomicFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.backups = this.root.resolve("backups/editor");
    }

    public Backup write(Path target, String contents) throws IOException {
        Path safe = safe(target);
        Files.createDirectories(safe.getParent());
        Path backup = null;
        boolean existed = Files.isRegularFile(safe);
        if (existed) {
            Files.createDirectories(backups);
            backup = backups.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safe.getFileName());
            Files.copy(safe, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path temporary = safe.resolveSibling('.' + safe.getFileName().toString() + '.' + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            move(temporary, safe);
        } finally { Files.deleteIfExists(temporary); }
        return new Backup(safe, backup, existed);
    }

    public Backup remove(Path target) throws IOException {
        Path safe = safe(target);
        if (!Files.isRegularFile(safe)) return new Backup(safe, null, false);
        Files.createDirectories(backups);
        Path backup = backups.resolve(Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safe.getFileName());
        Files.copy(safe, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Files.delete(safe);
        return new Backup(safe, backup, true);
    }

    public void restore(Backup backup) throws IOException {
        if (backup.existed()) move(backup.backup(), backup.target());
        else Files.deleteIfExists(safe(backup.target()));
    }

    private Path safe(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IllegalArgumentException("editor path escapes plugin folder");
        return normalized;
    }
    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public record Backup(Path target, Path backup, boolean existed) {}
}
