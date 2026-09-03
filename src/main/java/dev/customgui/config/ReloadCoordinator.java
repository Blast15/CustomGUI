package dev.customgui.config;

import dev.customgui.command.MenuCommandRegistry;
import dev.customgui.gui.SessionRegistry;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.integration.enchant.CrazyEnchantmentsBridge;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Loads and validates a candidate before committing commands and publishing it. */
public final class ReloadCoordinator {
    private final File dataFolder;
    private final AtomicReference<ConfigSnapshot> active;
    private final MenuCommandRegistry commands;
    private final SessionRegistry sessions;
    private final ItemProviderRegistry providers;
    private final CrazyEnchantmentsBridge crazyEnchantments;
    private final Consumer<String> severe;
    private boolean degraded;

    public ReloadCoordinator(File dataFolder, AtomicReference<ConfigSnapshot> active, MenuCommandRegistry commands,
                             SessionRegistry sessions, ItemProviderRegistry providers, CrazyEnchantmentsBridge crazyEnchantments,
                             Consumer<String> severe) {
        this.dataFolder = dataFolder; this.active = active; this.commands = commands; this.sessions = sessions;
        this.providers = providers; this.crazyEnchantments = crazyEnchantments; this.severe = severe;
    }

    public Result reload() {
        if (degraded) return new Result(false, "plugin is degraded after a failed rollback", active.get());
        ConfigSnapshot previous = active.get();
        try {
            ConfigSnapshot candidate = new ConfigLoader().load(dataFolder, previous.revision() + 1);
            providers.validate(candidate);
            CrazyEnchantmentsBridge.validate(candidate, crazyEnchantments);
            var commandPlan = commands.plan(candidate);
            commands.commit(commandPlan);
            active.set(candidate);
            sessions.invalidateAll();
            return new Result(true, null, candidate);
        } catch (RuntimeException | LinkageError failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("command rollback failed:")) {
                degraded = true;
                severe.accept("Reload command rollback failed; plugin is degraded and reload is disabled: " + failure.getMessage());
            }
            return new Result(false, failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), previous);
        }
    }

    public boolean degraded() { return degraded; }
    public record Result(boolean success, String error, ConfigSnapshot snapshot) {}
}
