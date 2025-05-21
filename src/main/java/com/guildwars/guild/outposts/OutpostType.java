package com.guildwars.guild.outposts;

public enum OutpostType {
    XP_SIPHON("XP Siphon", "Generates Guild XP over time."),
    BARRACKS("Barracks", "Passively generates a small amount of Guild XP."),
    RESOURCE_SILO("Resource Silo", "Foundation for future resource generation modules.");
    // Add more outpost types here later (e.g., ARMORY)

    private final String displayName;
    private final String description;

    OutpostType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static OutpostType fromString(String text) {
        for (OutpostType type : OutpostType.values()) {
            if (type.name().equalsIgnoreCase(text) || type.getDisplayName().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }
}
