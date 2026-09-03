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
        return apply(prepare());
    }

    /** File I/O and schema parsing only; safe to call away from the server thread. */
    public Prepared prepare() {
        ConfigSnapshot previous = active.get();
        if (degraded) return new Prepared(previous, null, "plugin is degraded after a failed rollback");
        try {
            ConfigSnapshot candidate = new ConfigLoader().load(dataFolder, previous.revision() + 1);
            return new Prepared(previous, candidate, null);
        } catch (RuntimeException failure) {
            return new Prepared(previous, null, message(failure));
        }
    }

    /** Provider calls, command mutation and publication must run on the server thread. */
    public Result apply(Prepared prepared) {
        if (prepared.error() != null) return new Result(false, prepared.error(), active.get());
        if (degraded) return new Result(false, "plugin is degraded after a failed rollback", active.get());
        if (active.get() != prepared.previous()) return new Result(false, "configuration changed while reload was prepared", active.get());
        try {
            ConfigSnapshot candidate = prepared.candidate();
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
            return new Result(false, message(failure), prepared.previous());
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    public boolean degraded() { return degraded; }
    public record Prepared(ConfigSnapshot previous, ConfigSnapshot candidate, String error) {}
    public record Result(boolean success, String error, ConfigSnapshot snapshot) {}
}
