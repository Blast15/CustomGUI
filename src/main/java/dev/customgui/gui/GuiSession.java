package dev.customgui.gui;

import java.time.Instant;
import java.util.UUID;

public record GuiSession(UUID playerId, UUID sessionId, String menuId, String currentPage,
                         String selectedRecipe, long renderRevision, long configRevision, Instant createdAt) {
    public static GuiSession create(UUID playerId, String menuId, long configRevision) {
        return new GuiSession(playerId, UUID.randomUUID(), menuId, "0", null, 0, configRevision, Instant.now());
    }
}

