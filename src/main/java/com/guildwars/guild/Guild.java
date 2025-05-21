package com.guildwars.guild;

import com.guildwars.guild.outposts.OutpostType; // Added for Outposts
import org.bukkit.inventory.ItemStack; // Added for Guild Bank
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // Added for claimedChunks

public class Guild {

    private String name;
    private UUID leader;
    private List<UUID> members;
    private List<UUID> officers;
    private int level;
    private long currentXp;

    // Guild Home location fields
    private String homeWorldName;
    private Double homeX, homeY, homeZ;
    private Float homeYaw, homePitch;
    // Transient Bukkit Location object for convenience
    private transient org.bukkit.Location guildHomeLocation;
    private Set<String> claimedChunks; // Format: "worldName:x:z"
    private ItemStack[] bankContents; // Added for Guild Bank
    public static final int BANK_SIZE = 54; // Standard double chest size

    // Simple record-like class to store precise outpost location
    public record LocationData(String worldName, int x, int y, int z) {}
    // Wrapper for outpost info including next tick time
    public record ActiveOutpostInfo(LocationData location, long nextTickTimestamp) {}

    // Outpost Information (Stores precise location of the outpost's core block and next tick time)
    private Map<OutpostType, ActiveOutpostInfo> activeOutposts; 

    public Guild(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members = new ArrayList<>();
        this.officers = new ArrayList<>();
        this.members.add(leader);
        this.officers.add(leader);
        this.level = 1;
        this.currentXp = 0;
        this.claimedChunks = ConcurrentHashMap.newKeySet(); // Initialize
        this.bankContents = new ItemStack[BANK_SIZE]; // Initialize bank
        this.activeOutposts = new HashMap<>(); // Initialize outposts map
    }

    // Constructor for loading from DB
    public Guild(String name, UUID leader, int level, long currentXp) {
        this.name = name;
        this.leader = leader;
        this.members = new ArrayList<>(); // Members will be populated separately
        this.officers = new ArrayList<>(); // Officers will be populated separately
        // Leader is added to members/officers by default when members are populated, or by GuildManager explicitly.
        this.level = level;
        this.currentXp = currentXp;
        // home will be set separately by DB loader
        this.claimedChunks = ConcurrentHashMap.newKeySet(); // Initialize
        this.bankContents = new ItemStack[BANK_SIZE]; // Initialize bank for loaded guild
        this.activeOutposts = new HashMap<>(); // Initialize outposts map
    }

    // Getters
    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public List<UUID> getMembers() {
        return members;
    }

    public List<UUID> getOfficers() {
        return officers;
    }

    // Chat Prefix
    public String getChatPrefix() {
        return "[" + this.name + "]";
    }

    // Member management methods

    public boolean addMember(UUID playerUuid) {
        if (!members.contains(playerUuid)) {
            members.add(playerUuid);
            return true; // Successfully added
        }
        return false; // Already a member
    }

    public boolean removeMember(UUID playerUuid) {
        if (playerUuid.equals(leader)) {
            return false; // Cannot remove the leader
        }
        boolean wasMember = members.remove(playerUuid);
        boolean wasOfficer = officers.remove(playerUuid); // Also remove from officers if they were one
        return wasMember; // Return true if they were a member and removed
    }

    public boolean promoteOfficer(UUID playerUuid) {
        if (playerUuid.equals(this.leader)) return false; // Cannot promote the leader (they are already effectively above officer)
        if (members.contains(playerUuid) && !officers.contains(playerUuid)) {
            officers.add(playerUuid);
            return true; // Successfully promoted
        }
        return false; // Not a member or already an officer
    }

    public boolean demoteOfficer(UUID playerUuid) {
        if (playerUuid.equals(leader)) {
            return false; // Cannot demote the leader from officer status
        }
        return officers.remove(playerUuid); // Returns true if they were an officer and removed
    }

    public boolean isMember(UUID playerUuid) {
        return members.contains(playerUuid);
    }

    public boolean isOfficer(UUID playerUuid) {
        return officers.contains(playerUuid);
    }

    // Method to set a new leader
    public void setLeader(UUID newLeaderUuid) {
        if (newLeaderUuid == null || !members.contains(newLeaderUuid)) {
            // Invalid new leader or not a member, do nothing or throw exception
            // For now, let's assume the GuildCommand will validate this before calling.
            return; 
        }
        
        // Ensure the old leader is at least an officer if they are not the new leader
        if (!this.leader.equals(newLeaderUuid) && !officers.contains(this.leader)) {
            officers.add(this.leader);
        }

        this.leader = newLeaderUuid;

        // Ensure the new leader is also an officer
        if (!officers.contains(newLeaderUuid)) {
            officers.add(newLeaderUuid);
        }
    }

    // XP and Leveling
    public int getLevel() {
        return level;
    }

    public long getCurrentXp() {
        return currentXp;
    }

    public void setLevel(int level) { // Mainly for DB loading
        this.level = level;
    }

    public void setCurrentXp(long currentXp) { // Mainly for DB loading
        this.currentXp = currentXp;
    }

    public long getXpForNextLevel() {
        // Example formula: 500 * level^2. Adjust as desired.
        // Using Math.pow returns double, ensure casting or use integer math for safety with large numbers if needed.
        return 500L * (long)this.level * (long)this.level; 
    }

    /**
     * Adds XP to the guild and handles leveling up.
     * @param amount The amount of XP to add.
     * @return True if the guild leveled up, false otherwise.
     */
    public boolean addXp(long amount) {
        if (amount <= 0) return false;

        this.currentXp += amount;
        boolean leveledUp = false;
        long xpForNext = getXpForNextLevel();

        while (this.currentXp >= xpForNext && xpForNext > 0) { // xpForNext > 0 to prevent infinite loop if formula is bad or max level
            this.currentXp -= xpForNext;
            this.level++;
            leveledUp = true;
            // GuildWarsPlugin.getInstance().getLogger().info("Guild " + this.name + " leveled up to " + this.level + "!"); // Example log
            // Announce to guild members - this should be handled by GuildManager or Command after this method returns true.
            xpForNext = getXpForNextLevel(); // Recalculate for next potential level up
            if (this.level >= 200) { // Example Max Level, xpForNextLevel formula might yield 0 or negative for high levels
                 this.currentXp = 0; // Cap XP at max level or set to just under next (non-existent) level
                 break; // Stop leveling if max level reached
            }
        }
        return leveledUp;
    }

    /**
     * Attempts to pay a specified amount of XP for upkeep.
     * Does not handle de-leveling; it's a direct XP deduction if possible.
     * @param xpAmount The amount of XP to pay.
     * @return True if XP was successfully paid, false otherwise.
     */
    public boolean payXpUpkeep(long xpAmount) {
        if (xpAmount < 0) return true; // No cost or negative cost means payment is successful
        if (this.currentXp >= xpAmount) {
            this.currentXp -= xpAmount;
            return true;
        }
        return false; // Not enough XP
    }

    // --- Guild Home Methods ---
    public org.bukkit.Location getGuildHomeLocation() {
        if (this.guildHomeLocation == null && this.homeWorldName != null && this.homeX != null && this.homeY != null && this.homeZ != null) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(this.homeWorldName);
            if (world != null) {
                this.guildHomeLocation = new org.bukkit.Location(world, this.homeX, this.homeY, this.homeZ, 
                                                        (this.homeYaw != null ? this.homeYaw : 0.0f), 
                                                        (this.homePitch != null ? this.homePitch : 0.0f));
            }
        }
        return this.guildHomeLocation;
    }

    public void setGuildHomeLocation(org.bukkit.Location location) {
        if (location == null) {
            this.homeWorldName = null;
            this.homeX = null;
            this.homeY = null;
            this.homeZ = null;
            this.homeYaw = null;
            this.homePitch = null;
            this.guildHomeLocation = null;
        } else {
            this.homeWorldName = location.getWorld().getName();
            this.homeX = location.getX();
            this.homeY = location.getY();
            this.homeZ = location.getZ();
            this.homeYaw = location.getYaw();
            this.homePitch = location.getPitch();
            this.guildHomeLocation = location;
        }
    }
    
    // Direct getters for raw home data (primarily for DB saving)
    public String getHomeWorldName() { return homeWorldName; }
    public Double getHomeX() { return homeX; }
    public Double getHomeY() { return homeY; }
    public Double getHomeZ() { return homeZ; }
    public Float getHomeYaw() { return homeYaw; }
    public Float getHomePitch() { return homePitch; }

    // Setters for DB loading (used by DatabaseManager)
    public void setRawHomeLocation(String worldName, Double x, Double y, Double z, Float yaw, Float pitch) {
        this.homeWorldName = worldName;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeYaw = yaw;
        this.homePitch = pitch;
        this.guildHomeLocation = null; // Force re-creation on next getGuildHomeLocation()
    }

    // --- Claimed Chunks Methods ---
    public Set<String> getClaimedChunks() {
        return Collections.unmodifiableSet(this.claimedChunks); // Return unmodifiable view
    }

    public void addClaimedChunk(String worldName, int chunkX, int chunkZ) {
        this.claimedChunks.add(worldName + ":" + chunkX + ":" + chunkZ);
    }
    
    // Used by DatabaseManager during loading
    public void loadClaimedChunk(String chunkId) {
        this.claimedChunks.add(chunkId);
    }

    public void removeClaimedChunk(String worldName, int chunkX, int chunkZ) {
        this.claimedChunks.remove(worldName + ":" + chunkX + ":" + chunkZ);
    }

    public boolean hasClaimedChunk(String worldName, int chunkX, int chunkZ) {
        return this.claimedChunks.contains(worldName + ":" + chunkX + ":" + chunkZ);
    }

    public void clearAllClaims() {
        this.claimedChunks.clear();
    }

    // --- Guild Bank Methods ---
    public ItemStack[] getBankContents() {
        return this.bankContents;
    }

    public void setBankContents(ItemStack[] bankContents) {
        if (bankContents != null && bankContents.length == BANK_SIZE) {
            this.bankContents = bankContents;
        } else {
            // Or initialize to empty if invalid size, or log error
            this.bankContents = new ItemStack[BANK_SIZE]; 
        }
    }

    public ItemStack getBankItem(int slot) {
        if (slot >= 0 && slot < BANK_SIZE) {
            return bankContents[slot];
        }
        return null;
    }

    public void setBankItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < BANK_SIZE) {
            bankContents[slot] = item;
        }
    }

    // --- Guild Outpost Methods (Phase 1) ---
    public boolean hasOutpost(OutpostType type) {
        return activeOutposts.containsKey(type);
    }

    public ActiveOutpostInfo getActiveOutpostInfo(OutpostType type) {
        return activeOutposts.get(type);
    }
    
    // Convenience getter for just location if needed, though getActiveOutpostInfo().location() is preferred
    public LocationData getOutpostLocationData(OutpostType type) {
        ActiveOutpostInfo info = activeOutposts.get(type);
        return (info != null) ? info.location() : null;
    }

    public Map<OutpostType, ActiveOutpostInfo> getAllActiveOutposts() {
        return Collections.unmodifiableMap(activeOutposts);
    }

    public void addOutpost(OutpostType type, LocationData locationData, long initialNextTickTimestamp) {
        activeOutposts.put(type, new ActiveOutpostInfo(locationData, initialNextTickTimestamp));
    }

    public void updateOutpostNextTick(OutpostType type, long newNextTickTimestamp) {
        ActiveOutpostInfo currentInfo = activeOutposts.get(type);
        if (currentInfo != null) {
            activeOutposts.put(type, new ActiveOutpostInfo(currentInfo.location(), newNextTickTimestamp));
        }
    }

    public void removeOutpost(OutpostType type) {
        activeOutposts.remove(type);
    }
}
