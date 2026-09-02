package dev.customgui.integration.item;

import dev.customgui.recipe.ItemSpec;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemProvider {
    String id();
    ItemStack create(ItemSpec spec);
    boolean matches(ItemStack stack, ItemSpec spec);
    default Optional<String> identify(ItemStack stack) { return Optional.empty(); }
    default boolean ready() { return true; }
    default void invalidate() {}
    default void refresh() {}
}
