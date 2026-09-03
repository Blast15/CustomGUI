package dev.customgui.config;

import dev.customgui.gui.SlotParser;
import dev.customgui.recipe.Recipe;
import dev.customgui.recipe.RecipeRegistry;
import dev.customgui.recipe.RequirementSpec;
import dev.customgui.recipe.ResultSpec;
import dev.customgui.recipe.RecipeValidator;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;

public final class ConfigLoader {
    private static final int MAX_MENUS = 512;
    private static final int MAX_RECIPES = 10_000;
    private static final int MAX_ITEMS_PER_MENU = 256;
    private static final int MAX_TEXT_LENGTH = 4_096;

    public ConfigSnapshot load(File dataFolder, long revision) {
        var config = strictLoad(new File(dataFolder, "config.yml"));
        if (config.getInt("config-version", -1) != 1) throw new IllegalArgumentException("config.yml: unsupported config-version (expected 1)");
        var messages = new LinkedHashMap<String, String>();
        var messageSection = config.getConfigurationSection("messages");
        if (messageSection != null) for (String key : messageSection.getKeys(false))
            messages.put(key, boundedText(messageSection.getString(key, key), "message " + key, new File(dataFolder, "config.yml")));
        var menus = new LinkedHashMap<String, MenuDefinition>();
        loadYamlFiles(new File(dataFolder, "menus")).forEach((file, yaml) -> {
            if (menus.size() >= MAX_MENUS) throw new IllegalArgumentException("menu count exceeds " + MAX_MENUS);
            String id = required(yaml, "id", file);
            int rows = yaml.getInt("rows", 6);
            int size = rows * 9;
            Object slots = yaml.get("recipes.slots", yaml.get("content-slots"));
            var contentSlots = slots == null ? List.<Integer>of() : SlotParser.parse(slots, size);
            Object configuredCommands = yaml.contains("open-commands") ? yaml.get("open-commands")
                : yaml.contains("open-command") ? yaml.get("open-command")
                : id.matches("[a-z0-9][a-z0-9_-]{0,31}") ? id : List.of();
            var menu = new MenuDefinition(id, boundedText(yaml.getString("title", id), "title", file), rows,
                permission(yaml.getString("permission", ""), file), commandList(configuredCommands, file),
                contentSlots, lowerList(yaml.getStringList("recipes.groups")), lowerList(yaml.getStringList("recipes.categories")),
                recipeActions(yaml.getConfigurationSection("recipes.click-actions"), file),
                boundedText(yaml.getString("recipes.name", "<yellow>%recipe_id%</yellow>"), "recipe name", file),
                boundedTextList(yaml, "recipes.lore", file),
                yaml.isConfigurationSection("items") ? menuItems(yaml.getConfigurationSection("items"), size, file) : legacyItems(size, yaml.contains("content-slots")));
            if (menus.putIfAbsent(id, menu) != null) throw new IllegalArgumentException("duplicate menu id: " + id);
        });
        var commandOwners = new LinkedHashMap<String, String>();
        for (var menu : menus.values()) for (String command : menu.openCommands()) {
            String previous = commandOwners.putIfAbsent(command, menu.id());
            if (previous != null) throw new IllegalArgumentException("open command " + command + " is used by " + previous + " and " + menu.id());
        }

        var recipes = new LinkedHashMap<String, Recipe>();
        var invalidRecipes = new LinkedHashMap<String, String>();
        loadYamlFiles(new File(dataFolder, "recipes")).forEach((file, yaml) -> {
            var root = yaml.getConfigurationSection("recipes");
            if (root == null) throw new IllegalArgumentException(file + ": recipes section is required");
            if (root.getKeys(false).size() > MAX_RECIPES) throw new IllegalArgumentException(file + ": recipe count exceeds " + MAX_RECIPES);
            for (String id : root.getKeys(false)) try {
                if (recipes.size() >= MAX_RECIPES) throw new IllegalArgumentException("total recipe count exceeds " + MAX_RECIPES);
                var parsed = recipe(id, root.getConfigurationSection(id), file);
                if (recipes.putIfAbsent(id, parsed) != null) invalidRecipes.put(file.getName() + ':' + id, "duplicate recipe id");
            } catch (RuntimeException ex) { invalidRecipes.put(file.getName() + ':' + id, ex.getMessage()); }
        });
        validateMenuLinks(menus, recipes);
        return new ConfigSnapshot(revision, menus, new RecipeRegistry(recipes.values()), messages, invalidRecipes,
            config.getBoolean("security.allow-player-inventory-interaction", false),
            bounded(config.getInt("security.max-batch-size", 256), 1, 4096, "security.max-batch-size"));
    }

    private Recipe recipe(String id, ConfigurationSection section, File file) {
        if (section == null) throw new IllegalArgumentException(file + ": recipe " + id + " must be a section");
        var requirements = list(section, "requirements").stream()
            .map(map -> new RequirementSpec(String.valueOf(map.get("type")), map)).toList();
        var results = list(section, "results").stream()
            .map(map -> new ResultSpec(String.valueOf(map.get("type")), map)).toList();
        return RecipeValidator.validate(new Recipe(id, section.getString("group"), section.getString("category"),
            section.getBoolean("enabled", true), requirements, results));
    }

    private static List<Map<String, Object>> list(ConfigurationSection section, String path) {
        var output = new ArrayList<Map<String, Object>>();
        Object configured = section.get(path);
        if (configured == null) return List.of();
        if (!(configured instanceof List<?> values))
            throw new IllegalArgumentException(section.getCurrentPath() + "." + path + " must be a list");
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw))
                throw new IllegalArgumentException(section.getCurrentPath() + "." + path + " entries must be maps");
            var map = new LinkedHashMap<String, Object>();
            raw.forEach((key, nested) -> map.put(String.valueOf(key), nested));
            output.add(Map.copyOf(map));
        }
        return output;
    }

    private static List<MenuItemDefinition> menuItems(ConfigurationSection root, int size, File file) {
        if (root == null) return List.of();
        var output = new ArrayList<MenuItemDefinition>();
        for (String id : root.getKeys(false)) {
            if (output.size() >= MAX_ITEMS_PER_MENU) throw new IllegalArgumentException(file + ": item count exceeds " + MAX_ITEMS_PER_MENU);
            var item = root.getConfigurationSection(id);
            if (item == null) throw new IllegalArgumentException(file + ": item " + id + " must be a section");
            Object rawSlots = item.get("slots", item.get("slot"));
            if (rawSlots == null) throw new IllegalArgumentException(file + ": item " + id + " requires slot/slots");
            var iconValues = new LinkedHashMap<String, Object>();
            for (String key : List.of("provider", "material", "id", "item-type", "amount"))
                if (item.contains(key)) iconValues.put(key, item.get(key));
            var actions = new LinkedHashMap<String, List<String>>();
            var actionSection = item.getConfigurationSection("actions");
            if (actionSection != null) for (String click : actionSection.getKeys(false))
                actions.put(click, validatedActions(textList(actionSection.get(click)), file, id));
            var lore = boundedTextList(item, "lore", file);
            output.add(new MenuItemDefinition(id, dev.customgui.recipe.ItemSpec.from(iconValues), boundedText(item.getString("name", id), "name", file),
                lore, SlotParser.parse(rawSlots, size), item.getInt("priority", 0),
                permission(item.getString("view-permission", ""), file), stringMap(item.getConfigurationSection("click-permissions")), actions,
                item.getBoolean("glow", false), item.contains("custom-model-data") ? item.getInt("custom-model-data") : null,
                item.getBoolean("hide-tooltip", false), item.getStringList("item-flags")));
        }
        return List.copyOf(output);
    }

    private static List<MenuItemDefinition> legacyItems(int size, boolean legacy) {
        if (!legacy) return List.of();
        return List.of(
            builtin("previous", "ARROW", "<yellow>Trang trước</yellow>", size - 6, "[previous-page]"),
            builtin("close", "BARRIER", "<red>Đóng</red>", size - 5, "[close]"),
            builtin("next", "ARROW", "<yellow>Trang sau</yellow>", size - 4, "[next-page]"));
    }

    private static MenuItemDefinition builtin(String id, String material, String name, int slot, String action) {
        return new MenuItemDefinition(id, new dev.customgui.recipe.ItemSpec("vanilla", material, "", 1), name, List.of(),
            List.of(slot), 0, "", Map.of(), Map.of("click", List.of(action)), false, null, false, List.of());
    }

    private static Map<String, String> recipeActions(ConfigurationSection section, File file) {
        if (section == null) return Map.of("left", "1", "right", "all");
        var output = new LinkedHashMap<String, String>();
        for (String key : section.getKeys(false)) {
            String click = key.toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("click", "left", "right", "shift-left", "shift-right", "middle").contains(click))
                throw new IllegalArgumentException(file + ": unsupported recipe click key " + key);
            String mode = section.getString(key, "").toLowerCase(java.util.Locale.ROOT);
            if (!mode.equals("all")) try { if (Integer.parseInt(mode) < 1) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException(file + ": invalid recipe click amount " + mode); }
            output.put(click, mode);
        }
        return Map.copyOf(output);
    }

    private static Map<String, String> stringMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        var output = new LinkedHashMap<String, String>();
        for (String key : section.getKeys(false)) output.put(key.toLowerCase(java.util.Locale.ROOT), section.getString(key, ""));
        return Map.copyOf(output);
    }

    private static List<String> validatedActions(List<String> actions, File file, String item) {
        for (String action : actions) try {
            if (action.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("action exceeds " + MAX_TEXT_LENGTH + " characters");
            dev.customgui.gui.MenuAction.parse(action);
        }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException(file + ": item " + item + ": " + ex.getMessage()); }
        return actions;
    }

    private static List<String> commandList(Object value, File file) {
        var commands = textList(value).stream().map(command -> command.toLowerCase(java.util.Locale.ROOT)).toList();
        for (String command : commands) if (!command.matches("[a-z0-9][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException(file + ": invalid open command " + command);
        return commands;
    }

    private static List<String> textList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).filter(text -> !text.isBlank()).toList();
        return String.valueOf(value).isBlank() ? List.of() : List.of(String.valueOf(value));
    }

    private static List<String> lowerList(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList();
    }

    private static Map<File, YamlConfiguration> loadYamlFiles(File directory) {
        var output = new LinkedHashMap<File, YamlConfiguration>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return output;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) output.put(file, strictLoad(file));
        return output;
    }

    private static YamlConfiguration strictLoad(File file) {
        if (!file.isFile()) throw new IllegalArgumentException("missing config file: " + file);
        if (file.length() > 2_000_000) throw new IllegalArgumentException(file + ": file exceeds 2 MB");
        var yaml = new YamlConfiguration();
        try { yaml.load(file); return yaml; }
        catch (IOException | InvalidConfigurationException ex) { throw new IllegalArgumentException(file + ": invalid YAML: " + ex.getMessage(), ex); }
    }

    private static String required(YamlConfiguration yaml, String path, File file) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(file + ": " + path + " is required");
        return value;
    }

    private static int bounded(int value, int min, int max, String key) {
        if (value < min || value > max) throw new IllegalArgumentException(key + " must be " + min + ".." + max);
        return value;
    }

    private static String boundedText(String value, String key, File file) {
        if (value == null) return "";
        if (value.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException(file + ": " + key + " exceeds " + MAX_TEXT_LENGTH + " characters");
        try { net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(file + ": invalid MiniMessage in " + key, ex); }
        return value;
    }

    private static List<String> boundedTextList(ConfigurationSection section, String path, File file) {
        if (!section.contains(path)) return List.of();
        Object configured = section.get(path);
        if (!(configured instanceof List<?> values))
            throw new IllegalArgumentException(file + ": " + path + " must be a list");
        return values.stream().map(value -> boundedText(String.valueOf(value), path, file)).toList();
    }

    private static String permission(String value, File file) {
        if (value == null || value.isBlank()) return "";
        if (!value.matches("[A-Za-z0-9_*.-]{1,128}")) throw new IllegalArgumentException(file + ": invalid permission " + value);
        return value;
    }

    private static void validateMenuLinks(Map<String, MenuDefinition> menus, Map<String, Recipe> recipes) {
        for (var menu : menus.values()) for (var item : menu.items()) for (var actions : item.actions().values())
            for (String raw : actions) {
                var action = dev.customgui.gui.MenuAction.parse(raw);
                String target = action.value().split("\\s+", 2)[0];
                if (action.type() == dev.customgui.gui.MenuAction.Type.OPEN_MENU && !menus.containsKey(target))
                    throw new IllegalArgumentException("menu " + menu.id() + " item " + item.id() + " links unknown menu " + target);
                if (action.type() == dev.customgui.gui.MenuAction.Type.RECIPE && !recipes.containsKey(target))
                    throw new IllegalArgumentException("menu " + menu.id() + " item " + item.id() + " links unknown recipe " + target);
            }
    }
}
