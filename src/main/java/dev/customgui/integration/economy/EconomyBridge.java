package dev.customgui.integration.economy;

import java.lang.reflect.Method;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

public final class EconomyBridge {
    private final Plugin vault;
    private final Object provider;
    private final Method has;
    private final Method withdraw;
    private final Method deposit;
    private final Method successful;

    private EconomyBridge(Plugin vault, Object provider, Method has, Method withdraw, Method deposit, Method successful) {
        this.vault = vault; this.provider = provider; this.has = has; this.withdraw = withdraw; this.deposit = deposit; this.successful = successful;
    }

    public static EconomyBridge discover(Server server) {
        Plugin vault = server.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return null;
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            Object registration = server.getServicesManager().getRegistration(economy);
            if (registration == null) return null;
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            Method withdraw = economy.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Class<?> response = withdraw.getReturnType();
            return new EconomyBridge(vault, provider, economy.getMethod("has", OfflinePlayer.class, double.class), withdraw,
                economy.getMethod("depositPlayer", OfflinePlayer.class, double.class), response.getMethod("transactionSuccess"));
        } catch (ReflectiveOperationException | LinkageError ex) { return null; }
    }

    public boolean ready() { return vault.isEnabled(); }
    public boolean has(OfflinePlayer player, double amount) { return invokeBoolean(has, player, amount); }
    public boolean withdraw(OfflinePlayer player, double amount) { return response(withdraw, player, amount); }
    public boolean deposit(OfflinePlayer player, double amount) { return response(deposit, player, amount); }

    private boolean invokeBoolean(Method method, OfflinePlayer player, double amount) {
        try { return ready() && Boolean.TRUE.equals(method.invoke(provider, player, amount)); }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }
    private boolean response(Method method, OfflinePlayer player, double amount) {
        try { Object value = method.invoke(provider, player, amount); return ready() && Boolean.TRUE.equals(successful.invoke(value)); }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }
}
