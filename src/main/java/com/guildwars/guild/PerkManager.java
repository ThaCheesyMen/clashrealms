package com.guildwars.guild; 

import com.guildwars.guild.perks.GuildPerk;
import com.guildwars.guild.perks.GuildPerkType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PerkManager {

    private final Map<Integer, List<GuildPerk>> perksByLevel = new HashMap<>();

    public PerkManager() {
        // Initialize hardcoded perks - later load from config
        loadDefaultPerks();
    }

    private void loadDefaultPerks() {
        // Level 2 Perks
        List<GuildPerk> level2Perks = new ArrayList<>();
        level2Perks.add(new GuildPerk(GuildPerkType.MAX_MEMBERS_INCREASE, 5));
        perksByLevel.put(2, level2Perks);

        // Level 3 Perks
        List<GuildPerk> level3Perks = new ArrayList<>();
        level3Perks.add(new GuildPerk(GuildPerkType.ALLOW_GUILD_SETHOME, true)); 
        perksByLevel.put(3, level3Perks);
        
        // Level 5 Perks
        List<GuildPerk> level5Perks = new ArrayList<>();
        level5Perks.add(new GuildPerk(GuildPerkType.MAX_MEMBERS_INCREASE, 5)); // Another +5 (total +10 from base)
        perksByLevel.put(5, level5Perks);

        // Level 4 Perks (Example for claims)
        List<GuildPerk> level4Perks = new ArrayList<>();
        level4Perks.add(new GuildPerk(GuildPerkType.MAX_CLAIMED_CHUNKS_INCREASE, 3));
        perksByLevel.put(4, level4Perks);

        // Level 7 Perks
        // List<GuildPerk> level7Perks = new ArrayList<>();
        // level7Perks.add(new GuildPerk(GuildPerkType.GUILD_HOME_PARTICLE, "HEART"));
        // perksByLevel.put(7, level7Perks);

        // Level 10 Perks
        // List<GuildPerk> level10Perks = new ArrayList<>();
        // level10Perks.add(new GuildPerk(GuildPerkType.PASSIVE_HASTE_AURA, 0)); // Haste I (amplifier 0)
        // perksByLevel.put(10, level10Perks);

        // Level 8 Perks (Example for claims)
        List<GuildPerk> level8Perks = new ArrayList<>();
        level8Perks.add(new GuildPerk(GuildPerkType.MAX_CLAIMED_CHUNKS_INCREASE, 5)); // Total +8 over base (3 from L4 + 5 from L8)
        perksByLevel.put(8, level8Perks);

        // System.out.println("PerkManager: Loaded " + perksByLevel.size() + " perk levels.");
    }

    public List<GuildPerk> getUnlockedPerks(int guildLevel) {
        List<GuildPerk> unlocked = new ArrayList<>();
        for (int i = 1; i <= guildLevel; i++) {
            if (perksByLevel.containsKey(i)) {
                unlocked.addAll(perksByLevel.get(i));
            }
        }
        return unlocked;
    }

    public int getAccumulatedIntValue(int guildLevel, GuildPerkType perkType) {
        return getUnlockedPerks(guildLevel).stream()
                .filter(perk -> perk.type == perkType && perk.value instanceof Number)
                .mapToInt(GuildPerk::getIntValue)
                .sum();
    }

    public boolean hasPerk(int guildLevel, GuildPerkType perkType) {
        return getUnlockedPerks(guildLevel).stream()
                .anyMatch(perk -> perk.type == perkType);
    }
    
    public String getStringPerkValue(int guildLevel, GuildPerkType perkType) {
         return getUnlockedPerks(guildLevel).stream()
                .filter(perk -> perk.type == perkType && perk.value instanceof String)
                .map(GuildPerk::getStringValue)
                .reduce((first, second) -> second) 
                .orElse(null);
    }

     public int getIntPerkValue(int guildLevel, GuildPerkType perkType) {
        return getUnlockedPerks(guildLevel).stream()
                .filter(perk -> perk.type == perkType && perk.value instanceof Number)
                .mapToInt(GuildPerk::getIntValue)
                .reduce((first, second) -> second) 
                .orElse(0); 
    }

    /**
     * Finds the lowest level at which a specific perk type is first defined/unlocked.
     * @param perkType The type of perk to find.
     * @return The level, or -1 if the perk is not found in the configuration.
     */
    public int getLevelRequiredForPerk(GuildPerkType perkType) {
        return perksByLevel.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(perk -> perk.type == perkType))
            .mapToInt(Map.Entry::getKey)
            .min()
            .orElse(-1);
    }
}
