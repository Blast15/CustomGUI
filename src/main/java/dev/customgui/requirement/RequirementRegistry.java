package dev.customgui.requirement;

import dev.customgui.recipe.RequirementSpec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RequirementRegistry<C> {
    private final Map<String, RequirementHandler<C>> handlers = new HashMap<>();

    public void register(String type, RequirementHandler<C> handler) {
        if (handlers.putIfAbsent(type.toLowerCase(Locale.ROOT), handler) != null)
            throw new IllegalArgumentException("duplicate requirement handler: " + type);
    }

    public RequirementCheckResult check(C context, RequirementSpec requirement) {
        var handler = handlers.get(requirement.type().toLowerCase(Locale.ROOT));
        return handler == null ? RequirementCheckResult.failure("unsupported-requirement") : handler.check(context, requirement);
    }
}

