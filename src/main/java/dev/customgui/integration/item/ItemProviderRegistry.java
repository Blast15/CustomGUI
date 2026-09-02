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
        var provider = find(spec.provider()).orElse(null);
        if (provider == null) return false;
        if (spec.provider().equals("vanilla") && providers.values().stream()
            .filter(candidate -> !candidate.id().equals("vanilla") && candidate.ready())
            .anyMatch(candidate -> safeIdentify(candidate, stack))) return false;
        try { return provider.matches(stack, spec); }
        catch (RuntimeException | LinkageError ex) { return false; }
    }

    public Map<String, Boolean> statuses() {
        var statuses = new java.util.TreeMap<String, Boolean>();
        providers.forEach((id, provider) -> statuses.put(id, provider.ready()));
        return Map.copyOf(statuses);
    }

    public void invalidateAll() { providers.values().forEach(ItemProvider::invalidate); }
    public void refreshAll() { providers.values().forEach(ItemProvider::refresh); }
    private static boolean safeIdentify(ItemProvider provider, ItemStack stack) {
        try { return provider.identify(stack).isPresent(); }
        catch (RuntimeException | LinkageError ex) { return true; }
    }
}
