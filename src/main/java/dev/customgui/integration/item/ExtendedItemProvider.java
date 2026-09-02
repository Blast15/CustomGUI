package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Optional integrations kept reflection-only so no third-party library is bundled. */
public final class ExtendedItemProvider implements ItemProvider {
    public enum Api { ECO_ITEMS, EXECUTABLE_ITEMS, SLIMEFUN, MYTHIC_MOBS, NOVA }

    private final String id;
    private final Plugin apiPlugin;
    private final Plugin[] requirements;
    private final Api api;
    private volatile Access access;

    public ExtendedItemProvider(String id, Plugin apiPlugin, Api api, Plugin... requirements) {
        this.id = id; this.apiPlugin = apiPlugin; this.api = api; this.requirements = requirements.clone(); refresh();
    }

    @Override public String id() { return id; }
    @Override public boolean ready() { return enabled() && access != null; }
    @Override public void invalidate() { access = null; }

    @Override public void refresh() {
        if (!enabled()) { access = null; return; }
        try {
            access = switch (api) {
                case ECO_ITEMS -> ecoItems();
                case EXECUTABLE_ITEMS -> executableItems();
                case SLIMEFUN -> slimefun();
                case MYTHIC_MOBS -> mythicMobs();
                case NOVA -> nova();
            };
        } catch (ReflectiveOperationException | LinkageError ex) { access = null; }
    }

    @Override public ItemStack create(ItemSpec spec) {
        var current = requireAccess();
        try {
            ItemStack stack = current.create().create(spec.id(), spec.amount());
            if (stack == null || stack.getType().isAir()) throw new IllegalArgumentException("unknown " + id + " item: " + spec.id());
            stack = stack.clone(); stack.setAmount(spec.amount()); return stack;
        } catch (ReflectiveOperationException | ClassCastException ex) { throw new IllegalStateException(id + " API invocation failed", ex); }
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        return identify(stack).map(found -> found.equalsIgnoreCase(spec.id())).orElse(false);
    }

    @Override public Optional<String> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Optional.empty();
        var current = requireAccess();
        try {
            String found = current.identify().identify(stack);
            return found == null || found.isBlank() ? Optional.empty() : Optional.of(found);
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(id + " identity API failed", ex); }
    }

    private Access ecoItems() throws ReflectiveOperationException {
        Class<?> registryType = load("com.willfp.ecoitems.items.EcoItems");
        Object registry = registryType.getField("INSTANCE").get(null);
        Method byId = registryType.getMethod("getByID", String.class);
        Method fromStack = load("com.willfp.ecoitems.items.ItemUtilsKt").getMethod("getEcoItem", ItemStack.class);
        return new Access((itemId, amount) -> {
            Object item = byId.invoke(registry, itemId);
            return item == null ? null : (ItemStack) item.getClass().getMethod("getItemStack").invoke(item);
        }, stack -> objectId(fromStack.invoke(null, stack)));
    }

    private Access executableItems() throws ReflectiveOperationException {
        Class<?> apiType = load("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
        Object manager = apiType.getMethod("getExecutableItemsManager").invoke(null);
        Method byId = manager.getClass().getMethod("getExecutableItem", String.class);
        Method fromStack = manager.getClass().getMethod("getExecutableItem", ItemStack.class);
        return new Access((itemId, amount) -> {
            Object item = optional(byId.invoke(manager, itemId));
            if (item == null) return null;
            return (ItemStack) item.getClass().getMethod("buildItem", int.class, Optional.class, Optional.class)
                .invoke(item, amount, Optional.empty(), Optional.empty());
        }, stack -> objectId(optional(fromStack.invoke(manager, stack))));
    }

    private Access slimefun() throws ReflectiveOperationException {
        Class<?> type = load("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
        Method byId = type.getMethod("getById", String.class), fromStack = type.getMethod("getByItem", ItemStack.class);
        return new Access((itemId, amount) -> {
            Object item = byId.invoke(null, itemId);
            return item == null ? null : (ItemStack) item.getClass().getMethod("getItem").invoke(item);
        }, stack -> objectId(fromStack.invoke(null, stack)));
    }

    private Access mythicMobs() throws ReflectiveOperationException {
        Class<?> type = load("io.lumine.mythic.bukkit.MythicBukkit");
        Object instance = type.getMethod("inst").invoke(null);
        Object manager = type.getMethod("getItemManager").invoke(instance);
        Method create;
        try { create = manager.getClass().getMethod("getItemStack", String.class, int.class); }
        catch (NoSuchMethodException ex) { create = manager.getClass().getMethod("getItemStack", String.class); }
        Method identify = manager.getClass().getMethod("getMythicTypeFromItem", ItemStack.class);
        Method creator = create;
        return new Access((itemId, amount) -> (ItemStack) (creator.getParameterCount() == 2
            ? creator.invoke(manager, itemId, amount) : creator.invoke(manager, itemId)), stack -> (String) identify.invoke(manager, stack));
    }

    private Access nova() throws ReflectiveOperationException {
        Class<?> type = load("xyz.xenondevs.nova.api.Nova");
        Object instance = type.getMethod("getNova").invoke(null);
        Object registry = type.getMethod("getItemRegistry").invoke(instance);
        Method byId = registry.getClass().getMethod("getOrNull", String.class);
        Method fromStack = registry.getClass().getMethod("getOrNull", ItemStack.class);
        return new Access((itemId, amount) -> {
            Object item = byId.invoke(registry, itemId);
            return item == null ? null : (ItemStack) item.getClass().getMethod("createItemStack", int.class).invoke(item, amount);
        }, stack -> objectId(fromStack.invoke(registry, stack)));
    }

    private String objectId(Object item) throws ReflectiveOperationException {
        if (item == null) return null;
        for (String method : ListHolder.ID_METHODS) try {
            Object value = item.getClass().getMethod(method).invoke(item);
            if (value != null) return value.toString();
        } catch (NoSuchMethodException ignored) { /* try the next official naming variant */ }
        throw new NoSuchMethodException(item.getClass().getName() + " has no public item ID accessor");
    }

    private static Object optional(Object value) { return value instanceof Optional<?> option ? option.orElse(null) : value; }
    private Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name, true, apiPlugin.getClass().getClassLoader()); }
    private boolean enabled() { return apiPlugin.isEnabled() && Arrays.stream(requirements).allMatch(Plugin::isEnabled); }
    private Access requireAccess() { var current = access; if (current == null || !enabled()) throw new IllegalStateException(id + " provider is unavailable"); return current; }

    @FunctionalInterface private interface Creator { ItemStack create(String id, int amount) throws ReflectiveOperationException; }
    @FunctionalInterface private interface Identifier { String identify(ItemStack stack) throws ReflectiveOperationException; }
    private record Access(Creator create, Identifier identify) {}
    private static final class ListHolder { private static final java.util.List<String> ID_METHODS = java.util.List.of("getId", "getID"); }
}
