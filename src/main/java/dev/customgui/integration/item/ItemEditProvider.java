package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** ItemEdit server-item integration using its documented ServerStorage API. */
public final class ItemEditProvider implements ItemProvider {
    private final Plugin plugin;
    private volatile Access access;

    public ItemEditProvider(Plugin plugin) { this.plugin = plugin; refresh(); }
    @Override public String id() { return "itemedit"; }
    @Override public boolean ready() { return plugin.isEnabled() && access != null; }
    @Override public void invalidate() { access = null; }

    @Override public void refresh() {
        if (!plugin.isEnabled()) { access = null; return; }
        try {
            Class<?> api = Class.forName("emanondev.itemedit.ItemEdit", true, plugin.getClass().getClassLoader());
            Object instance = api.getMethod("get").invoke(null);
            Object storage = api.getMethod("getServerStorage").invoke(instance);
            access = new Access(storage, storage.getClass().getMethod("getItem", String.class),
                storage.getClass().getMethod("getId", ItemStack.class));
        } catch (ReflectiveOperationException | LinkageError ex) { access = null; }
    }

    @Override public ItemStack create(ItemSpec spec) {
        Access current = requireAccess();
        try {
            Object value = current.getItem().invoke(current.storage(), spec.id());
            if (!(value instanceof ItemStack stack) || stack.getType().isAir())
                throw new IllegalArgumentException("unknown ItemEdit server item: " + spec.id());
            ItemStack result = stack.clone(); result.setAmount(spec.amount()); return result;
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("ItemEdit API invocation failed", ex); }
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        return identify(stack).map(spec.id()::equalsIgnoreCase).orElse(false);
    }

    @Override public Optional<String> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Optional.empty();
        Access current = requireAccess();
        try {
            Object value = current.getId().invoke(current.storage(), stack.clone());
            return value instanceof String id && !id.isBlank() ? Optional.of(id) : Optional.empty();
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("ItemEdit identity API failed", ex); }
    }

    private Access requireAccess() {
        Access current = access;
        if (current == null || !plugin.isEnabled()) throw new IllegalStateException("ItemEdit provider is unavailable");
        return current;
    }

    private record Access(Object storage, Method getItem, Method getId) {}
}
