package dev.customgui.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import dev.customgui.config.ConfigSnapshot;
import dev.customgui.config.MenuDefinition;
import dev.customgui.recipe.RecipeRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.junit.jupiter.api.Test;

class MenuCommandRegistryTest {
    @Test void failedReplacementRestoresOldRegistryWithoutPartialCommands() {
        Server server = mock(Server.class);
        CommandMap map = mock(CommandMap.class);
        Map<String, Command> known = new LinkedHashMap<>();
        var registrations = new AtomicInteger();
        when(server.getCommandMap()).thenReturn(map);
        when(map.getKnownCommands()).thenReturn(known);
        when(map.getCommand(anyString())).thenAnswer(call -> known.get(call.getArgument(0, String.class)));
        when(map.register(eq("customgui"), any(Command.class))).thenAnswer(call -> {
            Command command = call.getArgument(1);
            if (registrations.incrementAndGet() == 3) return false;
            known.put(command.getName(), command);
            known.put("customgui:" + command.getName(), command);
            return true;
        });

        var registry = new MenuCommandRegistry(server, () -> snapshot("old"), null);
        registry.commit(registry.plan(snapshot("old")));
        assertThrows(IllegalStateException.class, () -> registry.commit(registry.plan(snapshot("new-a", "new-b"))));

        assertTrue(known.containsKey("old"));
        assertFalse(known.containsKey("new-a"));
        assertFalse(known.containsKey("new-b"));
    }

    private static ConfigSnapshot snapshot(String... commands) {
        var menu = new MenuDefinition("menu", "Menu", 1, "", List.of(commands), List.of(), List.of(), List.of(),
            Map.of(), "", List.of(), List.of());
        return new ConfigSnapshot(1, Map.of("menu", menu), new RecipeRegistry(List.of()), Map.of(), Map.of(), false, 256);
    }
}
