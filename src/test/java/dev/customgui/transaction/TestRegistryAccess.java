package dev.customgui.transaction;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;
import org.mockito.Mockito;

@SuppressWarnings({"deprecation", "removal", "unchecked"})
public class TestRegistryAccess implements RegistryAccess {
    private final Registry<BlockType> blockRegistry = Mockito.mock(Registry.class);
    private final Registry<ItemType> itemRegistry = Mockito.mock(Registry.class);

    public TestRegistryAccess() {
        Mockito.when(blockRegistry.get(Mockito.any(NamespacedKey.class))).thenAnswer(inv -> {
            NamespacedKey key = inv.getArgument(0);
            BlockType blockType = Mockito.mock(BlockType.class);
            boolean air = key != null && (key.getKey().equals("air") || key.getKey().equals("cave_air") || key.getKey().equals("void_air"));
            Mockito.when(blockType.isAir()).thenReturn(air);
            Mockito.when(blockType.getKey()).thenReturn(key);
            return blockType;
        });

        Mockito.when(itemRegistry.get(Mockito.any(NamespacedKey.class))).thenAnswer(inv -> {
            NamespacedKey key = inv.getArgument(0);
            if (key == null) return null;
            Material mat = Material.matchMaterial(key.getKey());
            if (mat == null || mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return null;
            ItemType itemType = Mockito.mock(ItemType.class);
            Mockito.when(itemType.getKey()).thenReturn(key);
            Mockito.when(itemType.asMaterial()).thenReturn(mat);
            Mockito.when(itemType.isEnabledByFeature(Mockito.any())).thenReturn(true);
            return itemType;
        });
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        if (BlockType.class.isAssignableFrom(type)) {
            return (Registry<T>) blockRegistry;
        }
        if (ItemType.class.isAssignableFrom(type)) {
            return (Registry<T>) itemRegistry;
        }
        return Mockito.mock(Registry.class);
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        if (key != null && key.key() != null) {
            String subKey = key.key().asString();
            if (subKey.contains("block")) return (Registry<T>) blockRegistry;
            if (subKey.contains("item")) return (Registry<T>) itemRegistry;
        }
        return Mockito.mock(Registry.class);
    }
}
