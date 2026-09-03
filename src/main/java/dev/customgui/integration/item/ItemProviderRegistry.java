package dev.customgui.integration.item;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class ItemProviderRegistry {
    private final Map<String, ItemProvider> providers = new HashMap<>();

    public void register(ItemProvider provider) {
        if (providers.putIfAbsent(provider.id().toLowerCase(Locale.ROOT), provider) != null)
            throw new IllegalArgumentException("duplicate item provider: " + provider.id());
    }

    public Optional<ItemProvider> find(String id) {
        var provider = providers.get(id.toLowerCase(Locale.ROOT));
        return provider != null && provider.ready() ? Optional.of(provider) : Optional.empty();
    }

    public boolean matches(ItemStack stack, dev.customgui.recipe.ItemSpec spec) {
        return matches(stack, spec, false);
    }

    public boolean matches(ItemStack stack, dev.customgui.recipe.ItemSpec spec, boolean allowEnchantedLore) {
        var provider = find(spec.provider()).orElse(null);
        if (provider == null) return false;
        if (spec.provider().equals("vanilla") && isCustomItem(stack, allowEnchantedLore)) return false;
        try {
            if (allowEnchantedLore && provider instanceof VanillaItemProvider vanilla) {
                return vanilla.matches(stack, spec, true);
            }
            return provider.matches(stack, spec);
        } catch (RuntimeException | LinkageError ex) { return false; }
    }

    public boolean isCustomItem(ItemStack stack) {
        return isCustomItem(stack, false);
    }

    public boolean isCustomItem(ItemStack stack, boolean allowEnchantedLore) {
        if (stack == null || stack.getType().isAir()) return false;
        // If a known identity source is degraded, vanilla ownership cannot be established safely.
        if (providers.values().stream().anyMatch(candidate -> !candidate.id().equals("vanilla") && !candidate.ready())) return true;
        if (providers.values().stream()
            .filter(candidate -> !candidate.id().equals("vanilla") && candidate.ready())
            .anyMatch(candidate -> safeIdentify(candidate, stack))) return true;
        return VanillaItemProvider.hasCustomIdentity(stack, allowEnchantedLore);
    }

    public Map<String, Boolean> statuses() {
        var statuses = new java.util.TreeMap<String, Boolean>();
        providers.forEach((id, provider) -> statuses.put(id, provider.ready()));
        return Map.copyOf(statuses);
    }

    public void validate(dev.customgui.config.ConfigSnapshot snapshot) {
        for (var menu : snapshot.menus().values()) for (var item : menu.items()) validate(item.icon());
        for (var recipe : snapshot.recipes().all()) {
            for (var requirement : recipe.requirements()) if (requirement.type().equalsIgnoreCase("item"))
                validate(dev.customgui.recipe.ItemSpec.from(requirement.values()));
            for (var result : recipe.results()) if (result.type().equalsIgnoreCase("give-item"))
                validate(dev.customgui.recipe.ItemSpec.from(result.values()));
        }
    }

    private void validate(dev.customgui.recipe.ItemSpec spec) {
        var provider = find(spec.provider()).orElseThrow(() -> new IllegalArgumentException("item provider unavailable: " + spec.provider()));
        var probe = provider.create(new dev.customgui.recipe.ItemSpec(spec.provider(), spec.id(), spec.itemType(), 1));
        if (probe == null || probe.getType().isAir() || probe.getAmount() != 1 || !matches(probe, spec))
            throw new IllegalArgumentException("item provider rejected identity: " + spec.provider() + ':' + spec.id());
    }

    public void invalidateAll() { providers.values().forEach(ItemProvider::invalidate); }
    public void refreshAll() { providers.values().forEach(ItemProvider::refresh); }
    private static boolean safeIdentify(ItemProvider provider, ItemStack stack) {
        try { return provider.identify(stack).isPresent(); }
        catch (RuntimeException | LinkageError ex) { return true; }
    }
}
