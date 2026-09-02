package dev.customgui.event;

import dev.customgui.gui.GuiService;
import dev.customgui.gui.SessionHolder;
import dev.customgui.gui.SessionRegistry;
import dev.customgui.transaction.PlayerTransactionExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GuiListener implements Listener {
    private final GuiService gui;
    private final SessionRegistry sessions;
    private final PlayerTransactionExecutor transactions;

    public GuiListener(GuiService gui, SessionRegistry sessions, PlayerTransactionExecutor transactions) {
        this.gui = gui; this.sessions = sessions; this.transactions = transactions;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SessionHolder holder)) return;
        boolean top = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        if (top || !gui.allowPlayerInventoryInteraction() || !isLocalPlayerInventoryAction(event.getAction()))
            event.setCancelled(true);
        if (top && event.getWhoClicked() instanceof org.bukkit.entity.Player player)
            gui.click(player, holder, event.getRawSlot(), event.getClick());
    }

    private static boolean isLocalPlayerInventoryAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, CLONE_STACK, NOTHING -> true;
            default -> false;
        };
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SessionHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (!gui.allowPlayerInventoryInteraction() || event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof SessionHolder holder)
            sessions.valid(event.getPlayer().getUniqueId(), holder.session().sessionId())
                .ifPresent(session -> sessions.invalidate(event.getPlayer().getUniqueId()));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        sessions.invalidate(event.getPlayer().getUniqueId());
        transactions.release(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof SessionHolder) event.setCancelled(true);
    }

    @EventHandler public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder(false) instanceof SessionHolder) event.setCancelled(true);
    }

    @EventHandler public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        sessions.invalidate(event.getEntity().getUniqueId());
        transactions.release(event.getEntity().getUniqueId());
    }
}
