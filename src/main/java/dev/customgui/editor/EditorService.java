package dev.customgui.editor;

import dev.customgui.config.ConfigSnapshot;
import dev.customgui.gui.SlotParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class EditorService {
    private static final int PAGE_SIZE = 45;
    private final JavaPlugin plugin;
    private final Path root;
    private final Supplier<ConfigSnapshot> snapshot;
    private final Supplier<String> reload;
    private final AtomicFileStore files;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> transitions = new HashSet<>();

    public EditorService(JavaPlugin plugin, Supplier<ConfigSnapshot> snapshot, Supplier<String> reload) {
        this.plugin = plugin; this.root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.snapshot = snapshot; this.reload = reload; this.files = new AtomicFileStore(root);
    }

    public void open(Player player) {
        requirePermission(player);
        Inventory inventory = inventory(new EditorHolder(EditorHolder.View.DASHBOARD, "", "", 0), 27, "<dark_purple>CustomGUI Editor</dark_purple>");
        inventory.setItem(11, icon(Material.CHEST, "<yellow>GUI menus</yellow>", "<gray>Tạo, sửa, layout và action.</gray>"));
        inventory.setItem(13, icon(Material.CRAFTING_TABLE, "<gold>Recipes</gold>", "<gray>Sửa requirement/result trực tiếp.</gray>"));
        inventory.setItem(15, icon(Material.COMPARATOR, "<aqua>Global config</aqua>", "<gray>Security, giới hạn và messages.</gray>"));
        show(player, inventory);
    }

    public void click(Player player, EditorHolder holder, int slot, ClickType click) {
        requirePermission(player);
        try {
            switch (holder.view()) {
                case DASHBOARD -> dashboardClick(player, slot);
                case MENUS -> menusClick(player, holder.page(), slot);
                case MENU -> menuClick(player, holder.key(), slot, click);
                case LAYOUT -> layoutClick(player, holder.key(), slot, click);
                case ITEMS -> itemsClick(player, holder.key(), holder.page(), slot);
                case ITEM -> itemClick(player, holder.key(), holder.item(), slot);
                case RECIPES -> recipesClick(player, holder.page(), slot);
                case RECIPE -> recipeClick(player, holder.key(), slot);
                case RECIPE_ENTRIES -> recipeEntriesClick(player, holder.key(), holder.item(), holder.page(), slot, click);
                case CONFIG -> configClick(player, slot);
            }
        } catch (RuntimeException ex) {
            player.sendMessage(text("<red>Editor error:</red> <white>" + mini.escapeTags(ex.getMessage()) + "</white>"));
        }
    }

    public void closed(Player player, EditorHolder holder) {
        if (transitions.remove(player.getUniqueId()) || prompts.containsKey(player.getUniqueId())) return;
        if (holder.view() == EditorHolder.View.LAYOUT || holder.view() == EditorHolder.View.ITEMS || holder.view() == EditorHolder.View.ITEM)
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) openMenu(player, holder.key()); });
        else if (holder.view() == EditorHolder.View.RECIPE_ENTRIES)
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) openRecipe(player, holder.key()); });
    }

    public boolean acceptChat(Player player, String input) {
        UUID playerId = player.getUniqueId();
        Prompt prompt = prompts.remove(playerId);
        if (prompt == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current == null) return;
            if (input.equalsIgnoreCase("cancel")) { current.sendMessage(text("<yellow>Đã hủy nhập liệu.</yellow>")); prompt.cancel().run(); return; }
            try { prompt.accept().accept(input); }
            catch (RuntimeException ex) { current.sendMessage(text("<red>Giá trị không hợp lệ:</red> " + mini.escapeTags(ex.getMessage()))); prompt.cancel().run(); }
        });
        return true;
    }

    public void quit(UUID playerId) { prompts.remove(playerId); drafts.remove(playerId); transitions.remove(playerId); }
    public void shutdown() { prompts.clear(); drafts.clear(); transitions.clear(); }

    private void dashboardClick(Player player, int slot) {
        if (slot == 11) openMenus(player, 0);
        else if (slot == 13) openRecipes(player, 0);
        else if (slot == 15) openConfig(player);
    }

    private void openMenus(Player player, int page) {
        var ids = snapshot.get().menus().keySet().stream().sorted().toList();
        int maxPage = Math.max(0, (ids.size() - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, maxPage));
        Inventory inventory = inventory(new EditorHolder(EditorHolder.View.MENUS, "", "", page), 54,
            "<dark_purple>Menus</dark_purple> <gray>" + (page + 1) + '/' + (maxPage + 1) + "</gray>");
        for (int index = page * PAGE_SIZE; index < Math.min(ids.size(), (page + 1) * PAGE_SIZE); index++) {
            var menu = snapshot.get().menus().get(ids.get(index));
            inventory.setItem(index % PAGE_SIZE, icon(Material.CHEST, "<yellow>" + menu.id() + "</yellow>",
                "<gray>Rows:</gray> " + menu.rows(), "<gray>Commands:</gray> " + String.join(", ", menu.openCommands()), "<green>Click để chỉnh sửa</green>"));
        }
        inventory.setItem(45, icon(Material.LIME_DYE, "<green>Tạo menu</green>", "<gray>Nhập ID bằng chat.</gray>"));
        inventory.setItem(48, icon(Material.ARROW, "<yellow>Trang trước</yellow>"));
        inventory.setItem(49, icon(Material.BARRIER, "<red>Dashboard</red>"));
        inventory.setItem(50, icon(Material.ARROW, "<yellow>Trang sau</yellow>"));
        show(player, inventory);
    }

    private void menusClick(Player player, int page, int slot) {
        var ids = snapshot.get().menus().keySet().stream().sorted().toList();
        if (slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            if (index < ids.size()) loadMenu(player, ids.get(index));
        } else if (slot == 45) ask(player, "ID menu mới (a-z, 0-9, _.-)", () -> openMenus(player, page), input -> createMenu(player, input));
        else if (slot == 48) openMenus(player, page - 1);
        else if (slot == 49) open(player);
        else if (slot == 50) openMenus(player, page + 1);
    }

    private void loadMenu(Player player, String id) {
        Path file = findMenuFile(id);
        drafts.put(player.getUniqueId(), loadDraft(file, Kind.MENU, id));
        openMenu(player, id);
    }

    private void createMenu(Player player, String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT).trim();
        if (!id.matches("[a-z0-9][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("ID menu không hợp lệ");
        if (snapshot.get().menus().containsKey(id)) throw new IllegalArgumentException("Menu đã tồn tại");
        var yaml = new YamlConfiguration(); yaml.set("id", id); yaml.set("title", "<gold>" + id + "</gold>"); yaml.set("rows", 3);
        String command = id.replace('.', '_');
        yaml.set("open-commands", command.matches("[a-z0-9][a-z0-9_-]{0,31}") ? List.of(command) : List.of());
        var draft = new Draft(root.resolve("menus/editor-" + id + ".yml"), yaml, Kind.MENU, id, null, true);
        drafts.put(player.getUniqueId(), draft); openMenu(player, id);
    }

    private void openMenu(Player player, String id) {
        Draft draft = draft(player, Kind.MENU, id); YamlConfiguration y = draft.yaml();
        Inventory inventory = inventory(new EditorHolder(EditorHolder.View.MENU, id, "", 0), 54,
            "<dark_purple>Menu:</dark_purple> <white>" + id + (draft.dirty() ? " *</white>" : "</white>"));
        inventory.setItem(10, property(Material.NAME_TAG, "ID", id, "Không đổi trực tiếp"));
        inventory.setItem(11, property(Material.OAK_SIGN, "Title", y.getString("title", id), "Click → nhập chat"));
        inventory.setItem(12, property(Material.CHEST, "Rows", Integer.toString(y.getInt("rows", 6)), "Click → tăng, right → giảm"));
        inventory.setItem(13, property(Material.TRIPWIRE_HOOK, "Permission", y.getString("permission", "-"), "Click → nhập, '-' để xóa"));
        inventory.setItem(14, property(Material.COMMAND_BLOCK, "Open commands", join(y.getStringList("open-commands")), "Click → danh sách cách nhau dấu phẩy"));
        inventory.setItem(19, property(Material.HOPPER, "Recipe slots", String.valueOf(value(y, "recipes.slots", y.get("content-slots"))), "Click → YAML list/range"));
        inventory.setItem(20, property(Material.BOOK, "Recipe groups", join(y.getStringList("recipes.groups")), "Click → CSV"));
        inventory.setItem(21, property(Material.WRITABLE_BOOK, "Recipe categories", join(y.getStringList("recipes.categories")), "Click → CSV"));
        inventory.setItem(22, property(Material.PAPER, "Recipe name", y.getString("recipes.name", "%recipe_id%"), "Click → MiniMessage"));
        inventory.setItem(23, property(Material.MAP, "Recipe lore", String.join(" | ", y.getStringList("recipes.lore")), "Click → các dòng ngăn bởi ||"));
        inventory.setItem(24, property(Material.LEVER, "Recipe click modes", mapText(y.getConfigurationSection("recipes.click-actions")), "Ví dụ left=1,right=all"));
        inventory.setItem(30, icon(Material.ITEM_FRAME, "<aqua>Layout editor</aqua>", "<gray>Chỉnh item theo đúng slot.</gray>"));
        inventory.setItem(31, icon(Material.CHEST_MINECART, "<aqua>Item list</aqua>", "<gray>Chỉnh đầy đủ property/action.</gray>"));
        inventory.setItem(32, icon(Material.REPEATING_COMMAND_BLOCK, "<light_purple>Advanced YAML path</light_purple>", "<gray>Truy cập mọi option hiện tại/tương lai.</gray>"));
        inventory.setItem(45, icon(Material.ARROW, "<yellow>Hủy draft</yellow>", "<gray>Không ghi file.</gray>"));
        inventory.setItem(49, icon(Material.EMERALD_BLOCK, "<green>Lưu, validate & áp dụng</green>", "<gray>Có backup và rollback tự động.</gray>"));
        inventory.setItem(53, icon(Material.TNT, "<red>Xóa menu</red>", "<dark_red>Yêu cầu xác nhận.</dark_red>"));
        show(player, inventory);
    }

    private void menuClick(Player p, String id, int slot, ClickType click) {
        Draft d = draft(p, Kind.MENU, id); YamlConfiguration y = d.yaml(); Runnable reopen = () -> openMenu(p, id);
        switch (slot) {
            case 11 -> setString(p, d, "title", "Title MiniMessage", reopen);
            case 12 -> { int rows = y.getInt("rows", 6) + (click.isRightClick() ? -1 : 1); y.set("rows", Math.max(1, Math.min(6, rows))); dirty(d); reopen.run(); }
            case 13 -> setString(p, d, "permission", "Permission hoặc '-' để xóa", reopen);
            case 14 -> setCsv(p, d, "open-commands", "Các command, cách nhau dấu phẩy; '-' để tắt", reopen);
            case 19 -> setYaml(p, d, "recipes.slots", "Ví dụ [10-16, 19-25]", reopen);
            case 20 -> setCsv(p, d, "recipes.groups", "Recipe groups CSV", reopen);
            case 21 -> setCsv(p, d, "recipes.categories", "Recipe categories CSV", reopen);
            case 22 -> setString(p, d, "recipes.name", "Recipe item name", reopen);
            case 23 -> setLines(p, d, "recipes.lore", "Lore; tách dòng bằng ||", reopen);
            case 24 -> setPairs(p, d, "recipes.click-actions", "left=1,right=all,shift-left=16", reopen);
            case 30 -> openLayout(p, id);
            case 31 -> openItems(p, id, 0);
            case 32 -> advanced(p, d, "", reopen);
            case 45 -> { drafts.remove(p.getUniqueId()); openMenus(p, 0); }
            case 49 -> save(p, d, reopen);
            case 53 -> confirmDeleteMenu(p, d);
            default -> { }
        }
    }

    private void openLayout(Player player, String menuId) {
        Draft d = draft(player, Kind.MENU, menuId); int rows = Math.max(1, Math.min(6, d.yaml().getInt("rows", 6)));
        Inventory inventory = inventory(new EditorHolder(EditorHolder.View.LAYOUT, menuId, "", 0), rows * 9,
            "<dark_aqua>Layout:</dark_aqua> <white>" + menuId + "</white>");
        var resolved = resolvedItems(d.yaml(), rows * 9);
        for (int slot = 0; slot < rows * 9; slot++) {
            String item = resolved.get(slot);
            if (item != null) inventory.setItem(slot, preview(d.yaml(), item, slot));
        }
        show(player, inventory);
        player.sendMessage(text("<gray>Layout:</gray> click ô trống để tạo; click item để sửa; shift-right để bỏ slot; ESC để quay lại."));
    }

    private void layoutClick(Player p, String menuId, int slot, ClickType click) {
        Draft d = draft(p, Kind.MENU, menuId); String item = resolvedItems(d.yaml(), d.yaml().getInt("rows", 6) * 9).get(slot);
        if (item == null) ask(p, "ID item mới cho slot " + slot, () -> openLayout(p, menuId), input -> {
            String id = validNode(input); String base = "items." + id;
            if (d.yaml().isConfigurationSection(base)) throw new IllegalArgumentException("Item ID đã tồn tại");
            d.yaml().set(base + ".material", "STONE"); d.yaml().set(base + ".name", "<yellow>" + id + "</yellow>"); d.yaml().set(base + ".slot", slot);
            dirty(d); openItem(p, menuId, id);
        });
        else if (click == ClickType.SHIFT_RIGHT) { removeSlot(d, item, slot); openLayout(p, menuId); }
        else openItem(p, menuId, item);
    }

    private void openItems(Player player, String menuId, int page) {
        Draft d = draft(player, Kind.MENU, menuId); var section = d.yaml().getConfigurationSection("items");
        var ids = section == null ? List.<String>of() : section.getKeys(false).stream().sorted().toList();
        int max = Math.max(0, (ids.size() - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, max));
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.ITEMS, menuId, "", page), 54, "<dark_aqua>Items:</dark_aqua> " + menuId);
        for (int i = page * PAGE_SIZE; i < Math.min(ids.size(), (page + 1) * PAGE_SIZE); i++) inv.setItem(i % PAGE_SIZE, preview(d.yaml(), ids.get(i), -1));
        inv.setItem(45, icon(Material.LIME_DYE, "<green>Tạo item</green>")); inv.setItem(48, icon(Material.ARROW, "<yellow>Trang trước</yellow>"));
        inv.setItem(49, icon(Material.BARRIER, "<red>Menu settings</red>")); inv.setItem(50, icon(Material.ARROW, "<yellow>Trang sau</yellow>")); show(player, inv);
    }

    private void itemsClick(Player p, String menu, int page, int slot) {
        Draft d = draft(p, Kind.MENU, menu); var section = d.yaml().getConfigurationSection("items");
        var ids = section == null ? List.<String>of() : section.getKeys(false).stream().sorted().toList();
        if (slot < PAGE_SIZE) { int i = page * PAGE_SIZE + slot; if (i < ids.size()) openItem(p, menu, ids.get(i)); }
        else if (slot == 45) ask(p, "ID item mới", () -> openItems(p, menu, page), input -> {
            String id = validNode(input); if (d.yaml().isConfigurationSection("items." + id)) throw new IllegalArgumentException("ID đã tồn tại");
            d.yaml().set("items." + id + ".material", "STONE"); d.yaml().set("items." + id + ".name", "<yellow>" + id + "</yellow>");
            d.yaml().set("items." + id + ".slot", 0); dirty(d); openItem(p, menu, id);
        }); else if (slot == 48) openItems(p, menu, page - 1); else if (slot == 49) openMenu(p, menu); else if (slot == 50) openItems(p, menu, page + 1);
    }

    private void openItem(Player p, String menu, String item) {
        Draft d = draft(p, Kind.MENU, menu); String b = "items." + item; YamlConfiguration y = d.yaml();
        if (!y.isConfigurationSection(b)) throw new IllegalArgumentException("Item không tồn tại: " + item);
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.ITEM, menu, item, 0), 54, "<blue>Item:</blue> <white>" + item + "</white>");
        inv.setItem(10, property(Material.ENDER_CHEST, "Provider", y.getString(b + ".provider", "vanilla"), "vanilla/itemedit/itemsadder/oraxen/nexo/mmoitems"));
        inv.setItem(11, property(Material.STONE, "Material / ID", y.getString(b + ".material", y.getString(b + ".id", "-")), "Tự chọn material hoặc id theo provider"));
        inv.setItem(12, property(Material.IRON_SWORD, "MMOItems type", y.getString(b + ".item-type", "-"), "'-' để xóa"));
        inv.setItem(13, property(Material.SNOWBALL, "Amount", Integer.toString(y.getInt(b + ".amount", 1)), "Số item preview"));
        inv.setItem(14, property(Material.NAME_TAG, "Name", y.getString(b + ".name", item), "MiniMessage/PAPI"));
        inv.setItem(15, property(Material.MAP, "Lore", String.join(" | ", y.getStringList(b + ".lore")), "Tách dòng bằng ||"));
        inv.setItem(16, property(Material.ITEM_FRAME, "Slots", String.valueOf(value(y, b + ".slots", y.get(b + ".slot"))), "Ví dụ [0-8, 10]"));
        inv.setItem(19, property(Material.COMPARATOR, "Priority", Integer.toString(y.getInt(b + ".priority", 0)), "Số thấp thắng"));
        inv.setItem(20, property(Material.TRIPWIRE_HOOK, "View permission", y.getString(b + ".view-permission", "-"), "'-' để xóa"));
        inv.setItem(21, property(Material.ENCHANTED_BOOK, "Glow", Boolean.toString(y.getBoolean(b + ".glow", false)), "Click để bật/tắt"));
        inv.setItem(22, property(Material.RECOVERY_COMPASS, "Custom model data", y.contains(b + ".custom-model-data") ? Integer.toString(y.getInt(b + ".custom-model-data")) : "-", "'-' để xóa"));
        inv.setItem(23, property(Material.BARRIER, "Hide tooltip", Boolean.toString(y.getBoolean(b + ".hide-tooltip", false)), "Click để bật/tắt"));
        inv.setItem(24, property(Material.SHIELD, "Item flags", join(y.getStringList(b + ".item-flags")), "CSV Bukkit ItemFlag"));
        String[] clicks = {"left", "right", "shift-left", "shift-right", "middle", "click"};
        for (int i = 0; i < clicks.length; i++) inv.setItem(28 + i, property(Material.COMMAND_BLOCK, clicks[i] + " actions",
            String.join(" | ", y.getStringList(b + ".actions." + clicks[i])), "Tách action bằng ||"));
        inv.setItem(37, icon(Material.TRIPWIRE_HOOK, "<yellow>Click permissions</yellow>", "<gray>Nhập key=value CSV.</gray>"));
        inv.setItem(40, icon(Material.REPEATING_COMMAND_BLOCK, "<light_purple>Advanced item path</light_purple>"));
        inv.setItem(45, icon(Material.ARROW, "<yellow>Danh sách item</yellow>")); inv.setItem(49, icon(Material.EMERALD_BLOCK, "<green>Lưu menu</green>"));
        inv.setItem(53, icon(Material.TNT, "<red>Xóa item khỏi draft</red>")); show(p, inv);
    }

    private void itemClick(Player p, String menu, String item, int slot) {
        Draft d = draft(p, Kind.MENU, menu); String b = "items." + item; Runnable reopen = () -> openItem(p, menu, item);
        switch (slot) {
            case 10 -> setString(p, d, b + ".provider", "Item provider", reopen);
            case 11 -> ask(p, "Material hoặc custom item ID", reopen, input -> { String provider = d.yaml().getString(b + ".provider", "vanilla");
                d.yaml().set(b + (provider.equalsIgnoreCase("vanilla") ? ".material" : ".id"), input.trim());
                d.yaml().set(b + (provider.equalsIgnoreCase("vanilla") ? ".id" : ".material"), null); dirty(d); reopen.run(); });
            case 12 -> setString(p, d, b + ".item-type", "MMOItems type", reopen);
            case 13 -> setInteger(p, d, b + ".amount", 1, 64, "Amount 1..64", reopen);
            case 14 -> setString(p, d, b + ".name", "Item name MiniMessage", reopen);
            case 15 -> setLines(p, d, b + ".lore", "Lore, tách dòng bằng ||", reopen);
            case 16 -> setYaml(p, d, b + ".slots", "Slots, ví dụ [0-8, 10]", reopen);
            case 19 -> setInteger(p, d, b + ".priority", Integer.MIN_VALUE, Integer.MAX_VALUE, "Priority", reopen);
            case 20 -> setString(p, d, b + ".view-permission", "View permission", reopen);
            case 21 -> toggle(d, b + ".glow", reopen);
            case 22 -> setIntegerOrClear(p, d, b + ".custom-model-data", 0, Integer.MAX_VALUE, "CMD hoặc '-'", reopen);
            case 23 -> toggle(d, b + ".hide-tooltip", reopen);
            case 24 -> setCsv(p, d, b + ".item-flags", "Bukkit ItemFlag CSV", reopen);
            case 28, 29, 30, 31, 32, 33 -> {
                String[] clicks = {"left", "right", "shift-left", "shift-right", "middle", "click"};
                setLines(p, d, b + ".actions." + clicks[slot - 28], "Actions, tách bằng ||", reopen);
            }
            case 37 -> setPairs(p, d, b + ".click-permissions", "click=perm.node,left=perm.left", reopen);
            case 40 -> advanced(p, d, b + '.', reopen);
            case 45 -> openItems(p, menu, 0);
            case 49 -> save(p, d, reopen);
            case 53 -> ask(p, "Gõ DELETE " + item + " để xác nhận", reopen, input -> { if (!input.equals("DELETE " + item)) throw new IllegalArgumentException("Xác nhận không khớp");
                d.yaml().set(b, null); dirty(d); openItems(p, menu, 0); });
            default -> { }
        }
    }

    private void openRecipes(Player p, int page) {
        var ids = snapshot.get().recipes().all().stream().map(dev.customgui.recipe.Recipe::id).sorted().toList();
        int max = Math.max(0, (ids.size() - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, max));
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.RECIPES, "", "", page), 54, "<gold>Recipe editor</gold>");
        for (int i = page * PAGE_SIZE; i < Math.min(ids.size(), (page + 1) * PAGE_SIZE); i++) {
            var recipe = snapshot.get().recipes().find(ids.get(i)).orElseThrow();
            inv.setItem(i % PAGE_SIZE, icon(Material.CRAFTING_TABLE, "<gold>" + recipe.id() + "</gold>", "<gray>Group:</gray> " + recipe.group(), "<gray>Category:</gray> " + recipe.category()));
        }
        inv.setItem(45, icon(Material.LIME_DYE, "<green>Tạo recipe</green>")); inv.setItem(48, icon(Material.ARROW, "<yellow>Trang trước</yellow>"));
        inv.setItem(49, icon(Material.BARRIER, "<red>Dashboard</red>")); inv.setItem(50, icon(Material.ARROW, "<yellow>Trang sau</yellow>")); show(p, inv);
    }

    private void recipesClick(Player p, int page, int slot) {
        var ids = snapshot.get().recipes().all().stream().map(dev.customgui.recipe.Recipe::id).sorted().toList();
        if (slot < PAGE_SIZE) { int i = page * PAGE_SIZE + slot; if (i < ids.size()) loadRecipe(p, ids.get(i)); }
        else if (slot == 45) ask(p, "ID recipe mới", () -> openRecipes(p, page), input -> createRecipe(p, input));
        else if (slot == 48) openRecipes(p, page - 1); else if (slot == 49) open(p); else if (slot == 50) openRecipes(p, page + 1);
    }

    private void loadRecipe(Player p, String id) { Path file = findRecipeFile(id); drafts.put(p.getUniqueId(), loadDraft(file, Kind.RECIPE, id)); openRecipe(p, id); }
    private void createRecipe(Player p, String raw) {
        String id = validNode(raw); if (snapshot.get().recipes().find(id).isPresent()) throw new IllegalArgumentException("Recipe đã tồn tại");
        var y = new YamlConfiguration(); String b = "recipes." + id; y.set("config-version", 1); y.set(b + ".enabled", true); y.set(b + ".group", "default"); y.set(b + ".category", "default");
        y.set(b + ".requirements", List.of(Map.of("type", "item", "provider", "vanilla", "material", "STONE", "amount", 1, "consume", true)));
        y.set(b + ".results", List.of(Map.of("type", "give-item", "provider", "vanilla", "material", "DIAMOND", "amount", 1)));
        drafts.put(p.getUniqueId(), new Draft(root.resolve("recipes/editor-" + id + ".yml"), y, Kind.RECIPE, id, null, true)); openRecipe(p, id);
    }

    private void openRecipe(Player p, String id) {
        Draft d = draft(p, Kind.RECIPE, id); String b = "recipes." + id; YamlConfiguration y = d.yaml();
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.RECIPE, id, "", 0), 45, "<gold>Recipe:</gold> <white>" + id + "</white>");
        inv.setItem(10, property(Material.LEVER, "Enabled", Boolean.toString(y.getBoolean(b + ".enabled", true)), "Click toggle"));
        inv.setItem(11, property(Material.BOOK, "Group", y.getString(b + ".group", "default"), "Click nhập"));
        inv.setItem(12, property(Material.WRITABLE_BOOK, "Category", y.getString(b + ".category", "default"), "Click nhập"));
        inv.setItem(19, property(Material.HOPPER, "Requirements", Integer.toString(y.getMapList(b + ".requirements").size()), "Click nhập YAML list một dòng"));
        inv.setItem(20, property(Material.CHEST, "Results", Integer.toString(y.getMapList(b + ".results").size()), "Click nhập YAML list một dòng"));
        inv.setItem(22, icon(Material.REPEATING_COMMAND_BLOCK, "<light_purple>Advanced recipe path</light_purple>"));
        inv.setItem(36, icon(Material.ARROW, "<yellow>Hủy draft</yellow>")); inv.setItem(40, icon(Material.EMERALD_BLOCK, "<green>Lưu & áp dụng</green>"));
        inv.setItem(44, icon(Material.TNT, "<red>Xóa recipe</red>")); show(p, inv);
    }

    private void recipeClick(Player p, String id, int slot) {
        Draft d = draft(p, Kind.RECIPE, id); String b = "recipes." + id; Runnable reopen = () -> openRecipe(p, id);
        switch (slot) {
            case 10 -> toggle(d, b + ".enabled", reopen);
            case 11 -> setString(p, d, b + ".group", "Recipe group", reopen);
            case 12 -> setString(p, d, b + ".category", "Recipe category", reopen);
            case 19 -> openRecipeEntries(p, id, "requirements", 0);
            case 20 -> openRecipeEntries(p, id, "results", 0);
            case 22 -> advanced(p, d, b + '.', reopen);
            case 36 -> { drafts.remove(p.getUniqueId()); openRecipes(p, 0); }
            case 40 -> save(p, d, reopen);
            case 44 -> ask(p, "Gõ DELETE " + id, reopen, input -> { if (!input.equals("DELETE " + id)) throw new IllegalArgumentException("Xác nhận không khớp");
                d.yaml().set(b, null); dirty(d); save(p, d, () -> { drafts.remove(p.getUniqueId()); openRecipes(p, 0); }); });
            default -> { }
        }
    }

    private void openRecipeEntries(Player p, String recipe, String kind, int page) {
        Draft d = draft(p, Kind.RECIPE, recipe); String path = "recipes." + recipe + '.' + kind;
        var entries = d.yaml().getMapList(path); int max = Math.max(0, (entries.size() - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, max));
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.RECIPE_ENTRIES, recipe, kind, page), 54,
            "<gold>" + kind + ":</gold> <white>" + recipe + "</white>");
        for (int i = page * PAGE_SIZE; i < Math.min(entries.size(), (page + 1) * PAGE_SIZE); i++) {
            Map<?, ?> entry = entries.get(i); String type = String.valueOf(entry.containsKey("type") ? entry.get("type") : "unknown");
            inv.setItem(i % PAGE_SIZE, icon(kind.equals("requirements") ? Material.HOPPER : Material.CHEST,
                "<yellow>#" + (i + 1) + " " + mini.escapeTags(type) + "</yellow>",
                "<gray>" + mini.escapeTags(abbreviate(entry.toString(), 180)) + "</gray>", "<green>Click: sửa</green>", "<red>Shift-right: xóa khỏi draft</red>"));
        }
        inv.setItem(45, icon(Material.LIME_DYE, "<green>Thêm entry</green>", "<gray>Nhập YAML map một dòng.</gray>"));
        inv.setItem(48, icon(Material.ARROW, "<yellow>Trang trước</yellow>")); inv.setItem(49, icon(Material.BARRIER, "<red>Recipe settings</red>"));
        inv.setItem(50, icon(Material.ARROW, "<yellow>Trang sau</yellow>")); show(p, inv);
    }

    private void recipeEntriesClick(Player p, String recipe, String kind, int page, int slot, ClickType click) {
        Draft d = draft(p, Kind.RECIPE, recipe); String path = "recipes." + recipe + '.' + kind; var entries = new ArrayList<Map<?, ?>>(d.yaml().getMapList(path));
        if (slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot; if (index >= entries.size()) return;
            if (click == ClickType.SHIFT_RIGHT) { entries.remove(index); d.yaml().set(path, entries); dirty(d); openRecipeEntries(p, recipe, kind, page); }
            else ask(p, "YAML map cho entry #" + (index + 1), () -> openRecipeEntries(p, recipe, kind, page), input -> {
                Map<?, ?> parsed = yamlMap(input); entries.set(index, parsed); d.yaml().set(path, entries); dirty(d); openRecipeEntries(p, recipe, kind, page);
            });
        } else if (slot == 45) ask(p, "YAML map mới, ví dụ {type: item, material: STONE, amount: 1}",
            () -> openRecipeEntries(p, recipe, kind, page), input -> { entries.add(yamlMap(input)); d.yaml().set(path, entries); dirty(d); openRecipeEntries(p, recipe, kind, page); });
        else if (slot == 48) openRecipeEntries(p, recipe, kind, page - 1);
        else if (slot == 49) openRecipe(p, recipe);
        else if (slot == 50) openRecipeEntries(p, recipe, kind, page + 1);
    }

    private void openConfig(Player p) {
        Draft d = drafts.get(p.getUniqueId());
        if (d == null || d.kind() != Kind.CONFIG) { d = loadDraft(root.resolve("config.yml"), Kind.CONFIG, "config"); drafts.put(p.getUniqueId(), d); }
        Inventory inv = inventory(new EditorHolder(EditorHolder.View.CONFIG, "config", "", 0), 27, "<aqua>Global config editor</aqua>");
        inv.setItem(10, property(Material.BUNDLE, "Max batch size", Integer.toString(d.yaml().getInt("security.max-batch-size", 256)), "1..4096"));
        inv.setItem(12, property(Material.CHEST, "Player inventory interaction", Boolean.toString(d.yaml().getBoolean("security.allow-player-inventory-interaction", false)), "Click toggle"));
        inv.setItem(14, icon(Material.REPEATING_COMMAND_BLOCK, "<light_purple>Advanced config path</light_purple>", "<gray>Sửa messages và option bất kỳ.</gray>"));
        inv.setItem(18, icon(Material.ARROW, "<yellow>Hủy draft</yellow>")); inv.setItem(22, icon(Material.EMERALD_BLOCK, "<green>Lưu & áp dụng</green>")); show(p, inv);
    }

    private void configClick(Player p, int slot) {
        Draft d = draft(p, Kind.CONFIG, "config"); Runnable reopen = () -> openConfig(p);
        if (slot == 10) setInteger(p, d, "security.max-batch-size", 1, 4096, "Max batch 1..4096", reopen);
        else if (slot == 12) toggle(d, "security.allow-player-inventory-interaction", reopen);
        else if (slot == 14) advanced(p, d, "", reopen);
        else if (slot == 18) { drafts.remove(p.getUniqueId()); open(p); }
        else if (slot == 22) save(p, d, reopen);
    }

    private void advanced(Player p, Draft d, String prefix, Runnable reopen) {
        ask(p, "YAML path (không gồm prefix '" + prefix + "')", reopen, path -> {
            String safePath = path.trim(); if (!safePath.matches("[a-zA-Z0-9_.-]{1,160}")) throw new IllegalArgumentException("Path không hợp lệ");
            if (d.kind() == Kind.MENU && prefix.isEmpty() && safePath.equals("id")) throw new IllegalArgumentException("Menu ID là immutable; hãy tạo menu mới");
            ask(p, "Giá trị YAML một dòng; gõ ~ để xóa. Path: " + prefix + safePath, reopen, input -> {
                d.yaml().set(prefix + safePath, input.trim().equals("~") ? null : yamlValue(input)); dirty(d); reopen.run();
            });
        });
    }

    private void save(Player p, Draft d, Runnable success) {
        try {
            if (!sameBase(d)) { p.sendMessage(text("<red>File đã thay đổi bên ngoài editor. Draft không được ghi đè; hãy hủy và mở lại.</red>")); return; }
            String content = d.yaml().saveToString(); var backup = files.write(d.file(), content); String error = reload.get();
            if (error != null) {
                files.restore(backup); String restoreError = reload.get();
                p.sendMessage(text("<red>Validation thất bại, đã rollback:</red> " + mini.escapeTags(error)
                    + (restoreError == null ? "" : " <dark_red>Restore reload lỗi: " + mini.escapeTags(restoreError) + "</dark_red>")));
                return;
            }
            d.baseContent = content; d.dirty = false; p.sendMessage(text("<green>Đã lưu, validate và áp dụng. Backup nằm trong backups/editor.</green>")); success.run();
        } catch (IOException ex) { p.sendMessage(text("<red>Không thể ghi file:</red> " + mini.escapeTags(ex.getMessage()))); }
    }

    private void confirmDeleteMenu(Player p, Draft d) {
        ask(p, "Gõ DELETE " + d.key() + " để xóa menu", () -> openMenu(p, d.key()), input -> {
            if (!input.equals("DELETE " + d.key())) throw new IllegalArgumentException("Xác nhận không khớp");
            try {
                if (!sameBase(d)) throw new IllegalArgumentException("File đã thay đổi bên ngoài editor");
                var backup = files.remove(d.file()); String error = reload.get();
                if (error != null) { files.restore(backup); reload.get(); throw new IllegalArgumentException("Không thể xóa: " + error); }
                drafts.remove(p.getUniqueId()); p.sendMessage(text("<green>Đã xóa menu; backup vẫn được giữ.</green>")); openMenus(p, 0);
            } catch (IOException ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
        });
    }

    private void setString(Player p, Draft d, String path, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { d.yaml().set(path, clear(input) ? null : input); dirty(d); reopen.run(); });
    }
    private void setCsv(Player p, Draft d, String path, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { d.yaml().set(path, clear(input) ? List.of() : csv(input)); dirty(d); reopen.run(); });
    }
    private void setLines(Player p, Draft d, String path, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { d.yaml().set(path, clear(input) ? List.of() : java.util.Arrays.stream(input.split("\\|\\|", -1)).map(String::trim).toList()); dirty(d); reopen.run(); });
    }
    private void setPairs(Player p, Draft d, String path, String label, Runnable reopen) {
        ask(p, label, reopen, input -> {
            var parsed = new LinkedHashMap<String, String>();
            if (!clear(input)) for (String pair : input.split(",")) {
                String[] parts = pair.trim().split("=", 2);
                if (parts.length != 2 || parts[0].isBlank()) throw new IllegalArgumentException("Cần key=value");
                parsed.put(parts[0].trim(), parts[1].trim());
            }
            d.yaml().set(path, null);
            parsed.forEach((key, value) -> d.yaml().set(path + '.' + key, value));
            dirty(d); reopen.run();
        });
    }
    private void setYaml(Player p, Draft d, String path, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { d.yaml().set(path, clear(input) ? null : yamlValue(input)); dirty(d); reopen.run(); });
    }
    private void setInteger(Player p, Draft d, String path, int min, int max, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { int value = Integer.parseInt(input.trim()); if (value < min || value > max) throw new IllegalArgumentException("Cần " + min + ".." + max);
            d.yaml().set(path, value); dirty(d); reopen.run(); });
    }
    private void setIntegerOrClear(Player p, Draft d, String path, int min, int max, String label, Runnable reopen) {
        ask(p, label, reopen, input -> { if (clear(input)) d.yaml().set(path, null); else { int value = Integer.parseInt(input.trim());
            if (value < min || value > max) throw new IllegalArgumentException("Cần " + min + ".." + max); d.yaml().set(path, value); } dirty(d); reopen.run(); });
    }
    private void toggle(Draft d, String path, Runnable reopen) { d.yaml().set(path, !d.yaml().getBoolean(path, false)); dirty(d); reopen.run(); }

    private void ask(Player p, String description, Runnable cancel, Consumer<String> accept) {
        UUID id = p.getUniqueId(); Prompt prompt = new Prompt(Instant.now().plusSeconds(120), accept, cancel); prompts.put(id, prompt);
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) transitions.add(id);
        p.closeInventory(); p.sendMessage(text("<aqua>Editor input:</aqua> <white>" + mini.escapeTags(description) + "</white>"));
        p.sendMessage(text("<gray>Nhập trong chat trong 120 giây, hoặc gõ <yellow>cancel</yellow>.</gray>"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Prompt current = prompts.get(id);
            if (current == prompt && Instant.now().isAfter(prompt.expires())) { prompts.remove(id); if (p.isOnline()) { p.sendMessage(text("<yellow>Editor input đã hết hạn.</yellow>")); cancel.run(); } }
        }, 20L * 121);
    }

    private void show(Player p, Inventory inventory) {
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) transitions.add(p.getUniqueId());
        p.openInventory(inventory);
    }
    private Inventory inventory(EditorHolder holder, int size, String title) { return Bukkit.createInventory(holder, size, text(title)); }
    private Component text(String input) { try { return mini.deserialize(input); } catch (RuntimeException ex) { return Component.text(input); } }
    private ItemStack property(Material material, String name, String value, String hint) {
        return icon(material, "<yellow>" + name + "</yellow>", "<white>" + mini.escapeTags(value == null ? "-" : value) + "</white>", "<dark_gray>" + hint + "</dark_gray>");
    }
    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(text(name));
        meta.lore(java.util.Arrays.stream(lore).map(this::text).toList()); stack.setItemMeta(meta); return stack;
    }
    private ItemStack preview(YamlConfiguration y, String item, int slot) {
        String b = "items." + item; Material material = Material.matchMaterial(y.getString(b + ".material", "PAPER"));
        if (material == null || material.isAir()) material = y.getString(b + ".provider", "vanilla").equalsIgnoreCase("vanilla") ? Material.BARRIER : Material.ENDER_CHEST;
        return icon(material, y.getString(b + ".name", "<yellow>" + item + "</yellow>"), "<gray>ID:</gray> " + item,
            slot < 0 ? "<gray>Click để sửa</gray>" : "<gray>Slot:</gray> " + slot, "<dark_gray>Editor preview; item thật do provider render.</dark_gray>");
    }

    private Map<Integer, String> resolvedItems(YamlConfiguration y, int size) {
        var section = y.getConfigurationSection("items"); if (section == null) return Map.of();
        record Entry(String id, int priority) {}
        var resolved = new HashMap<Integer, Entry>();
        for (String id : section.getKeys(false)) {
            String b = "items." + id; Object raw = value(y, b + ".slots", y.get(b + ".slot")); if (raw == null) continue;
            int priority = y.getInt(b + ".priority", 0);
            for (int slot : SlotParser.parse(raw, size)) resolved.compute(slot, (ignored, old) -> old == null || priority < old.priority() ? new Entry(id, priority) : old);
        }
        var output = new HashMap<Integer, String>(); resolved.forEach((slot, entry) -> output.put(slot, entry.id())); return output;
    }
    private void removeSlot(Draft d, String item, int slot) {
        String b = "items." + item; Object raw = value(d.yaml(), b + ".slots", d.yaml().get(b + ".slot"));
        var slots = new ArrayList<>(SlotParser.parse(raw, d.yaml().getInt("rows", 6) * 9)); slots.remove(Integer.valueOf(slot));
        if (slots.isEmpty()) d.yaml().set(b, null);
        else { d.yaml().set(b + ".slot", null); d.yaml().set(b + ".slots", slots); }
        dirty(d);
    }

    private Draft loadDraft(Path file, Kind kind, String key) {
        try {
            String content = Files.readString(file); var yaml = new YamlConfiguration(); yaml.loadFromString(content);
            return new Draft(file, yaml, kind, key, content, false);
        } catch (IOException | InvalidConfigurationException ex) { throw new IllegalArgumentException("Không thể mở " + file + ": " + ex.getMessage(), ex); }
    }
    private Path findMenuFile(String id) { return yamlFiles("menus").stream().filter(path -> id.equals(load(path).getString("id"))).findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file menu " + id)); }
    private Path findRecipeFile(String id) { return yamlFiles("recipes").stream().filter(path -> load(path).isConfigurationSection("recipes." + id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file recipe " + id)); }
    private List<Path> yamlFiles(String directory) {
        File[] found = root.resolve(directory).toFile().listFiles((ignored, name) -> name.endsWith(".yml"));
        if (found == null) return List.of(); return java.util.Arrays.stream(found).map(File::toPath).sorted().toList();
    }
    private YamlConfiguration load(Path path) { var y = new YamlConfiguration(); try { y.load(path.toFile()); return y; } catch (Exception ex) { throw new IllegalArgumentException(ex.getMessage(), ex); } }
    private Draft draft(Player p, Kind kind, String key) { Draft d = drafts.get(p.getUniqueId()); if (d == null || d.kind() != kind || !d.key().equals(key)) throw new IllegalArgumentException("Draft đã hết hạn; mở lại editor"); return d; }
    private boolean sameBase(Draft d) throws IOException { return d.baseContent == null ? !Files.exists(d.file()) : Files.isRegularFile(d.file()) && Files.readString(d.file()).equals(d.baseContent); }
    private static void dirty(Draft d) { d.dirty = true; }
    private void requirePermission(Player p) { if (!p.hasPermission("customgui.editor")) throw new IllegalArgumentException("Bạn không có quyền customgui.editor"); }
    private static String validNode(String input) { String id = input.toLowerCase(Locale.ROOT).trim(); if (!id.matches("[a-z0-9][a-z0-9_-]{0,127}")) throw new IllegalArgumentException("Editor ID chỉ hỗ trợ a-z, 0-9, _ và -"); return id; }
    private static boolean clear(String input) { return input.trim().equals("-") || input.trim().equals("~"); }
    private static List<String> csv(String input) { return java.util.Arrays.stream(input.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList(); }
    private static String join(List<String> values) { return values.isEmpty() ? "-" : String.join(", ", values); }
    private static Object value(YamlConfiguration y, String preferred, Object fallback) { Object value = y.get(preferred); return value == null ? fallback : value; }
    private static String mapText(org.bukkit.configuration.ConfigurationSection section) { if (section == null) return "-"; return section.getKeys(false).stream().sorted().map(k -> k + '=' + section.getString(k)).collect(java.util.stream.Collectors.joining(", ")); }
    private static Object yamlValue(String input) {
        var y = new YamlConfiguration(); try { y.loadFromString("value: " + input); return plainValue(y.get("value")); }
        catch (InvalidConfigurationException ex) { throw new IllegalArgumentException("YAML value lỗi: " + ex.getMessage(), ex); }
    }
    private static Map<?, ?> yamlMap(String input) {
        Object value = yamlValue(input);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Cần YAML map dạng {key: value}");
        return new LinkedHashMap<>(map);
    }
    private static Object plainValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            var output = new LinkedHashMap<String, Object>();
            section.getKeys(false).forEach(key -> output.put(key, plainValue(section.get(key))));
            return output;
        }
        if (value instanceof Map<?, ?> map) {
            var output = new LinkedHashMap<String, Object>();
            map.forEach((key, nested) -> output.put(String.valueOf(key), plainValue(nested)));
            return output;
        }
        if (value instanceof List<?> list) return list.stream().map(EditorService::plainValue).toList();
        return value;
    }
    private static String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 3) + "..."; }

    private enum Kind { MENU, RECIPE, CONFIG }
    private static final class Draft {
        private final Path file; private final YamlConfiguration yaml; private final Kind kind; private final String key;
        private String baseContent; private boolean dirty;
        private Draft(Path file, YamlConfiguration yaml, Kind kind, String key, String baseContent, boolean dirty) {
            this.file = file; this.yaml = yaml; this.kind = kind; this.key = key; this.baseContent = baseContent; this.dirty = dirty;
        }
        Path file() { return file; } YamlConfiguration yaml() { return yaml; } Kind kind() { return kind; } String key() { return key; } boolean dirty() { return dirty; }
    }
    private record Prompt(Instant expires, Consumer<String> accept, Runnable cancel) {}
}
