package dev.customgui.transaction;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

final class ShulkerBoxInventory {
    private ShulkerBoxInventory() {}

    static ItemStack[] contents(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta meta)
            || !(meta.getBlockState() instanceof ShulkerBox shulker)) return null;
        return InventorySimulation.cloneContents(shulker.getInventory().getStorageContents());
    }

    static void setContents(ItemStack item, ItemStack[] contents) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)
            || !(meta.getBlockState() instanceof ShulkerBox shulker))
            throw new IllegalArgumentException("item is not a shulker box");
        shulker.getInventory().setStorageContents(InventorySimulation.cloneContents(contents));
        meta.setBlockState(shulker);
        if (!item.setItemMeta(meta)) throw new IllegalStateException("could not update shulker box contents");
    }
}
