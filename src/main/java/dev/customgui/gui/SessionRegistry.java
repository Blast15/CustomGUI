package dev.customgui.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionRegistry {
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    public void put(GuiSession session) { sessions.put(session.playerId(), session); }
    public Optional<GuiSession> valid(UUID playerId, UUID sessionId) {
        var session = sessions.get(playerId);
        return session != null && session.sessionId().equals(sessionId) ? Optional.of(session) : Optional.empty();
    }
    public void invalidate(UUID playerId) { sessions.remove(playerId); }
    public void invalidateAll() { sessions.clear(); }
}
