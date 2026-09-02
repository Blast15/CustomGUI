package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ReflectiveItemProvider implements ItemProvider {
    public enum Api { ITEMSADDER, ORAXEN, NEXO }

    private final String id;
    private final Plugin plugin;
    private final Api api;
    private volatile Access access;

    public ReflectiveItemProvider(String id, Plugin plugin, Api api) {
        this.id = id; this.plugin = plugin; this.api = api;
        refresh();
    }

    @Override public String id() { return id; }
    @Override public boolean ready() { return plugin.isEnabled() && access != null; }
    @Override public void invalidate() { access = null; }

    @Override public void refresh() {
        if (!plugin.isEnabled()) { access = null; return; }
        try { access = switch (api) {
            case ITEMSADDER -> itemsAdder();
            case ORAXEN -> builderApi("io.th0rgal.oraxen.api.OraxenItems", "getItemById", "getIdByItem");
            case NEXO -> builderApi("com.nexomc.nexo.api.NexoItems", "itemFromId", "idFromItem");
        }; } catch (ReflectiveOperationException | LinkageError ex) { access = null; }
    }

    @Override public ItemStack create(ItemSpec spec) {
        var current = requireAccess();
        try {
            Object wrapper = current.byId().invoke(null, spec.id());
            if (wrapper == null) throw new IllegalArgumentException("unknown " + id + " item: " + spec.id());
            ItemStack stack = (ItemStack) wrapper.getClass().getMethod(current.buildMethod()).invoke(wrapper);
            if (stack == null || stack.getType().isAir()) throw new IllegalArgumentException("provider returned empty item");
            stack = stack.clone(); stack.setAmount(spec.amount()); return stack;
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalStateException(id + " API invocation failed", ex);
        }
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        return identify(stack).map(found -> found.equalsIgnoreCase(spec.id())).orElse(false);
    }

    @Override public Optional<String> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Optional.empty();
        var current = requireAccess();
        try {
            Object identified = current.fromStack().invoke(null, stack);
            if (identified == null) return Optional.empty();
            Object value = current.idMethod().isEmpty() ? identified : identified.getClass().getMethod(current.idMethod()).invoke(identified);
            return value instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(id + " identity API failed", ex); }
    }

    private Access requireAccess() {
        var current = access;
        if (current == null || !plugin.isEnabled()) throw new IllegalStateException(id + " provider is unavailable");
        return current;
    }

    private Access itemsAdder() throws ReflectiveOperationException {
        Class<?> type = load("dev.lone.itemsadder.api.CustomStack");
        return new Access(type.getMethod("getInstance", String.class), type.getMethod("byItemStack", ItemStack.class), "getItemStack", "getNamespacedID");
    }

    private Access builderApi(String className, String byId, String fromStack) throws ReflectiveOperationException {
        Class<?> type = load(className);
        return new Access(staticMethod(type, byId, String.class), staticMethod(type, fromStack, ItemStack.class), "build", "");
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, plugin.getClass().getClassLoader());
    }

    private static Method staticMethod(Class<?> type, String name, Class<?> parameter) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameter);
        if (!Modifier.isStatic(method.getModifiers())) throw new NoSuchMethodException(type.getName() + '.' + name + " is not static");
        return method;
    }

    private record Access(Method byId, Method fromStack, String buildMethod, String idMethod) {
        private Access { Objects.requireNonNull(byId); Objects.requireNonNull(fromStack); }
    }
}

