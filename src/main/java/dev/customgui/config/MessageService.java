package dev.customgui.config;

import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MessageService {
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry("unknown-recipe", "<red>Không tồn tại recipe %recipe%.</red>"),
        Map.entry("unknown-player", "<red>Không tìm thấy người chơi %player%.</red>"),
        Map.entry("player-required", "<red>Lệnh này cần một người chơi.</red>"),
        Map.entry("invalid-page", "<red>Số trang không hợp lệ.</red>"),
        Map.entry("invalid-amount", "<red>Số lần đổi không hợp lệ hoặc vượt giới hạn.</red>"),
        Map.entry("nothing-to-exchange", "<yellow>Không có lượt đổi hợp lệ nào.</yellow>"),
        Map.entry("menu-list", "<yellow>Menu:</yellow> <white>%menus%</white>"),
        Map.entry("info", "<gold>CustomGUI</gold> <gray>|</gray> %menus% menu, %recipes% recipe, %invalid% recipe lỗi"),
        Map.entry("providers", "<yellow>Item providers:</yellow> <white>%providers%</white>"),
        Map.entry("capture-usage", "<yellow>/customgui capture &lt;id&gt; [replace]</yellow> — cầm item trên tay chính."),
        Map.entry("capture-success", "<green>Đã capture template <white>%item%</white> (%mode%). Tổng: %count%.</green>"),
        Map.entry("capture-failed", "<red>Không thể capture item: %error%</red>"));
    private final Supplier<ConfigSnapshot> snapshot;
    private final MiniMessage mini = MiniMessage.miniMessage();
    public MessageService(Supplier<ConfigSnapshot> snapshot) { this.snapshot = snapshot; }

    public Component render(String key) { return render(key, Map.of()); }
    public Component render(String key, Map<String, String> values) {
        String text = snapshot.get().messages().getOrDefault(key, DEFAULTS.getOrDefault(key, "<red>Missing message: " + key + "</red>"));
        for (var value : values.entrySet()) text = text.replace('%' + value.getKey() + '%', escape(value.getValue()));
        try { return mini.deserialize(text); } catch (RuntimeException ex) { return Component.text("Invalid message: " + key); }
    }
    private String escape(String value) { return mini.escapeTags(value == null ? "" : value); }
}
