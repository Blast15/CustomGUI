package dev.customgui.editor;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class EditorListener implements Listener {
    private final EditorService editor;
    public EditorListener(EditorService editor) { this.editor = editor; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EditorHolder holder)) return;
        event.setCancelled(true);
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < size && event.getWhoClicked() instanceof org.bukkit.entity.Player player
            && (event.getClick().isLeftClick() || event.getClick().isRightClick() || event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE))
            editor.click(player, holder, event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof EditorHolder) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof EditorHolder holder && event.getPlayer() instanceof org.bukkit.entity.Player player)
            editor.closed(player, holder);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (editor.acceptChat(event.getPlayer(), input)) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { editor.quit(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) event.setCancelled(true);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) event.setCancelled(true);
    }
}
