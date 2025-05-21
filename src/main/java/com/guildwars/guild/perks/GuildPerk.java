package com.guildwars.guild.perks;

public class GuildPerk {
    public final GuildPerkType type;
    public final Object value; // Can be Integer, String, Boolean etc.

    public GuildPerk(GuildPerkType type, Object value) {
        this.type = type;
        this.value = value;
    }

    // Helper to get value as specific type to avoid casting everywhere
    public int getIntValue() {
        if (value instanceof Number) { // Handle various number types
            return ((Number) value).intValue();
        }
        return 0; // Default or throw error
    }
    public String getStringValue() {
        return (value instanceof String) ? (String) value : null;
    }
    public boolean getBooleanValue() { 
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false; // Default for non-boolean or null value
    }
}
