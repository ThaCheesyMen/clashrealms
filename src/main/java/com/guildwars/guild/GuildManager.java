package com.guildwars.guild;

import com.guildwars.GuildWarsPlugin;
import com.guildwars.guild.outposts.OutpostType;
import com.guildwars.guild.perks.GuildPerkType;
import com.guildwars.services.HologramManager;
import com.guildwars.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GuildManager {

    private final GuildWarsPlugin plugin;
    private final DatabaseManager dbManager;
    private final PerkManager perkManager;
    private final HologramManager hologramManager;
    private final Map<String, Guild> guilds;
    private final Map<UUID, String> pendingInvites;
    private Map<String, List<String>> activeWars;

    private final long upkeepXpPerChunk;
    private final long xpSiphonCreationCost, xpSiphonXpGeneration, xpSiphonIntervalMillis;
    private final long barracksCreationCost, barracksXpGeneration, barracksIntervalMillis;
    private final long resourceSiloCreationCost, resourceSiloIntervalMillis;
    private final int resourceSiloGenerationChance;

    private static final int BASE_MAX_MEMBERS = 10;
    public static final int BASE_MAX_CLAIMS = 5;
    private final Random random = new Random();

    public static final int WAR_POINTS_PER_KILL = 1;
    public static final int WAR_POINTS_PER_CHUNK_CONTEST = 5;

    public GuildManager(GuildWarsPlugin plugin, DatabaseManager dbManager, PerkManager perkManager,
                        long upkeepXpPerChunk, long xpSiphonCreationCost, long xpSiphonXpGeneration, long xpSiphonIntervalHours,
                        long barracksCreationCost, long barracksXpGeneration, long barracksIntervalHours,
                        long resourceSiloCreationCost, long resourceSiloIntervalHours, int resourceSiloGenerationChance) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.perkManager = perkManager;
        this.upkeepXpPerChunk = upkeepXpPerChunk;
        this.xpSiphonCreationCost = xpSiphonCreationCost;
        this.xpSiphonXpGeneration = xpSiphonXpGeneration;
        this.xpSiphonIntervalMillis = TimeUnit.HOURS.toMillis(xpSiphonIntervalHours);
        this.barracksCreationCost = barracksCreationCost;
        this.barracksXpGeneration = barracksXpGeneration;
        this.barracksIntervalMillis = TimeUnit.HOURS.toMillis(barracksIntervalHours);
        this.resourceSiloCreationCost = resourceSiloCreationCost;
        this.resourceSiloIntervalMillis = TimeUnit.HOURS.toMillis(resourceSiloIntervalHours);
        this.resourceSiloGenerationChance = resourceSiloGenerationChance;

        this.guilds = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();
        this.activeWars = new ConcurrentHashMap<>();
        this.hologramManager = new HologramManager(plugin); // HologramManager is initialized here

        loadAllData();
        // reloadAllOutpostHolograms(); // This is now called by GuildWarsPlugin after HologramManager.initializeAfterServerLoad()
    }

    private void loadAllData() {
        if (this.dbManager == null) {
            System.err.println("DatabaseManager is null. Cannot load data."); return;
        }
        Map<String, Guild> loadedGuilds = dbManager.loadAllGuilds();
        dbManager.loadGuildMembersAndOfficers(loadedGuilds);
        for (Guild guild : loadedGuilds.values()) {
            dbManager.loadGuildClaims(guild);
        }
        this.guilds.putAll(loadedGuilds);
        this.pendingInvites.putAll(dbManager.loadPendingInvites());
        this.activeWars.putAll(dbManager.loadActiveWars());
        plugin.getLogger().info("Loaded " + this.activeWars.size() + " active war relationships.");
    }
    
    // Called by GuildWarsPlugin after HologramManager's own late init
    public void reloadAllOutpostHolograms() {
        if (!hologramManager.isEnabled()) {
            plugin.getLogger().info("HologramManager not fully enabled; skipping hologram reload.");
            return;
        }
        plugin.getLogger().info("Reloading outpost holograms...");
        for (Guild guild : guilds.values()) {
            for (Map.Entry<OutpostType, Guild.ActiveOutpostInfo> entry : guild.getAllActiveOutposts().entrySet()) {
                OutpostType type = entry.getKey();
                Guild.ActiveOutpostInfo outpostInfo = entry.getValue();
                Guild.LocationData locData = outpostInfo.location();
                World world = Bukkit.getWorld(locData.worldName());
                if (world != null) {
                    Location outpostCoreLocation = new Location(world, locData.x(), locData.y(), locData.z());
                    double yOffset = (type == OutpostType.XP_SIPHON) ? 3.0 : 2.7;
                    Location hologramDisplayLocation = outpostCoreLocation.clone().add(0.5, yOffset, 0.5);
                    hologramManager.createOutpostHologram(hologramDisplayLocation, guild, type, outpostInfo.nextTickTimestamp());
                } else {
                    plugin.getLogger().warning("Could not reload hologram for " + guild.getName() + "'s " + type.getDisplayName() + ": World '" + locData.worldName() + "' not found.");
                }
            }
        }
        plugin.getLogger().info("Finished reloading outpost holograms.");
    }
    
    // --- Outpost Structure Generation/Removal Helpers ---
    private Location getOutpostCoreLocation(Guild guild, OutpostType type) {
        Guild.ActiveOutpostInfo outpostInfo = guild.getActiveOutpostInfo(type);
        if (outpostInfo == null) return null;
        Guild.LocationData locData = outpostInfo.location();
        World world = Bukkit.getWorld(locData.worldName());
        if (world == null) return null;
        return new Location(world, locData.x(), locData.y(), locData.z());
    }

    private Location generateOutpostStructure(Location playerLocation, OutpostType type) {
        World world = playerLocation.getWorld();
        if (world == null) return null;
        int coreX = playerLocation.getBlockX();
        int coreZ = playerLocation.getBlockZ();
        int coreY = world.getHighestBlockYAt(coreX, coreZ);
        Block highestBlock = world.getBlockAt(coreX, coreY, coreZ);
        if (highestBlock.getType().isAir() && coreY > world.getMinHeight()) {
            coreY--;
        }
        if (world.getBlockAt(coreX, coreY, coreZ).getType().isAir() || coreY < world.getMinHeight() + 5) {
            coreY = Math.max(playerLocation.getBlockY() - 1, world.getMinHeight() + 1);
            coreY = Math.min(coreY, world.getMaxHeight() - 10); 
        }
        Material platformMaterial = Material.STONE_BRICKS;
        Material centralBlockMaterial = Material.AIR;
        Location centralBlockEffectiveLocation = new Location(world, coreX, coreY + 1, coreZ); 

        switch (type) {
            case XP_SIPHON:
                centralBlockMaterial = Material.BEACON;
                platformMaterial = Material.LAPIS_BLOCK;
                break;
            case BARRACKS:
                centralBlockMaterial = Material.SMOOTH_STONE_SLAB;
                platformMaterial = Material.COBBLESTONE;
                centralBlockEffectiveLocation = new Location(world, coreX, coreY, coreZ); 
                break;
            case RESOURCE_SILO:
                centralBlockMaterial = Material.CHEST;
                platformMaterial = Material.OAK_PLANKS;
                break;
            default: return null;
        }
        int platformBaseY = coreY;
        if (type == OutpostType.BARRACKS) { 
            platformBaseY = centralBlockEffectiveLocation.getBlockY();
        } else { 
            platformBaseY = coreY; 
        }
        if (type == OutpostType.XP_SIPHON || type == OutpostType.RESOURCE_SILO) {
             platformBaseY = coreY; 
        } else if (type == OutpostType.BARRACKS) {
             platformBaseY = coreY; 
             centralBlockEffectiveLocation = new Location(world, coreX, coreY, coreZ); 
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(coreX + dx, platformBaseY, coreZ + dz).setType(platformMaterial);
            }
        }
        centralBlockEffectiveLocation.getBlock().setType(centralBlockMaterial);
        return centralBlockEffectiveLocation;
    }

    private void removeOutpostStructure(Location centralBlockLocation, OutpostType type) {
        if (centralBlockLocation == null) return;
        World world = centralBlockLocation.getWorld();
        if (world == null) return;
        int coreX = centralBlockLocation.getBlockX();
        int coreY = centralBlockLocation.getBlockY(); 
        int coreZ = centralBlockLocation.getBlockZ();
        world.getBlockAt(coreX, coreY, coreZ).setType(Material.AIR); 
        int platformY = coreY; 
        if (type == OutpostType.XP_SIPHON || type == OutpostType.RESOURCE_SILO) {
            platformY = coreY - 1; 
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block platformBlock = world.getBlockAt(coreX + dx, platformY, coreZ + dz);
                if ((type == OutpostType.XP_SIPHON && platformBlock.getType() == Material.LAPIS_BLOCK) ||
                    (type == OutpostType.BARRACKS && platformBlock.getType() == Material.COBBLESTONE) || 
                    (type == OutpostType.RESOURCE_SILO && platformBlock.getType() == Material.OAK_PLANKS) ||
                     platformBlock.getType() == Material.STONE_BRICKS) { 
                    platformBlock.setType(Material.AIR);
                }
            }
        }
    }

    // --- Standard Guild Getters ---
    public Guild getGuild(String name) { return guilds.get(name); }
    public Guild getGuildByPlayer(UUID playerUuid) { for (Guild guild : guilds.values()) { if (guild.isMember(playerUuid)) return guild; } return null; }
    public Guild getGuildByLeader(UUID leaderUuid) { for (Guild guild : guilds.values()) { if (guild.getLeader().equals(leaderUuid)) return guild; } return null; }
    public Map<String, Guild> getAllGuilds() { return Collections.unmodifiableMap(guilds); }
    public Guild getGuildByNameSanitized(String sanitizedName) {
        if (sanitizedName == null || sanitizedName.isEmpty()) return null;
        for (Guild guild : guilds.values()) {
            String currentSanitized = guild.getName().toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (currentSanitized.isEmpty() && sanitizedName.equals("invalidguild")) return guild;
            if (currentSanitized.equals(sanitizedName)) return guild;
        }
        return null;
    }

    // --- Guild Creation / Deletion ---
    public Guild createGuild(String name, UUID leader) { 
        if (guilds.containsKey(name)) return null;
        if (getGuildByPlayer(leader) != null) return null;
        Guild newGuild = new Guild(name, leader);
        guilds.put(name, newGuild);
        if (dbManager != null) dbManager.saveNewGuild(newGuild);
        return newGuild;
    }
    public boolean deleteGuild(String name) { 
        Guild guild = guilds.get(name);
        if (guild != null) {
            if (hologramManager.isEnabled()) {
                hologramManager.deleteAllOutpostHologramsForGuild(guild);
            }
            for (Map.Entry<OutpostType, Guild.ActiveOutpostInfo> entry : new HashMap<>(guild.getAllActiveOutposts()).entrySet()) {
                Guild.LocationData locData = entry.getValue().location();
                World world = Bukkit.getWorld(locData.worldName());
                if (world != null) {
                    Location loc = new Location(world, locData.x(), locData.y(), locData.z());
                    removeOutpostStructure(loc, entry.getKey());
                }
            }
            guild.clearAllClaims();
            guilds.remove(name);
            if (dbManager != null) dbManager.deleteGuildDB(name);
            return true;
        }
        return false;
    }
    
    // --- Member Management --- 
    public boolean addPlayerToGuild(String guildName, UUID playerUuid) { 
        Guild guild = getGuild(guildName);
        if (guild != null && getGuildByPlayer(playerUuid) == null) {
            if (guild.getMembers().size() >= getEffectiveMaxMembers(guild)) return false; 
            boolean added = guild.addMember(playerUuid);
            if (added && dbManager != null) dbManager.addGuildMemberDB(guildName, playerUuid, false);
            return added;
        }
        return false;
    }
    public boolean removePlayerFromGuild(String guildName, UUID playerUuid) { 
        Guild guild = getGuild(guildName);
        if (guild != null) {
            boolean removed = guild.removeMember(playerUuid);
            if (removed && dbManager != null) {
                dbManager.removeGuildMemberDB(guildName, playerUuid);
                if (guild.getMembers().isEmpty() && !guild.getLeader().equals(playerUuid)) { 
                     deleteGuild(guildName);
                }
            }
            return removed;
        }
        return false;
    }
    public boolean promotePlayerInGuild(String guildName, UUID playerUuid) { 
        Guild guild = getGuild(guildName);
        if (guild != null && guild.isMember(playerUuid) && !guild.isOfficer(playerUuid) && !guild.getLeader().equals(playerUuid)) {
            boolean promoted = guild.promoteOfficer(playerUuid);
            if (promoted && dbManager != null) dbManager.updateGuildMemberRoleDB(guildName, playerUuid, true);
            return promoted;
        }
        return false;
    }
    public boolean demotePlayerInGuild(String guildName, UUID playerUuid) { 
        Guild guild = getGuild(guildName);
        if (guild != null && guild.isOfficer(playerUuid) && !guild.getLeader().equals(playerUuid)) {
            boolean demoted = guild.demoteOfficer(playerUuid);
            if (demoted && dbManager != null) dbManager.updateGuildMemberRoleDB(guildName, playerUuid, false);
            return demoted;
        }
        return false;
    }

    // --- Invite Management --- 
    public void addInvite(UUID invitedPlayerUuid, String guildName) { 
        pendingInvites.put(invitedPlayerUuid, guildName);
        if (dbManager != null) dbManager.saveInviteDB(invitedPlayerUuid, guildName);
    }
    public String getInvitationGuildName(UUID invitedPlayerUuid) { 
        return pendingInvites.get(invitedPlayerUuid);
    }
    public boolean hasInvite(UUID invitedPlayerUuid, String guildName) { 
        String invitingGuild = pendingInvites.get(invitedPlayerUuid);
        return guildName != null && guildName.equals(invitingGuild);
    }
    public void removeInvite(UUID invitedPlayerUuid) { 
        pendingInvites.remove(invitedPlayerUuid);
        if (dbManager != null) dbManager.removeInviteDB(invitedPlayerUuid);
    }

    // --- XP & Level --- 
    public void updateGuildLevelAndXp(Guild guild) { 
        if (dbManager != null && guild != null) {
            dbManager.updateGuildLevelAndXp(guild.getName(), guild.getLevel(), guild.getCurrentXp());
        }
    }
    public long contributeItems(Player player, Guild guild, List<ItemStack> items) { 
        if (player == null || guild == null || items == null || items.isEmpty()) return 0;
        long totalXpAwarded = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                totalXpAwarded += (10L * item.getAmount()); 
            }
        }
        if (totalXpAwarded > 0) {
            boolean leveledUp = guild.addXp(totalXpAwarded);
            updateGuildLevelAndXp(guild);
            player.sendMessage(ChatColor.GREEN + "You contributed items and earned " + ChatColor.AQUA + totalXpAwarded + ChatColor.GREEN + " XP for your guild!");
            if (leveledUp) {
                String levelUpMessage = ChatColor.GOLD + "Your guild, " + ChatColor.AQUA + guild.getName() + ChatColor.GOLD + ", has leveled up to level " + ChatColor.YELLOW + guild.getLevel() + ChatColor.GOLD + "!";
                player.sendMessage(levelUpMessage);
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (guild.isMember(onlinePlayer.getUniqueId()) && !onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                        onlinePlayer.sendMessage(levelUpMessage);
                    }
                }
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "No XP was awarded for the contribution.");
        }
        return totalXpAwarded;
    }

    // --- Guild Home --- 
    public void setGuildHome(Guild guild, Location location) { 
        if (guild == null) return;
        guild.setGuildHomeLocation(location);
        if (dbManager != null) dbManager.updateGuildHomeDB(guild.getName(), location);
    }

    // --- Claim Management --- 
    public static String getChunkId(World world, int chunkX, int chunkZ) { 
        return world.getName() + ":" + chunkX + ":" + chunkZ;
    }
    public boolean claimChunk(Guild guild, World world, int chunkX, int chunkZ) { 
        if (guild == null || world == null) return false;
        if (getGuildOwningChunk(world, chunkX, chunkZ) != null) return false;
        int bonusClaims = perkManager.getAccumulatedIntValue(guild.getLevel(), GuildPerkType.MAX_CLAIMED_CHUNKS_INCREASE);
        int maxClaims = BASE_MAX_CLAIMS + bonusClaims;
        if (guild.getClaimedChunks().size() >= maxClaims) return false;
        guild.addClaimedChunk(world.getName(), chunkX, chunkZ);
        if (dbManager != null) dbManager.saveGuildClaim(guild.getName(), world.getName(), chunkX, chunkZ);
        return true;
    }
    public boolean unclaimChunk(Guild guild, World world, int chunkX, int chunkZ) { 
        if (guild == null || world == null) return false;
        if (!guild.hasClaimedChunk(world.getName(), chunkX, chunkZ)) return false;
        guild.removeClaimedChunk(world.getName(), chunkX, chunkZ);
        if (dbManager != null) dbManager.removeGuildClaim(world.getName(), chunkX, chunkZ);
        return true;
    }
    public Guild getGuildOwningChunk(World world, int chunkX, int chunkZ) { 
        if (world == null) return null;
        String targetChunkId = getChunkId(world, chunkX, chunkZ);
        for (Guild g : guilds.values()) {
            if (g.hasClaimedChunk(world.getName(), chunkX, chunkZ)) return g;
        }
        return null;
    }
    
    // --- Perk Related Getters --- 
    public int getEffectiveMaxMembers(Guild guild) { 
        if (guild == null || perkManager == null) return BASE_MAX_MEMBERS;
        int bonus = perkManager.getAccumulatedIntValue(guild.getLevel(), GuildPerkType.MAX_MEMBERS_INCREASE);
        return BASE_MAX_MEMBERS + bonus;
    }
    public boolean canGuildSetHome(Guild guild) { 
        if (guild == null || perkManager == null) return false;
        return perkManager.hasPerk(guild.getLevel(), GuildPerkType.ALLOW_GUILD_SETHOME);
    }
    public String getGuildHomeParticleEffect(Guild guild) { 
        if (guild == null || perkManager == null) return null;
        return perkManager.getStringPerkValue(guild.getLevel(), GuildPerkType.GUILD_HOME_PARTICLE);
    }
    public int getPassiveHasteAuraAmplifier(Guild guild) { 
        if (guild == null || perkManager == null) return -1;
        if (perkManager.hasPerk(guild.getLevel(), GuildPerkType.PASSIVE_HASTE_AURA)) {
            return perkManager.getIntPerkValue(guild.getLevel(), GuildPerkType.PASSIVE_HASTE_AURA);
        }
        return -1;
    }
    public PerkManager getPerkManager() { 
        return perkManager;
    }

    // --- Guild Bank Management --- 
    public void saveGuildBank(Guild guild) { 
        if (dbManager != null && guild != null) dbManager.saveGuildBank(guild);
    }

    // --- Guild Upkeep Processing --- 
    public void processAllGuildUpkeep() { 
        System.out.println("Processing guild upkeep...");
        for (Guild guild : guilds.values()) {
            if (guild.getClaimedChunks().isEmpty()) continue;
            long totalUpkeepCost = guild.getClaimedChunks().size() * this.upkeepXpPerChunk;
            if (totalUpkeepCost <= 0) continue;
            boolean paymentSuccessful = guild.payXpUpkeep(totalUpkeepCost);
            if (paymentSuccessful) {
                updateGuildLevelAndXp(guild);
                Player leader = Bukkit.getPlayer(guild.getLeader());
                if (leader != null && leader.isOnline()) {
                    leader.sendMessage(ChatColor.GREEN + "Your guild paid " + ChatColor.AQUA + totalUpkeepCost + ChatColor.GREEN + " XP for territory upkeep.");
                }
                System.out.println("Guild '" + guild.getName() + "' paid " + totalUpkeepCost + " XP for upkeep.");
            } else {
                System.out.println("Guild '" + guild.getName() + "' FAILED to pay " + totalUpkeepCost + " XP for upkeep. Unclaiming all lands.");
                String upkeepFailedMessage = ChatColor.RED + "Your guild, " + ChatColor.AQUA + guild.getName() + ChatColor.RED + ", failed to pay upkeep of " + ChatColor.YELLOW + totalUpkeepCost + ChatColor.RED + " XP. All claimed lands have been lost!";
                for (UUID memberUuid : guild.getMembers()) {
                    Player member = Bukkit.getPlayer(memberUuid);
                    if (member != null && member.isOnline() && (guild.isOfficer(memberUuid) || guild.getLeader().equals(memberUuid))) {
                        member.sendMessage(upkeepFailedMessage);
                    }
                }
                List<String> chunksToUnclaim = new ArrayList<>(guild.getClaimedChunks());
                for (String chunkId : chunksToUnclaim) {
                    String[] parts = chunkId.split(":");
                    if (parts.length == 3) {
                        World world = Bukkit.getWorld(parts[0]);
                        if (world != null) {
                            try {
                                int chunkX = Integer.parseInt(parts[1]);
                                int chunkZ = Integer.parseInt(parts[2]);
                                unclaimChunk(guild, world, chunkX, chunkZ);
                            } catch (NumberFormatException e) {
                                System.err.println("Error parsing chunkId for unclaim: " + chunkId + " for guild " + guild.getName());
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Guild upkeep processing finished.");
    }

    // --- Guild Outpost Management ---
    public boolean createOutpost(Player player, OutpostType type) {
        Guild guild = getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return false; }
        if (!guild.isOfficer(player.getUniqueId()) && !guild.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only officers or the leader can establish outposts."); return false;
        }
        long creationCost;
        long intervalMillis;
        switch (type) {
            case XP_SIPHON: creationCost = this.xpSiphonCreationCost; intervalMillis = this.xpSiphonIntervalMillis; break;
            case BARRACKS: creationCost = this.barracksCreationCost; intervalMillis = this.barracksIntervalMillis; break;
            case RESOURCE_SILO: creationCost = this.resourceSiloCreationCost; intervalMillis = this.resourceSiloIntervalMillis; break;
            default: player.sendMessage(ChatColor.RED + "Unknown outpost type."); return false;
        }
        if (guild.hasOutpost(type)) {
            player.sendMessage(ChatColor.RED + "Your guild already has a " + type.getDisplayName() + " outpost."); return false;
        }
        Location playerLocation = player.getLocation();
        String playerChunkId = getChunkId(playerLocation.getWorld(), playerLocation.getChunk().getX(), playerLocation.getChunk().getZ());
        if (!guild.getClaimedChunks().contains(playerChunkId)) {
            player.sendMessage(ChatColor.RED + "You can only build outposts in claimed territory."); return false;
        }
        if (!guild.payXpUpkeep(creationCost)) {
            player.sendMessage(ChatColor.RED + "Guild needs " + creationCost + " XP for a " + type.getDisplayName() + "."); return false;
        }
        Location centralBlockLocation = generateOutpostStructure(playerLocation, type);
        if (centralBlockLocation == null) {
            player.sendMessage(ChatColor.RED + "Failed to generate outpost structure.");
            guild.addXp(creationCost); // Refund
            updateGuildLevelAndXp(guild);
            return false;
        }
        Guild.LocationData locData = new Guild.LocationData(centralBlockLocation.getWorld().getName(), centralBlockLocation.getBlockX(), centralBlockLocation.getBlockY(), centralBlockLocation.getBlockZ());
        long initialNextTick = System.currentTimeMillis() + intervalMillis;
        guild.addOutpost(type, locData, initialNextTick);
        if (dbManager != null) dbManager.saveGuildOutposts(guild);
        updateGuildLevelAndXp(guild);
        if (hologramManager.isEnabled()) {
            double yOffset = (type == OutpostType.XP_SIPHON) ? 3.0 : 2.7;
            Location holoLoc = centralBlockLocation.clone().add(0.5, yOffset, 0.5);
            hologramManager.createOutpostHologram(holoLoc, guild, type, initialNextTick); // Correctly passing timestamp
        }
        player.sendMessage(ChatColor.GREEN + type.getDisplayName() + " outpost established!");
        return true;
    }

    public boolean destroyOutpost(Player player, OutpostType type) {
        Guild guild = getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return false; }
        if (!guild.isOfficer(player.getUniqueId()) && !guild.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only officers or leaders can destroy outposts."); return false;
        }
        Guild.ActiveOutpostInfo outpostInfo = guild.getActiveOutpostInfo(type);
        if (outpostInfo == null) {
            player.sendMessage(ChatColor.RED + "Your guild does not have a " + type.getDisplayName() + " outpost."); return false;
        }
        Location outpostCoreLoc = getOutpostCoreLocation(guild, type);
         if (outpostCoreLoc == null) { 
            player.sendMessage(ChatColor.RED + "Error finding outpost location. Removing data.");
            guild.removeOutpost(type);
            if (dbManager != null) dbManager.saveGuildOutposts(guild);
            return true; 
        }
        removeOutpostStructure(outpostCoreLoc, type);
        if (hologramManager.isEnabled()) {
            hologramManager.deleteOutpostHologram(outpostCoreLoc, guild, type);
        }
        guild.removeOutpost(type);
        if (dbManager != null) dbManager.saveGuildOutposts(guild);
        player.sendMessage(ChatColor.GREEN + type.getDisplayName() + " outpost destroyed.");
        return true;
    }

    public void getGuildOutpostStatus(Player player) { 
        Guild guild = getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "Not in a guild."); return; }
        Map<OutpostType, Guild.ActiveOutpostInfo> outposts = guild.getAllActiveOutposts();
        if (outposts.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "No active outposts."); return; }
        player.sendMessage(ChatColor.GOLD + "--- " + guild.getName() + " Outpost Status ---");
        for (Map.Entry<OutpostType, Guild.ActiveOutpostInfo> entry : outposts.entrySet()) {
            Guild.LocationData loc = entry.getValue().location();
            long nextTick = entry.getValue().nextTickTimestamp();
            String timeRem = formatTimeMillis(nextTick - System.currentTimeMillis());
            player.sendMessage(ChatColor.AQUA + entry.getKey().getDisplayName() + ChatColor.GRAY + " at " + loc.worldName() + " (" + loc.x() + "," + loc.y() + "," + loc.z() + ")");
            switch (entry.getKey()) {
                case XP_SIPHON: player.sendMessage(ChatColor.GRAY + "  Generates: " + this.xpSiphonXpGeneration + " XP. Next: " + ChatColor.YELLOW + timeRem); break;
                case BARRACKS: player.sendMessage(ChatColor.GRAY + "  Generates: " + this.barracksXpGeneration + " XP. Next: " + ChatColor.YELLOW + timeRem); break;
                case RESOURCE_SILO: player.sendMessage(ChatColor.GRAY + "  Status: Operational. Next attempt: " + ChatColor.YELLOW + timeRem); break;
            }
        }
    }

    private String formatTimeMillis(long millis) {
        if (millis <= 0) return "Processing Now";
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long h = TimeUnit.MILLISECONDS.toHours(millis);
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return (m > 0) ? String.format("%dm %02ds", m, s) : String.format("%ds", s);
    }

    public void processXpSiphons() {
        long currentTime = System.currentTimeMillis();
        for (Guild guild : guilds.values()) {
            Guild.ActiveOutpostInfo info = guild.getActiveOutpostInfo(OutpostType.XP_SIPHON);
            if (info != null && currentTime >= info.nextTickTimestamp()) {
                guild.addXp(this.xpSiphonXpGeneration);
                updateGuildLevelAndXp(guild);
                long newNextTick = currentTime + this.xpSiphonIntervalMillis;
                guild.updateOutpostNextTick(OutpostType.XP_SIPHON, newNextTick);
                if (dbManager != null) dbManager.saveGuildOutposts(guild);
                if (hologramManager.isEnabled()) {
                    Location coreLoc = getOutpostCoreLocation(guild, OutpostType.XP_SIPHON);
                    if(coreLoc != null) hologramManager.createOutpostHologram(coreLoc.clone().add(0.5, 3.0, 0.5), guild, OutpostType.XP_SIPHON, newNextTick); 
                }
                Player leader = Bukkit.getPlayer(guild.getLeader());
                if (leader != null && leader.isOnline()) leader.sendMessage(ChatColor.GREEN + "XP Siphon: +" + this.xpSiphonXpGeneration + " XP!");
            }
        }
    }

    public void processBarracks() {
        long currentTime = System.currentTimeMillis();
        for (Guild guild : guilds.values()) {
            Guild.ActiveOutpostInfo info = guild.getActiveOutpostInfo(OutpostType.BARRACKS);
            if (info != null && currentTime >= info.nextTickTimestamp()) {
                guild.addXp(this.barracksXpGeneration);
                updateGuildLevelAndXp(guild);
                long newNextTick = currentTime + this.barracksIntervalMillis;
                guild.updateOutpostNextTick(OutpostType.BARRACKS, newNextTick);
                if (dbManager != null) dbManager.saveGuildOutposts(guild);
                if (hologramManager.isEnabled()) {
                    Location coreLoc = getOutpostCoreLocation(guild, OutpostType.BARRACKS);
                    if(coreLoc != null) hologramManager.createOutpostHologram(coreLoc.clone().add(0.5, 2.7, 0.5), guild, OutpostType.BARRACKS, newNextTick);
                }
                Player leader = Bukkit.getPlayer(guild.getLeader());
                if (leader != null && leader.isOnline()) leader.sendMessage(ChatColor.GREEN + "Barracks: +" + this.barracksXpGeneration + " XP!");
            }
        }
    }

    public void processResourceSilos(GuildWarsPlugin pluginInstance) {
        ConfigurationSection siloConfig = pluginInstance.getConfig().getConfigurationSection("outposts.RESOURCE_SILO");
        if (siloConfig == null) { System.err.println("RSilo: Main config section missing."); return; }
        ConfigurationSection itemConfig = siloConfig.getConfigurationSection("generated-resources");
        if (itemConfig == null) { System.err.println("RSilo: 'generated-resources' missing."); return; }
        long currentTime = System.currentTimeMillis();
        for (Guild guild : guilds.values()) {
            Guild.ActiveOutpostInfo outpostInfo = guild.getActiveOutpostInfo(OutpostType.RESOURCE_SILO);
            if (outpostInfo != null && currentTime >= outpostInfo.nextTickTimestamp()) {
                long newNextTick = currentTime + this.resourceSiloIntervalMillis;
                guild.updateOutpostNextTick(OutpostType.RESOURCE_SILO, newNextTick);
                if (dbManager != null) dbManager.saveGuildOutposts(guild); 
                if (random.nextInt(100) < this.resourceSiloGenerationChance) {
                    final List<ItemStack> itemsToDeposit = new ArrayList<>();
                    for (String matName : itemConfig.getKeys(false)) {
                        String range = itemConfig.getString(matName, "1-1");
                        String[] parts = range.split("-");
                        int min = 1, max = 1;
                        try {
                            min = Integer.parseInt(parts[0]);
                            max = parts.length > 1 ? Integer.parseInt(parts[1]) : min;
                            if (min <= 0) min = 1;
                            if (max < min) max = min;
                        } catch (NumberFormatException ex) { continue; }
                        Material material = Material.matchMaterial(matName.toUpperCase());
                        if (material != null) {
                            int amount = (min == max) ? min : random.nextInt(max - min + 1) + min;
                            if (amount > 0) itemsToDeposit.add(new ItemStack(material, amount));
                        }
                    }
                    if (!itemsToDeposit.isEmpty()) {
                        final Guild finalGuild = guild;
                        Bukkit.getScheduler().runTask(pluginInstance, () -> {
                            ItemStack[] bank = finalGuild.getBankContents();
                            Map<Material, Integer> summary = new HashMap<>();
                            for (ItemStack item : itemsToDeposit) {
                                int remaining = item.getAmount();
                                for (int i = 0; i < Guild.BANK_SIZE && remaining > 0; i++) { 
                                    if (bank[i] != null && bank[i].isSimilar(item) && bank[i].getAmount() < bank[i].getMaxStackSize()) {
                                        int add = Math.min(remaining, bank[i].getMaxStackSize() - bank[i].getAmount());
                                        bank[i].setAmount(bank[i].getAmount() + add); remaining -= add;
                                        summary.put(item.getType(), summary.getOrDefault(item.getType(), 0) + add);
                                    }
                                }
                                for (int i = 0; i < Guild.BANK_SIZE && remaining > 0; i++) { 
                                    if (bank[i] == null || bank[i].getType() == Material.AIR) {
                                        bank[i] = new ItemStack(item.getType(), remaining); 
                                        summary.put(item.getType(), summary.getOrDefault(item.getType(), 0) + remaining); remaining = 0; break;
                                    }
                                }
                                if (remaining > 0) plugin.getLogger().warning("Silo for " + finalGuild.getName() + ": Not all " + item.getType() + " fit.");
                            }
                            if (!summary.isEmpty()) {
                                finalGuild.setBankContents(bank);
                                dbManager.saveGuildBank(finalGuild);
                                String msg = summary.entrySet().stream().map(e->e.getValue()+"x "+e.getKey().name().toLowerCase().replace("_"," ")).collect(Collectors.joining(", "));
                                Player ldr = Bukkit.getPlayer(finalGuild.getLeader());
                                if (ldr != null && ldr.isOnline()) ldr.sendMessage(ChatColor.GREEN + "Resource Silo generated: " + ChatColor.AQUA + msg);
                            }
                        });
                    }
                }
                if (hologramManager.isEnabled()) { 
                    Location coreLoc = getOutpostCoreLocation(guild, OutpostType.RESOURCE_SILO);
                    if(coreLoc != null) hologramManager.createOutpostHologram(coreLoc.clone().add(0.5, 2.7, 0.5), guild, OutpostType.RESOURCE_SILO, newNextTick);
                }
            }
        }
    }

    // --- Guild War Management ---
    public boolean isAtWarWith(String guild1Name, String guild2Name) { 
         if (guild1Name == null || guild2Name == null) return false;
        List<String> wars = activeWars.get(guild1Name);
        return wars != null && wars.contains(guild2Name);
    }
    public List<String> getWarsForGuild(String guildName) { 
        return activeWars.getOrDefault(guildName, Collections.emptyList());
    }
    public boolean declareWar(Player declarer, String targetGuildName) { 
        Guild declaringGuild = getGuildByPlayer(declarer.getUniqueId());
        if (declaringGuild == null) { declarer.sendMessage(ChatColor.RED + "Not in a guild."); return false; }
        if (!declaringGuild.getLeader().equals(declarer.getUniqueId())) { declarer.sendMessage(ChatColor.RED + "Leaders only."); return false; }
        Guild targetGuild = getGuild(targetGuildName);
        if (targetGuild == null) { declarer.sendMessage(ChatColor.RED + "Target guild not found."); return false; }
        if (declaringGuild.getName().equalsIgnoreCase(targetGuild.getName())) { declarer.sendMessage(ChatColor.RED + "Cannot war yourself."); return false; }
        if (isAtWarWith(declaringGuild.getName(), targetGuild.getName())) { declarer.sendMessage(ChatColor.YELLOW + "Already at war."); return false; }
        long startDate = System.currentTimeMillis() / 1000L;
        dbManager.saveWar(declaringGuild.getName(), targetGuild.getName(), startDate);
        activeWars.computeIfAbsent(declaringGuild.getName(), k -> new ArrayList<>()).add(targetGuild.getName());
        activeWars.computeIfAbsent(targetGuild.getName(), k -> new ArrayList<>()).add(declaringGuild.getName());
        Bukkit.broadcastMessage(ChatColor.RED + "WAR! " + ChatColor.GOLD + declaringGuild.getName() + ChatColor.RED + " vs " + ChatColor.GOLD + targetGuild.getName() + ChatColor.RED + "!");
        return true;
    }
    public boolean declarePeace(Player declarer, String targetGuildName) { 
        Guild declaringGuild = getGuildByPlayer(declarer.getUniqueId());
        if (declaringGuild == null) { declarer.sendMessage(ChatColor.RED + "Not in a guild."); return false; }
        if (!declaringGuild.getLeader().equals(declarer.getUniqueId())) { declarer.sendMessage(ChatColor.RED + "Leaders only."); return false; }
        Guild targetGuild = getGuild(targetGuildName);
        if (targetGuild == null) { declarer.sendMessage(ChatColor.RED + "Target guild not found."); return false; }
        if (!isAtWarWith(declaringGuild.getName(), targetGuild.getName())) { declarer.sendMessage(ChatColor.YELLOW + "Not at war with them."); return false; }
        dbManager.deleteWar(declaringGuild.getName(), targetGuild.getName());
        List<String> dWars = activeWars.get(declaringGuild.getName());
        if (dWars != null) dWars.remove(targetGuild.getName());
        List<String> tWars = activeWars.get(targetGuild.getName());
        if (tWars != null) tWars.remove(declaringGuild.getName());
        Bukkit.broadcastMessage(ChatColor.GREEN + "PEACE between " + ChatColor.GOLD + declaringGuild.getName() + ChatColor.GREEN + " and " + ChatColor.GOLD + targetGuild.getName() + ChatColor.GREEN + ".");
        return true;
    }
    public void incrementWarScore(String actingGuildName, String opposingGuildName, int points, String reason) { 
        if (points <= 0) return;
        Guild actingGuild = getGuild(actingGuildName);
        Guild opposingGuild = getGuild(opposingGuildName);
        if (actingGuild == null || opposingGuild == null || !isAtWarWith(actingGuildName, opposingGuildName)) return;
        String g1 = actingGuildName.compareTo(opposingGuildName) < 0 ? actingGuildName : opposingGuildName;
        String g2 = actingGuildName.compareTo(opposingGuildName) < 0 ? opposingGuildName : actingGuildName;
        int g1ScoreDelta = actingGuildName.equals(g1) ? points : 0;
        int g2ScoreDelta = actingGuildName.equals(g2) ? points : 0;
        dbManager.updateWarScore(g1, g2, g1ScoreDelta, g2ScoreDelta);
        Map<String, Integer> newScores = dbManager.getWarScore(g1, g2);
        String scoreMsg = String.format("%s%s %s+%d points %sagainst %s%s%s! (Score: %d - %d)", ChatColor.GOLD, actingGuild.getName(), ChatColor.GREEN, points, ChatColor.GREEN, ChatColor.GOLD, opposingGuild.getName(), ChatColor.GREEN, newScores.getOrDefault(actingGuildName,0), newScores.getOrDefault(opposingGuildName,0));
        Bukkit.getOnlinePlayers().stream().filter(p -> { Guild g = getGuildByPlayer(p.getUniqueId()); return g != null && (g.getName().equals(actingGuildName) || g.getName().equals(opposingGuildName)); }).forEach(p -> p.sendMessage(scoreMsg));
        plugin.getLogger().info("War Score: " + ChatColor.stripColor(scoreMsg));
    }
    public DatabaseManager getDbManager() { 
        return dbManager; 
    }
    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
