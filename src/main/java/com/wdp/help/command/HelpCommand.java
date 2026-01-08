package com.wdp.help.command;

import com.wdp.help.WDPHelpPlugin;
import com.wdp.help.config.ConfigManager;
import com.wdp.help.config.MessageManager;
import com.wdp.help.data.HelpAnswer;
import com.wdp.help.data.PlayerHelpData;
import com.wdp.help.display.ChatDisplay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Main help command handler
 * /help - Shows recent answers and info
 * /help [question] - Asks the AI
 * /help reload - Reloads config (admin)
 */
public class HelpCommand implements CommandExecutor, TabCompleter {
    
    private final WDPHelpPlugin plugin;
    private final MessageManager messages;
    private final ConfigManager config;
    
    // Track active help sessions to prevent spam
    private final Set<UUID> activeSessions = Collections.synchronizedSet(new HashSet<>());
    
    public HelpCommand(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.config = plugin.getConfigManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if player
        if (!(sender instanceof Player)) {
            sender.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("error.player-only")));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permission
        if (!player.hasPermission("wdphelp.use")) {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("error.no-permission")));
            return true;
        }
        
        // Handle subcommands
        if (args.length >= 1) {
            String firstArg = args[0].toLowerCase();
            
            // Admin commands
            if (firstArg.equals("reload") && player.hasPermission("wdphelp.admin.reload")) {
                handleReload(player);
                return true;
            }
            
            if (firstArg.equals("debug") && player.hasPermission("wdphelp.admin.debug")) {
                handleDebug(player);
                return true;
            }
            
            // Otherwise, treat as a question
            String question = String.join(" ", args);
            handleQuestion(player, question);
            return true;
        }
        
        // No args - show help menu
        showHelpMenu(player);
        return true;
    }
    
    /**
     * Show the help menu with recent answers
     */
    private void showHelpMenu(Player player) {
        player.sendMessage("");
        player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.menu.header")));
        player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.menu.description")));
        player.sendMessage("");
        
        // Show recent answers (deduplicated)
        PlayerHelpData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        if (data.hasRecentAnswers()) {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.menu.recent-header")));
            
            // Track seen questions to mark duplicates
            java.util.Set<Integer> seenHashes = new java.util.HashSet<>();
            
            for (HelpAnswer answer : data.getRecentAnswers()) {
                boolean isDuplicate = !seenHashes.add(answer.getQuestionHash());
                
                String item = messages.get("help.menu.recent-item", "title", answer.getTitle());
                
                // Add duplicate indicator
                if (isDuplicate) {
                    item = item + " &#888888(asked again)";
                }
                
                player.sendMessage(WDPHelpPlugin.translateHexColors(item));
                
                // Show short description under title
                String shortDesc = messages.get("help.menu.recent-short", "short", answer.getShortDescription());
                player.sendMessage(WDPHelpPlugin.translateHexColors(shortDesc));
            }
        } else {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.menu.no-recent")));
        }
        
        player.sendMessage("");
        player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.menu.footer")));
        player.sendMessage("");
    }
    
    /**
     * Handle a question to the AI
     */
    private void handleQuestion(Player player, String question) {
        UUID uuid = player.getUniqueId();
        
        // Check if already processing
        if (activeSessions.contains(uuid)) {
            player.sendMessage(WDPHelpPlugin.translateHexColors("&#FFAA00Please wait for your current question to be answered!"));
            return;
        }
        
        // Check if API is configured
        if (!config.isApiKeyConfigured()) {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("error.invalid-key")));
            return;
        }
        
        // Check if should suggest using /help
        if (plugin.getPlayerDataManager().shouldSuggestHelp(uuid, question)) {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.get("help.repeat-tip")));
        }
        
        // Mark session as active
        activeSessions.add(uuid);
        
        // Create display handler
        ChatDisplay display = new ChatDisplay(plugin, player);
        
        // Show header
        display.showHeader();
        
        // Start thinking animation
        display.startThinkingAnimation();
        
        // Send question to AI
        plugin.getAIService().askQuestion(
                uuid,
                question,
                // On chunk received
                (chunk) -> {
                    display.appendText(chunk);
                },
                // On tool use
                (toolMessage) -> {
                    display.showToolMessage(toolMessage);
                },
                // On complete
                (response) -> {
                    // Stop thinking animation
                    display.stopThinkingAnimation();
                    
                    // Complete display
                    display.complete();
                    
                    // Show footer
                    display.showFooter();
                    
                    // Check relevance score before saving
                    int minRelevance = config.getRelevanceThreshold();
                    if (response.getRelevanceScore() >= minRelevance) {
                        // Save answer to player data
                        plugin.getPlayerDataManager().addAnswer(
                                uuid,
                                question,
                                response.getDisplayText(),
                                response.getShortDescription(),
                                response.getTitle()
                        );
                    } else if (config.isDebugEnabled()) {
                        // Log low relevance in debug mode
                        plugin.getLogger().info("Question from " + player.getName() + " had low relevance (" + 
                                response.getRelevanceScore() + "/10): " + question);
                    }
                    
                    // Remove from active sessions
                    activeSessions.remove(uuid);
                },
                // On error
                (error) -> {
                    display.stopThinkingAnimation();
                    
                    // Show error message
                    String errorMsg;
                    if (error.startsWith("error.")) {
                        errorMsg = messages.get(error);
                    } else {
                        errorMsg = messages.get("error.api-error");
                    }
                    player.sendMessage(WDPHelpPlugin.translateHexColors(errorMsg));
                    
                    // Remove from active sessions
                    activeSessions.remove(uuid);
                }
        );
    }
    
    /**
     * Handle reload command
     */
    private void handleReload(Player player) {
        try {
            plugin.reload();
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.getPrefixed("admin.reload-success")));
        } catch (Exception e) {
            player.sendMessage(WDPHelpPlugin.translateHexColors(messages.getPrefixed("admin.reload-fail")));
            plugin.getLogger().severe("Reload failed: " + e.getMessage());
        }
    }
    
    /**
     * Handle debug command
     */
    private void handleDebug(Player player) {
        player.sendMessage("");
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#FFD700━━━━ &#FFFFFF&lWDP-Help Debug &#FFD700━━━━"));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• API URL: &#FFFFFF" + config.getBaseUrl()));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• Model: &#FFFFFF" + config.getModel()));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• API Key: &#FFFFFF" + (config.isApiKeyConfigured() ? "Configured" : "NOT SET")));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• OpenRouter: &#FFFFFF" + (config.isOpenRouterEnabled() ? "Enabled" : "Disabled")));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• Streaming: &#FFFFFF" + (config.isStreamEnabled() ? "Enabled" : "Disabled")));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• Context Files: &#FFFFFF" + plugin.getContextManager().getAllContextFiles().size()));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• Default Context: &#FFFFFF" + plugin.getContextManager().getDefaultContextFiles().size()));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#AAAAAA• Extra Context: &#FFFFFF" + plugin.getContextManager().getExtraContextNames().size()));
        player.sendMessage(WDPHelpPlugin.translateHexColors("&#FFD700━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            
            // Admin commands
            if (sender.hasPermission("wdphelp.admin.reload")) {
                if ("reload".startsWith(partial)) completions.add("reload");
            }
            if (sender.hasPermission("wdphelp.admin.debug")) {
                if ("debug".startsWith(partial)) completions.add("debug");
            }
            
            // Suggest common questions
            List<String> suggestions = Arrays.asList(
                    "how do I teleport",
                    "how to get money",
                    "what are skills",
                    "how to link discord",
                    "how to set home",
                    "what is the wanderer"
            );
            
            for (String suggestion : suggestions) {
                if (suggestion.toLowerCase().startsWith(partial)) {
                    completions.add(suggestion);
                }
            }
        }
        
        return completions;
    }
}
