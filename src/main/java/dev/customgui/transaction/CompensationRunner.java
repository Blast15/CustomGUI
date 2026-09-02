package dev.customgui.transaction;

final class CompensationRunner {
    private CompensationRunner() {}

    static CompensationReport run(boolean restoreInventory, ThrowingAction inventory,
                                  boolean restoreEconomy, ThrowingAction economy) {
        Throwable inventoryFailure = null, economyFailure = null;
        boolean inventoryRestored = !restoreInventory, economyRestored = !restoreEconomy;
        if (restoreInventory) try { inventory.run(); inventoryRestored = true; }
        catch (Throwable failure) { inventoryFailure = failure; }
        if (restoreEconomy) try { economy.run(); economyRestored = true; }
        catch (Throwable failure) { economyFailure = failure; }
        return new CompensationReport(inventoryRestored, economyRestored, inventoryFailure, economyFailure);
    }

    @FunctionalInterface interface ThrowingAction { void run() throws Throwable; }
}
