package com.guildwars.commands;

import com.guildwars.guild.Guild;
import com.guildwars.guild.GuildManager;
import com.guildwars.guild.PerkManager;
import com.guildwars.guild.perks.GuildPerkType;
import com.guildwars.guild.outposts.OutpostType;
import com.guildwars.gui.GuildMainGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuildCommand implements CommandExecutor, TabCompleter {

    private final GuildManager guildManager;
    private final GuildMainGui guildMainGui;

    // This is a legacy contribution system, consider removing if GUI is primary
    private static final Map<String, ContributableItemInfo> CONTRIBUTABLE_ITEMS = new HashMap<>();
    static {
        CONTRIBUTABLE_ITEMS.put("diamond", new ContributableItemInfo(Material.DIAMOND, 10));
        CONTRIBUTABLE_ITEMS.put("iron_ingot", new ContributableItemInfo(Material.IRON_INGOT, 1));
    }
    private static class ContributableItemInfo {
        public final Material material; public final int xpValue;
        public ContributableItemInfo(Material material, int xpValue) { this.material = material; this.xpValue = xpValue; }
    }

    public GuildCommand(GuildManager guildManager, GuildMainGui guildMainGui) {
        this.guildManager = guildManager;
        this.guildMainGui = guildMainGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            if (guildMainGui != null) guildMainGui.open(player);
            else sendHelpMessage(player);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create": handleCreateCommand(player, args); break;
            case "invite": handleInviteCommand(player, args); break;
            case "join": handleJoinCommand(player, args); break;
            case "leave": handleLeaveCommand(player, args); break;
            case "kick": handleKickCommand(player, args); break;
            case "promote": handlePromoteCommand(player, args); break;
            case "demote": handleDemoteCommand(player, args); break;
            case "info": handleInfoCommand(player, args); break;
            case "disband": handleDisbandCommand(player, args); break;
            case "transfer": case "transferleader": handleTransferLeaderCommand(player, args); break;
            case "list": handleListCommand(player, args); break;
            case "contribute": handleContributeCommand(player, args); break; // Legacy command
            case "sethome": handleSetHomeCommand(player, args); break;
            case "home": handleHomeCommand(player, args); break;
            case "claim": handleClaimCommand(player, args); break;
            case "unclaim": handleUnclaimCommand(player, args); break;
            case "outpost": handleOutpostCommand(player, args); break;
            case "war": handleWarCommand(player, args); break;
            case "help": // Fallthrough to default
            default:
                sendHelpMessage(player);
                break;
        }
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Guild Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/guild" + ChatColor.GRAY + " - Opens the main guild GUI.");
        player.sendMessage(ChatColor.YELLOW + "/guild create <name>" + ChatColor.GRAY + " - Creates a guild.");
        player.sendMessage(ChatColor.YELLOW + "/guild invite <player>" + ChatColor.GRAY + " - Invites a player.");
        player.sendMessage(ChatColor.YELLOW + "/guild join <guildName>" + ChatColor.GRAY + " - Joins an invited guild.");
        player.sendMessage(ChatColor.YELLOW + "/guild leave" + ChatColor.GRAY + " - Leaves your guild.");
        player.sendMessage(ChatColor.YELLOW + "/guild kick <player>" + ChatColor.GRAY + " - Kicks a member (Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild promote <player>" + ChatColor.GRAY + " - Promotes member to officer (Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild demote <player>" + ChatColor.GRAY + " - Demotes officer (Leader only).");
        player.sendMessage(ChatColor.YELLOW + "/guild info [guildName]" + ChatColor.GRAY + " - Shows guild information.");
        player.sendMessage(ChatColor.YELLOW + "/guild disband" + ChatColor.GRAY + " - Disbands your guild (Leader only).");
        player.sendMessage(ChatColor.YELLOW + "/guild transfer <player>" + ChatColor.GRAY + " - Transfers leadership (Leader only).");
        player.sendMessage(ChatColor.YELLOW + "/guild list" + ChatColor.GRAY + " - Lists all guilds.");
        player.sendMessage(ChatColor.YELLOW + "/guild sethome" + ChatColor.GRAY + " - Sets guild home (Perk required, Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild home" + ChatColor.GRAY + " - Teleports to guild home.");
        player.sendMessage(ChatColor.YELLOW + "/guild claim" + ChatColor.GRAY + " - Claims current chunk (Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild unclaim" + ChatColor.GRAY + " - Unclaims current chunk (Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild outpost <create|destroy|status> [type]" + ChatColor.GRAY + " - Manages outposts (Officer+).");
        player.sendMessage(ChatColor.YELLOW + "/guild war <declare|peace|list|score|contest> [guildName]" + ChatColor.GRAY + " - Manages guild wars.");
        player.sendMessage(ChatColor.YELLOW + "/guild help" + ChatColor.GRAY + " - Shows this help message.");
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /guild create <name>"); return; }
        String guildName = args[1];
        if (guildName.length() < 3 || guildName.length() > 16) { player.sendMessage(ChatColor.RED + "Guild name: 3-16 chars."); return; }
        if (!guildName.matches("^[a-zA-Z0-9_]+$")) { player.sendMessage(ChatColor.RED + "Guild name: Alphanumeric & underscores only."); return; }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) { player.sendMessage(ChatColor.RED + "You are already in a guild."); return; }
        if (guildManager.getGuild(guildName) != null) { player.sendMessage(ChatColor.RED + "Guild '" + guildName + "' already exists."); return; }
        Guild newGuild = guildManager.createGuild(guildName, player.getUniqueId());
        if (newGuild != null) {
            player.sendMessage(ChatColor.GREEN + "Guild '" + ChatColor.GOLD + guildName + ChatColor.GREEN + "' created!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create guild.");
        }
    }
    
    private void handleInviteCommand(Player inviter, String[] args) {
        if (args.length < 2) { inviter.sendMessage(ChatColor.RED + "Usage: /guild invite <playerName>"); return; }
        Guild inviterGuild = guildManager.getGuildByPlayer(inviter.getUniqueId());
        if (inviterGuild == null) { inviter.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!inviterGuild.isOfficer(inviter.getUniqueId())) { inviter.sendMessage(ChatColor.RED + "Only officers can invite."); return; }
        if (inviterGuild.getMembers().size() >= guildManager.getEffectiveMaxMembers(inviterGuild)) { inviter.sendMessage(ChatColor.RED + "Guild is full."); return; }
        Player targetPlayer = Bukkit.getPlayerExact(args[1]);
        if (targetPlayer == null) { inviter.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found/online."); return; }
        if (inviter.getUniqueId().equals(targetPlayer.getUniqueId())) { inviter.sendMessage(ChatColor.RED + "Cannot invite yourself."); return; }
        if (guildManager.getGuildByPlayer(targetPlayer.getUniqueId()) != null) { inviter.sendMessage(ChatColor.RED + targetPlayer.getName() + " is already in a guild."); return; }
        guildManager.addInvite(targetPlayer.getUniqueId(), inviterGuild.getName());
        inviter.sendMessage(ChatColor.GREEN + "Invitation sent to " + targetPlayer.getName() + ".");
        targetPlayer.sendMessage(ChatColor.GREEN + "Invited to join " + inviterGuild.getName() + " by " + inviter.getName() + ". Type /guild join " + inviterGuild.getName());
    }

    private void handleJoinCommand(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /guild join <guildName>"); return; }
        if (guildManager.getGuildByPlayer(player.getUniqueId()) != null) { player.sendMessage(ChatColor.RED + "You are already in a guild."); return; }
        String guildNameToJoin = args[1];
        Guild guild = guildManager.getGuild(guildNameToJoin);
        if (guild == null) { player.sendMessage(ChatColor.RED + "Guild '" + guildNameToJoin + "' not found."); return; }
        if (guild.getMembers().size() >= guildManager.getEffectiveMaxMembers(guild)) { player.sendMessage(ChatColor.RED + "Guild '" + guild.getName() + "' is full."); return; }
        if (!guildManager.hasInvite(player.getUniqueId(), guildNameToJoin)) { player.sendMessage(ChatColor.RED + "No invite from '" + guildNameToJoin + "'."); return; }
        if (guildManager.addPlayerToGuild(guildNameToJoin, player.getUniqueId())) {
            guildManager.removeInvite(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Joined guild: " + guildNameToJoin + "!");
            guild.getMembers().stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline() && !p.equals(player))
                   .forEach(p -> p.sendMessage(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " has joined the guild!"));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to join guild.");
        }
    }

    private void handleLeaveCommand(Player player, String[] args) {
        Guild currentGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (currentGuild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (currentGuild.getLeader().equals(player.getUniqueId()) && currentGuild.getMembers().size() > 1) {
            player.sendMessage(ChatColor.RED + "Leader must transfer leadership or disband if others remain."); return;
        }
        boolean left = guildManager.removePlayerFromGuild(currentGuild.getName(), player.getUniqueId()); // This also handles disband if last member
        if (left) {
            player.sendMessage(ChatColor.GREEN + "You have left " + currentGuild.getName() + ".");
             // Guild might be null if disbanded, check before notifying
            Guild guildAfterLeave = guildManager.getGuild(currentGuild.getName());
            if (guildAfterLeave != null) { 
                 guildAfterLeave.getMembers().stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline())
                                 .forEach(p -> p.sendMessage(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " has left the guild."));
            } else {
                 player.sendMessage(ChatColor.GRAY + "The guild was disbanded as you were the last member.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to leave guild.");
        }
    }

    private void handleKickCommand(Player kicker, String[] args) {
        if (args.length < 2) { kicker.sendMessage(ChatColor.RED + "Usage: /guild kick <playerName>"); return; }
        Guild kickerGuild = guildManager.getGuildByPlayer(kicker.getUniqueId());
        if (kickerGuild == null) { kicker.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!kickerGuild.isOfficer(kicker.getUniqueId())) { kicker.sendMessage(ChatColor.RED + "Only officers can kick."); return; }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(args[1]);
        if (!targetOfflinePlayer.hasPlayedBefore() && targetOfflinePlayer.getUniqueId() == null) { kicker.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found."); return; }
        UUID targetUuid = targetOfflinePlayer.getUniqueId();
        if (kicker.getUniqueId().equals(targetUuid)) { kicker.sendMessage(ChatColor.RED + "Cannot kick yourself."); return; }
        if (!kickerGuild.isMember(targetUuid)) { kicker.sendMessage(ChatColor.RED + args[1] + " is not in your guild."); return; }
        if (kickerGuild.getLeader().equals(targetUuid) && !kicker.getUniqueId().equals(targetUuid)) { kicker.sendMessage(ChatColor.RED + "Cannot kick the leader."); return; }
        if (kickerGuild.isOfficer(targetUuid) && !kickerGuild.getLeader().equals(kicker.getUniqueId())) { kicker.sendMessage(ChatColor.RED + "Officers cannot kick other officers."); return; }
        if (guildManager.removePlayerFromGuild(kickerGuild.getName(), targetUuid)) {
            kicker.sendMessage(ChatColor.GREEN + args[1] + " kicked from the guild.");
            if (targetOfflinePlayer.isOnline()) targetOfflinePlayer.getPlayer().sendMessage(ChatColor.RED + "You were kicked from " + kickerGuild.getName());
            kickerGuild.getMembers().stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).forEach(p -> p.sendMessage(ChatColor.YELLOW + args[1] + " was kicked by " + kicker.getName()));
        } else {
            kicker.sendMessage(ChatColor.RED + "Failed to kick " + args[1] + ".");
        }
    }
    
    private void handlePromoteCommand(Player promoter, String[] args) {
        if (args.length < 2) { promoter.sendMessage(ChatColor.RED + "Usage: /guild promote <playerName>"); return; }
        Guild guild = guildManager.getGuildByPlayer(promoter.getUniqueId());
        if (guild == null) { promoter.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!guild.isOfficer(promoter.getUniqueId())) { promoter.sendMessage(ChatColor.RED + "Only officers can promote."); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && target.getUniqueId() == null) { promoter.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found."); return; }
        UUID targetUuid = target.getUniqueId();
        if (!guild.isMember(targetUuid)) { promoter.sendMessage(ChatColor.RED + args[1] + " is not in your guild."); return; }
        if (guild.isOfficer(targetUuid)) { promoter.sendMessage(ChatColor.YELLOW + args[1] + " is already an officer."); return; }
        if (guildManager.promotePlayerInGuild(guild.getName(), targetUuid)) {
            promoter.sendMessage(ChatColor.GREEN + args[1] + " promoted to officer.");
            if(target.isOnline()) target.getPlayer().sendMessage(ChatColor.GREEN + "You were promoted to officer!");
        } else { promoter.sendMessage(ChatColor.RED + "Failed to promote."); }
    }

    private void handleDemoteCommand(Player demoter, String[] args) {
        if (args.length < 2) { demoter.sendMessage(ChatColor.RED + "Usage: /guild demote <playerName>"); return; }
        Guild guild = guildManager.getGuildByPlayer(demoter.getUniqueId());
        if (guild == null) { demoter.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!guild.getLeader().equals(demoter.getUniqueId())) { demoter.sendMessage(ChatColor.RED + "Only the leader can demote."); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
         if (!target.hasPlayedBefore() && target.getUniqueId() == null) { demoter.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found."); return; }
        UUID targetUuid = target.getUniqueId();
        if (guild.getLeader().equals(targetUuid)) { demoter.sendMessage(ChatColor.RED + "Cannot demote the leader."); return; }
        if (!guild.isOfficer(targetUuid)) { demoter.sendMessage(ChatColor.YELLOW + args[1] + " is not an officer."); return; }
        if (guildManager.demotePlayerInGuild(guild.getName(), targetUuid)) {
            demoter.sendMessage(ChatColor.GREEN + args[1] + " demoted to member.");
            if(target.isOnline()) target.getPlayer().sendMessage(ChatColor.YELLOW + "You were demoted to member.");
        } else { demoter.sendMessage(ChatColor.RED + "Failed to demote."); }
    }

    private void handleInfoCommand(Player player, String[] args) { /* ... (condensed, logic remains same as before) ... */
        Guild targetGuild = (args.length < 2) ? guildManager.getGuildByPlayer(player.getUniqueId()) : guildManager.getGuild(args[1]);
        if (targetGuild == null) { 
            player.sendMessage(args.length < 2 ? ChatColor.RED + "You are not in a guild." : ChatColor.RED + "Guild not found."); return; 
        }
        displayGuildInfo(player, targetGuild);
    }

    private void displayGuildInfo(Player receiver, Guild guild) { /* ... (condensed, logic remains same as before) ... */
        receiver.sendMessage(ChatColor.GOLD + "--- Guild: " + ChatColor.AQUA + guild.getName() + ChatColor.GOLD + " ---");
        OfflinePlayer leader = Bukkit.getOfflinePlayer(guild.getLeader());
        receiver.sendMessage(ChatColor.YELLOW + "Leader: " + ChatColor.WHITE + (leader.getName() != null ? leader.getName() : "Unknown"));
        receiver.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.AQUA + guild.getLevel());
        receiver.sendMessage(ChatColor.YELLOW + "XP: " + ChatColor.GREEN + guild.getCurrentXp() + "/" + guild.getXpForNextLevel());
        receiver.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + guild.getMembers().size() + "/" + guildManager.getEffectiveMaxMembers(guild));
        int maxClaims = GuildManager.BASE_MAX_CLAIMS + guildManager.getPerkManager().getAccumulatedIntValue(guild.getLevel(), GuildPerkType.MAX_CLAIMED_CHUNKS_INCREASE);
        receiver.sendMessage(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + guild.getClaimedChunks().size() + "/" + maxClaims);
        // TODO: Add war status and outpost status to info
    }

    private void handleDisbandCommand(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!guild.getLeader().equals(player.getUniqueId())) { player.sendMessage(ChatColor.RED + "Only the leader can disband."); return; }
        String guildName = guild.getName();
        if (guildManager.deleteGuild(guildName)) {
            Bukkit.broadcastMessage(ChatColor.RED + "Guild " + ChatColor.GOLD + guildName + ChatColor.RED + " has been disbanded!");
        } else { player.sendMessage(ChatColor.RED + "Failed to disband guild."); }
    }

    private void handleTransferLeaderCommand(Player player, String[] args) { /* ... (condensed, logic as before) ... */ 
        if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /guild transfer <player>"); return; }
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null || !guild.getLeader().equals(player.getUniqueId())) { player.sendMessage(ChatColor.RED + "Only leader can transfer."); return; }
        Player newLeader = Bukkit.getPlayerExact(args[1]);
        if (newLeader == null || !guild.isMember(newLeader.getUniqueId()) || newLeader.equals(player)) { player.sendMessage(ChatColor.RED + "Invalid target player."); return; }
        guildManager.getDbManager().updateGuildLeaderDB(guild.getName(), newLeader.getUniqueId()); // DB first
        guild.setLeader(newLeader.getUniqueId()); // Then memory
        player.sendMessage(ChatColor.GREEN + "Leadership transferred to " + newLeader.getName());
        newLeader.sendMessage(ChatColor.GREEN + "You are the new leader of " + guild.getName());
    }

    private void handleListCommand(Player player, String[] args) { /* ... (condensed, logic as before) ... */ 
        Map<String, Guild> allGuilds = guildManager.getAllGuilds();
        if(allGuilds.isEmpty()){ player.sendMessage(ChatColor.YELLOW+"No guilds exist."); return;}
        player.sendMessage(ChatColor.GOLD+"--- Guilds ("+allGuilds.size()+") ---");
        allGuilds.values().forEach(g -> player.sendMessage(ChatColor.AQUA + g.getName() + ChatColor.GRAY + " - L:"+g.getLevel()+" M:"+g.getMembers().size()));
    }

    private void handleContributeCommand(Player player, String[] args) { /* ... (condensed legacy command) ... */ 
        player.sendMessage(ChatColor.YELLOW + "Item contribution via command is legacy. Please use the GUI via /guild.");
    }

    private void handleSetHomeCommand(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "Not in a guild."); return; }
        if (!guild.isOfficer(player.getUniqueId())) { player.sendMessage(ChatColor.RED + "Officers only."); return; }
        if (!guildManager.canGuildSetHome(guild)) { player.sendMessage(ChatColor.RED + "Guild Home perk not unlocked."); return; }
        guildManager.setGuildHome(guild, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Guild home set!");
    }

    private void handleHomeCommand(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "Not in a guild."); return; }
        Location home = guild.getGuildHomeLocation();
        if (home == null) { player.sendMessage(ChatColor.RED + "Home not set or perk missing."); return; }
        player.teleport(home);
        player.sendMessage(ChatColor.GREEN + "Teleported to guild home.");
    }

    private void handleClaimCommand(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "Not in a guild."); return; }
        if (!guild.isOfficer(player.getUniqueId())) { player.sendMessage(ChatColor.RED + "Officers only."); return; }
        Chunk chunk = player.getLocation().getChunk();
        if (guildManager.getGuildOwningChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()) != null) { player.sendMessage(ChatColor.RED + "Chunk already claimed."); return; }
        if (!guildManager.claimChunk(guild, chunk.getWorld(), chunk.getX(), chunk.getZ())) { player.sendMessage(ChatColor.RED + "Cannot claim (limit reached or error)."); return; }
        player.sendMessage(ChatColor.GREEN + "Chunk claimed!");
    }

    private void handleUnclaimCommand(Player player, String[] args) {
        Guild guild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (guild == null) { player.sendMessage(ChatColor.RED + "Not in a guild."); return; }
        if (!guild.isOfficer(player.getUniqueId())) { player.sendMessage(ChatColor.RED + "Officers only."); return; }
        Chunk chunk = player.getLocation().getChunk();
        Guild owner = guildManager.getGuildOwningChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (owner == null || !owner.getName().equals(guild.getName())) { player.sendMessage(ChatColor.RED + "Your guild does not own this chunk."); return; }
        guildManager.unclaimChunk(guild, chunk.getWorld(), chunk.getX(), chunk.getZ());
        player.sendMessage(ChatColor.GREEN + "Chunk unclaimed.");
    }
    
    private void handleOutpostCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /guild outpost <create|destroy|status> [type]");
            player.sendMessage(ChatColor.YELLOW + "Available types: " + getAvailableOutpostTypesString());
            return;
        }
        String action = args[1].toLowerCase();
        OutpostType type = (args.length > 2) ? OutpostType.fromString(args[2]) : null;
        if (args.length > 2 && type == null && (action.equals("create") || action.equals("destroy"))){
             player.sendMessage(ChatColor.RED + "Unknown outpost type: " + args[2] + ". Available: " + getAvailableOutpostTypesString()); return;
        }
        switch (action) {
            case "create":
                if (type == null) { player.sendMessage(ChatColor.RED + "Usage: /guild outpost create <type>"); return; }
                guildManager.createOutpost(player, type);
                break;
            case "destroy":
                if (type == null) { player.sendMessage(ChatColor.RED + "Usage: /guild outpost destroy <type>"); return; }
                guildManager.destroyOutpost(player, type);
                break;
            case "status": guildManager.getGuildOutpostStatus(player); break;
            default: player.sendMessage(ChatColor.RED + "Unknown outpost action. Usage: /guild outpost <create|destroy|status> [type]"); break;
        }
    }

    private String getAvailableOutpostTypesString() {
        return Arrays.stream(OutpostType.values()).map(OutpostType::name).collect(Collectors.joining(", "));
    }

    private void handleWarCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /guild war <declare|peace|list|score|contest> [guildName]");
            return;
        }
        String action = args[1].toLowerCase();
        Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        switch (action) {
            case "declare":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /guild war declare <targetGuild>"); return; }
                guildManager.declareWar(player, args[2]);
                break;
            case "peace":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /guild war peace <targetGuild>"); return; }
                guildManager.declarePeace(player, args[2]);
                break;
            case "list":
                if (playerGuild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
                List<String> wars = guildManager.getWarsForGuild(playerGuild.getName());
                if (wars.isEmpty()) {
                    player.sendMessage(ChatColor.GREEN + "Your guild is at peace.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "--- " + playerGuild.getName() + " - Active Wars ---");
                    for (String enemyName : wars) {
                        Map<String, Integer> scores = guildManager.getDbManager().getWarScore(playerGuild.getName(), enemyName);
                        player.sendMessage(ChatColor.RED + " - vs " + ChatColor.DARK_RED + enemyName +
                                           ChatColor.YELLOW + " (Score: " + scores.getOrDefault(playerGuild.getName(), 0) + " - " + scores.getOrDefault(enemyName, 0) + ")");
                    }
                }
                break;
            case "score":
                handleWarScoreCommand(player, args);
                break;
            case "contest":
            case "neutralizechunk": 
                handleContestChunkCommand(player);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown war action. Usage: /guild war <declare|peace|list|score|contest> [guildName]");
                break;
        }
    }

    private void handleContestChunkCommand(Player player) {
        Guild attackerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (attackerGuild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (!attackerGuild.isOfficer(player.getUniqueId()) && !attackerGuild.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only officers or leaders can contest chunks."); return;
        }
        Chunk currentChunk = player.getLocation().getChunk();
        Guild defendingGuild = guildManager.getGuildOwningChunk(currentChunk.getWorld(), currentChunk.getX(), currentChunk.getZ());
        if (defendingGuild == null) { player.sendMessage(ChatColor.RED + "This chunk is unclaimed."); return; }
        if (defendingGuild.getName().equalsIgnoreCase(attackerGuild.getName())) { player.sendMessage(ChatColor.RED + "Cannot contest your own chunk."); return; }
        if (!guildManager.isAtWarWith(attackerGuild.getName(), defendingGuild.getName())) {
            player.sendMessage(ChatColor.RED + "Your guild is not at war with " + defendingGuild.getName() + "."); return;
        }
        if (guildManager.unclaimChunk(defendingGuild, currentChunk.getWorld(), currentChunk.getX(), currentChunk.getZ())) {
            String chunkCoords = "(" + currentChunk.getX() + "," + currentChunk.getZ() + ")@" + currentChunk.getWorld().getName();
            player.sendMessage(ChatColor.GREEN + "Neutralized chunk " + chunkCoords + " from " + defendingGuild.getName() + "!");
            guildManager.incrementWarScore(attackerGuild.getName(), defendingGuild.getName(), GuildManager.WAR_POINTS_PER_CHUNK_CONTEST, "neutralizing territory");
            // Simplified notifications for brevity in this example, GuildManager.incrementWarScore handles broad ones.
            Player defenderLeader = Bukkit.getPlayer(defendingGuild.getLeader());
            if(defenderLeader != null && defenderLeader.isOnline()) {
                defenderLeader.sendMessage(ChatColor.DARK_RED + "WAR: Your chunk " + chunkCoords + " was neutralized by " + attackerGuild.getName() + "!");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to contest chunk.");
        }
    }

    private void handleWarScoreCommand(Player player, String[] args) {
        Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());
        if (playerGuild == null) { player.sendMessage(ChatColor.RED + "You are not in a guild."); return; }
        if (args.length < 3) { // /guild war score OR /guild war score <enemy>
            List<String> currentWars = guildManager.getWarsForGuild(playerGuild.getName());
            if (currentWars.isEmpty()) { player.sendMessage(ChatColor.GREEN + "Your guild is not at war."); return; }
            player.sendMessage(ChatColor.GOLD + "Current War Scores for " + playerGuild.getName() + ":");
            for (String enemyName : currentWars) {
                Map<String, Integer> scores = guildManager.getDbManager().getWarScore(playerGuild.getName(), enemyName);
                player.sendMessage(ChatColor.YELLOW + " vs " + ChatColor.GOLD + enemyName + ChatColor.YELLOW + ": " +
                                   ChatColor.GREEN + playerGuild.getName() + " (" + scores.getOrDefault(playerGuild.getName(), 0) + ")" +
                                   ChatColor.GRAY + " - " +
                                   ChatColor.RED + enemyName + " (" + scores.getOrDefault(enemyName, 0) + ")");
            }
            return;
        }
        // /guild war score <guild1> <guild2> - Admin/general purpose if needed, or specific enemy
        // For now, let's assume args[2] is the enemy guild if player is in a guild
        String targetGuildName = args[2];
        Guild targetGuild = guildManager.getGuild(targetGuildName);
        if (targetGuild == null) { player.sendMessage(ChatColor.RED + "Guild '" + targetGuildName + "' not found."); return; }
        if (!guildManager.isAtWarWith(playerGuild.getName(), targetGuild.getName())) {
            player.sendMessage(ChatColor.YELLOW + "Your guild is not at war with " + targetGuild.getName() + "."); return; }
        Map<String, Integer> scores = guildManager.getDbManager().getWarScore(playerGuild.getName(), targetGuild.getName());
        player.sendMessage(ChatColor.GOLD + "War Score: " +
                           ChatColor.GREEN + playerGuild.getName() + " (" + scores.getOrDefault(playerGuild.getName(),0) + ")" +
                           ChatColor.GRAY + " vs " +
                           ChatColor.RED + targetGuild.getName() + " (" + scores.getOrDefault(targetGuild.getName(),0) + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;
        List<String> mainSubCommands = Arrays.asList("create", "invite", "join", "leave", "kick", "promote", "demote", "info", "disband", "transfer", "list", "help", "sethome", "home", "claim", "unclaim", "outpost", "war");

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], mainSubCommands, completions);
        } else if (args.length >= 2) {
            String subCmd = args[0].toLowerCase();
            Guild playerGuild = guildManager.getGuildByPlayer(player.getUniqueId());

            switch (subCmd) {
                case "invite": case "kick": case "promote": case "demote": case "transfer":
                    if (args.length == 2) {
                        List<String> playerSuggestions = new ArrayList<>();
                        if ("invite".equals(subCmd)) {
                            Bukkit.getOnlinePlayers().stream().filter(p -> guildManager.getGuildByPlayer(p.getUniqueId()) == null && !p.equals(player)).forEach(p -> playerSuggestions.add(p.getName()));
                        } else if (playerGuild != null) {
                            playerGuild.getMembers().stream().filter(uuid -> !uuid.equals(player.getUniqueId()) || subCmd.equals("promote") || subCmd.equals("demote")).map(Bukkit::getOfflinePlayer).filter(op -> op != null && op.getName() != null).forEach(op -> playerSuggestions.add(op.getName()));
                        }
                        StringUtil.copyPartialMatches(args[1], playerSuggestions, completions);
                    }
                    break;
                case "join": case "info":
                    if (args.length == 2) {
                        List<String> guildSuggestions = new ArrayList<>(guildManager.getAllGuilds().keySet());
                        if ("join".equals(subCmd) && guildManager.getInvitationGuildName(player.getUniqueId()) != null) {
                            guildSuggestions.clear();
                            guildSuggestions.add(guildManager.getInvitationGuildName(player.getUniqueId()));
                        }
                        StringUtil.copyPartialMatches(args[1], guildSuggestions, completions);
                    }
                    break;
                case "outpost":
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], Arrays.asList("create", "destroy", "status"), completions);
                    } else if (args.length == 3 && (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("destroy"))) {
                        StringUtil.copyPartialMatches(args[2], Arrays.stream(OutpostType.values()).map(OutpostType::name).collect(Collectors.toList()), completions);
                    }
                    break;
                case "war":
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], Arrays.asList("declare", "peace", "list", "score", "contest", "neutralizechunk"), completions);
                    } else if (args.length == 3 && (args[1].equalsIgnoreCase("declare") || args[1].equalsIgnoreCase("peace") || args[1].equalsIgnoreCase("score"))) {
                        List<String> potentialTargets = new ArrayList<>();
                        String playerGuildName = (playerGuild != null) ? playerGuild.getName() : "";
                        if (args[1].equalsIgnoreCase("score") && playerGuild != null) {
                            potentialTargets.addAll(guildManager.getWarsForGuild(playerGuildName));
                        } else {
                            guildManager.getAllGuilds().keySet().stream().filter(gn -> !gn.equalsIgnoreCase(playerGuildName)).forEach(potentialTargets::add);
                        }
                        StringUtil.copyPartialMatches(args[2], potentialTargets, completions);
                    }
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
