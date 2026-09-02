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
        return stack != null && stack.getType() == material(spec);
    }

    @Override public java.util.Optional<String> identify(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? java.util.Optional.empty() : java.util.Optional.of(stack.getType().getKey().toString());
    }

    private Material material(ItemSpec spec) {
        var material = Material.matchMaterial(spec.id().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem())
            throw new IllegalArgumentException("invalid vanilla material: " + spec.id());
        return material;
    }
}
