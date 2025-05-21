package com.guildwars.guild.perks;

public enum GuildPerkType {
    MAX_MEMBERS_INCREASE,    // Value: integer amount to increase by
    ALLOW_GUILD_SETHOME,     // Value: typically not needed, presence implies true
    GUILD_HOME_PARTICLE,     // Value: String identifier for particle effect?
    PASSIVE_HASTE_AURA,      // Value: Potion effect level (e.g., 0 for Haste I)
    MAX_CLAIMED_CHUNKS_INCREASE // Value: integer amount to increase by
    // Add more as needed
}
