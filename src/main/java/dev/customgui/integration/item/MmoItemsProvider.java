package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MmoItemsProvider implements ItemProvider {
    private final Plugin plugin;
    private volatile Access access;
    public MmoItemsProvider(Plugin plugin) { this.plugin = plugin; refresh(); }
    @Override public String id() { return "mmoitems"; }
    @Override public boolean ready() { return plugin.isEnabled() && access != null; }
    @Override public void invalidate() { access = null; }

    @Override public void refresh() {
        try {
            Class<?> api = Class.forName("net.Indyuce.mmoitems.MMOItems", true, plugin.getClass().getClassLoader());
            Object instance = api.getField("plugin").get(null);
            Method getTypes = api.getMethod("getTypes");
            Object types = getTypes.invoke(instance);
            Method typeById = types.getClass().getMethod("get", String.class);
            Method getItem = findGetItem(api);
            access = new Access(api, instance, types, typeById, getItem, api.getMethod("getTypeName", ItemStack.class), api.getMethod("getID", ItemStack.class));
        } catch (ReflectiveOperationException | LinkageError ex) { access = null; }
    }

    @Override public ItemStack create(ItemSpec spec) {
        var current = requireAccess();
        if (spec.itemType().isBlank()) throw new IllegalArgumentException("mmoitems item-type is required");
        try {
            Object type = current.typeById().invoke(current.types(), spec.itemType());
            Object result = current.getItem().invoke(current.instance(), type, spec.id());
            if (!(result instanceof ItemStack stack) || stack.getType().isAir()) throw new IllegalArgumentException("unknown MMOItems item");
            stack = stack.clone(); stack.setAmount(spec.amount()); return stack;
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("MMOItems API invocation failed", ex); }
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        if (stack == null) return false;
        var current = requireAccess();
        try {
            return spec.itemType().equalsIgnoreCase(String.valueOf(current.getTypeName().invoke(null, stack)))
                && spec.id().equalsIgnoreCase(String.valueOf(current.getId().invoke(null, stack)));
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("MMOItems identity API failed", ex); }
    }

    @Override public Optional<String> identify(ItemStack stack) {
        if (stack == null) return Optional.empty();
        var current = requireAccess();
        try {
            Object type = current.getTypeName().invoke(null, stack), id = current.getId().invoke(null, stack);
            return type == null || id == null ? Optional.empty() : Optional.of(type + ":" + id);
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("MMOItems identity API failed", ex); }
    }

    private Access requireAccess() { var value = access; if (value == null || !plugin.isEnabled()) throw new IllegalStateException("MMOItems unavailable"); return value; }
    private static Method findGetItem(Class<?> api) throws NoSuchMethodException {
        for (Method method : api.getMethods()) if (method.getName().equals("getItem") && method.getParameterCount() == 2
            && method.getParameterTypes()[0].getName().equals("net.Indyuce.mmoitems.api.Type")
            && method.getParameterTypes()[1] == String.class) return method;
        throw new NoSuchMethodException("MMOItems#getItem(Type,String)");
    }
    private record Access(Class<?> api, Object instance, Object types, Method typeById, Method getItem, Method getTypeName, Method getId) {}
}
