package dev.customgui.api;

import dev.customgui.config.ConfigSnapshot;
import dev.customgui.gui.GuiService;
import dev.customgui.integration.item.ItemProvider;
import dev.customgui.integration.item.ItemProviderRegistry;
import dev.customgui.recipe.Recipe;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

public final class CustomGuiApiImpl implements CustomGuiApi {
    private final ItemProviderRegistry providers;
    private final Supplier<ConfigSnapshot> snapshot;
    private final GuiService gui;
    public CustomGuiApiImpl(ItemProviderRegistry providers, Supplier<ConfigSnapshot> snapshot, GuiService gui) {
        this.providers = providers; this.snapshot = snapshot; this.gui = gui;
    }
    @Override public void registerItemProvider(ItemProvider provider) { providers.register(provider); }
    @Override public Optional<Recipe> recipe(String id) { return snapshot.get().recipes().find(id); }
    @Override public boolean openMenu(Player player, String menuId) { return gui.open(player, menuId, 0); }
}
