package dev.customgui.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SessionHolder implements InventoryHolder {
    private final GuiSession session;
    public SessionHolder(GuiSession session) { this.session = session; }
    public GuiSession session() { return session; }
    @Override public Inventory getInventory() { throw new UnsupportedOperationException("holder does not own inventory reference"); }
}

