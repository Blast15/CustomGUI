package dev.customgui.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record EditorHolder(View view, String key, String item, int page) implements InventoryHolder {
    public enum View { DASHBOARD, MENUS, MENU, LAYOUT, ITEMS, ITEM, RECIPES, RECIPE, RECIPE_ENTRIES, CONFIG }
    @Override public Inventory getInventory() { throw new UnsupportedOperationException("editor holder does not own inventory reference"); }
}
