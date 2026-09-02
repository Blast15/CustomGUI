package dev.customgui.transaction;

public record CompensationReport(boolean inventoryRestored, boolean economyRestored,
                                 Throwable inventoryFailure, Throwable economyFailure) {
    public boolean complete() { return inventoryRestored && economyRestored; }
}
