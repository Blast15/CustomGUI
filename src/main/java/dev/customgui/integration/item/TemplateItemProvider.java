package dev.customgui.integration.item;

import dev.customgui.editor.AtomicFileStore;
import dev.customgui.recipe.ItemSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** Exact ItemMeta templates for plugins that expose no stable identity API. */
public final class TemplateItemProvider implements ItemProvider {
    private static final int MAX_TEMPLATES = 2048;
    private final Path file;
    private final AtomicFileStore files;
    private volatile Map<String, ItemStack> templates = Map.of();
    private volatile Map<Material, List<Map.Entry<String, ItemStack>>> byMaterial = Map.of();

    public TemplateItemProvider(Path dataFolder) {
        Path root = dataFolder.toAbsolutePath().normalize(); this.file = root.resolve("item-templates.yml"); this.files = new AtomicFileStore(root); refresh();
    }

    @Override public String id() { return "template"; }
    @Override public boolean ready() { return true; }

    @Override public ItemStack create(ItemSpec spec) {
        ItemStack template = templates.get(spec.id().toLowerCase(Locale.ROOT));
        if (template == null) throw new IllegalArgumentException("unknown template item: " + spec.id());
        ItemStack output = template.clone(); output.setAmount(spec.amount()); return output;
    }

    @Override public boolean matches(ItemStack stack, ItemSpec spec) {
        ItemStack template = templates.get(spec.id().toLowerCase(Locale.ROOT));
        return stack != null && template != null && template.isSimilar(stack);
    }

    @Override public Optional<String> identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return Optional.empty();
        return byMaterial.getOrDefault(stack.getType(), List.of()).stream()
            .filter(entry -> entry.getValue().isSimilar(stack)).map(Map.Entry::getKey).findFirst();
    }

    public synchronized Capture capture(String inputId, ItemStack held, boolean replace) throws IOException {
        String itemId = inputId.toLowerCase(Locale.ROOT);
        if (!itemId.matches("[a-z0-9][a-z0-9_-]{0,127}")) throw new IllegalArgumentException("Template ID chỉ hỗ trợ a-z, 0-9, _ và -");
        if (held == null || held.getType().isAir()) throw new IllegalArgumentException("Hãy cầm item cần capture trên tay chính");
        if (!replace && templates.containsKey(itemId)) throw new IllegalArgumentException("Template đã tồn tại; thêm 'replace' để ghi đè");
        for (var entry : templates.entrySet()) if (!entry.getKey().equals(itemId) && entry.getValue().isSimilar(held))
            throw new IllegalArgumentException("Item giống hệt template đã có: " + entry.getKey());
        if (!templates.containsKey(itemId) && templates.size() >= MAX_TEMPLATES) throw new IllegalArgumentException("Đã đạt giới hạn " + MAX_TEMPLATES + " templates");

        var yaml = load(); ItemStack stored = held.clone(); stored.setAmount(1); yaml.set("items." + itemId, stored);
        files.write(file, yaml.saveToString()); boolean replaced = templates.containsKey(itemId); refresh();
        return replaced ? Capture.REPLACED : Capture.CREATED;
    }

    public int size() { return templates.size(); }

    @Override public synchronized void refresh() {
        var yaml = load(); var loaded = new LinkedHashMap<String, ItemStack>();
        var section = yaml.getConfigurationSection("items");
        if (section != null) for (String key : section.getKeys(false)) {
            ItemStack stack = section.getItemStack(key);
            if (stack == null || stack.getType().isAir()) continue;
            stack = stack.clone(); stack.setAmount(1); loaded.put(key.toLowerCase(Locale.ROOT), stack);
        }
        if (loaded.size() > MAX_TEMPLATES) throw new IllegalArgumentException("item-templates.yml exceeds " + MAX_TEMPLATES + " entries");
        var index = new LinkedHashMap<Material, List<Map.Entry<String, ItemStack>>>();
        loaded.entrySet().forEach(entry -> index.computeIfAbsent(entry.getValue().getType(), ignored -> new ArrayList<>()).add(entry));
        var immutableIndex = new LinkedHashMap<Material, List<Map.Entry<String, ItemStack>>>();
        index.forEach((material, entries) -> immutableIndex.put(material, List.copyOf(entries)));
        templates = Map.copyOf(loaded); byMaterial = Map.copyOf(immutableIndex);
    }

    private YamlConfiguration load() {
        var yaml = new YamlConfiguration();
        if (!Files.isRegularFile(file)) { yaml.set("config-version", 1); return yaml; }
        try { yaml.load(file.toFile()); return yaml; }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid item-templates.yml: " + ex.getMessage(), ex); }
    }

    public enum Capture { CREATED, REPLACED }
}
