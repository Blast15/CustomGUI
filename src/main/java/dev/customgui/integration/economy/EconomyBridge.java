package dev.customgui.integration.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

public final class EconomyBridge {
    public enum Outcome { SUCCEEDED, REJECTED, UNKNOWN }
    private final Economy provider;

    private EconomyBridge(Economy provider) { this.provider = provider; }

    public static EconomyBridge discover(Server server) {
        var vault = server.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return null;
        var registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new EconomyBridge(registration.getProvider());
    }

    public boolean ready() { return provider.isEnabled(); }
    public boolean has(OfflinePlayer player, double amount) {
        try { return ready() && provider.has(player, amount); }
        catch (RuntimeException | LinkageError ex) { return false; }
    }
    public Outcome withdraw(OfflinePlayer player, double amount) { return mutate(() -> provider.withdrawPlayer(player, amount)); }
    public Outcome deposit(OfflinePlayer player, double amount) { return mutate(() -> provider.depositPlayer(player, amount)); }

    private Outcome mutate(java.util.function.Supplier<EconomyResponse> operation) {
        if (!ready()) return Outcome.REJECTED;
        try {
            EconomyResponse response = operation.get();
            return response != null && response.transactionSuccess() ? Outcome.SUCCEEDED : Outcome.REJECTED;
        } catch (RuntimeException | LinkageError ex) {
            return Outcome.UNKNOWN;
        }
    }
}
