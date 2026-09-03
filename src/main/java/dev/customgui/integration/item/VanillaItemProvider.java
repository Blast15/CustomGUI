package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class VanillaItemProvider implements ItemProvider {
    @Override public String id() { return "vanilla"; }

    @Override public ItemStack create(ItemSpec spec) {
        return new ItemStack(material(spec), spec.amount());
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        return matches(stack, spec, false);
    }

    public boolean matches(ItemStack stack, ItemSpec spec, boolean allowEnchantedLore) {
        return stack != null && stack.getType() == material(spec) && !hasCustomIdentity(stack, allowEnchantedLore);
    }

    @Override public java.util.Optional<String> identify(ItemStack stack) {
        return stack == null || stack.getType().isAir() || hasCustomIdentity(stack, false)
            ? java.util.Optional.empty()
            : java.util.Optional.of(stack.getType().getKey().toString());
    }

    public static boolean hasCustomIdentity(ItemStack stack) {
        return hasCustomIdentity(stack, false);
    }

    @SuppressWarnings("deprecation")
    public static boolean hasCustomIdentity(ItemStack stack, boolean allowEnchantedLore) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!stack.hasItemMeta()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (!meta.getPersistentDataContainer().getKeys().isEmpty()) return true;
        if (meta.hasCustomModelData()) return true;
        if (hasItemModel(meta)) return true;
        if (meta.hasDisplayName()) return true;
        if (!allowEnchantedLore && meta.hasLore()) return true;
        if (meta.hasAttributeModifiers()) return true;
        if (!meta.getItemFlags().isEmpty()) return true;
        return false;
    }

    private static boolean hasItemModel(org.bukkit.inventory.meta.ItemMeta meta) {
        try {
            java.lang.reflect.Method method = meta.getClass().getMethod("hasItemModel");
            return Boolean.TRUE.equals(method.invoke(meta));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Material material(ItemSpec spec) {
        var material = Material.matchMaterial(spec.id().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem())
            throw new IllegalArgumentException("invalid vanilla material: " + spec.id());
        return material;
    }
}
