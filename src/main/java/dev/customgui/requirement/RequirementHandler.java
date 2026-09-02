package dev.customgui.requirement;

import dev.customgui.recipe.RequirementSpec;

@FunctionalInterface
public interface RequirementHandler<C> {
    RequirementCheckResult check(C context, RequirementSpec requirement);
}

