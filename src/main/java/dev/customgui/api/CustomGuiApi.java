package dev.customgui.api;

import dev.customgui.integration.item.ItemProvider;
import dev.customgui.recipe.Recipe;
import java.util.Optional;
import org.bukkit.entity.Player;

public interface CustomGuiApi {
    void registerItemProvider(ItemProvider provider);
    Optional<Recipe> recipe(String id);
    boolean openMenu(Player player, String menuId);
}
