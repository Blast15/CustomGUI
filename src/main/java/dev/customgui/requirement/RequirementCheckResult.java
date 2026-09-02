package dev.customgui.requirement;

public record RequirementCheckResult(boolean met, String messageKey) {
    public static RequirementCheckResult success() { return new RequirementCheckResult(true, ""); }
    public static RequirementCheckResult failure(String key) { return new RequirementCheckResult(false, key); }
}

