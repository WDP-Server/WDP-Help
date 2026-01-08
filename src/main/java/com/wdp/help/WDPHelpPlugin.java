package com.wdp.help;

import com.wdp.help.ai.AIService;
import com.wdp.help.command.HelpCommand;
import com.wdp.help.config.ConfigManager;
import com.wdp.help.config.MessageManager;
import com.wdp.help.context.ContextManager;
import com.wdp.help.data.PlayerDataManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WDP-Help - AI-Powered Help System for WDP Server
 * Uses OpenRouter/OpenAI compatible APIs to answer player questions
 * 
 * @author WDP Development Team
 * @version 1.0.0
 */
public class WDPHelpPlugin extends JavaPlugin {
    
    private static WDPHelpPlugin instance;
    
    // Managers
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ContextManager contextManager;
    private PlayerDataManager playerDataManager;
    private AIService aiService;
    
    // Hex color pattern
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    
    @Override
    public void onEnable() {
        instance = this;
        
        // ASCII Art Banner
        getLogger().info("");
        getLogger().info("╔═══════════════════════════════════════╗");
        getLogger().info("║         WDP-Help v" + getDescription().getVersion() + "             ║");
        getLogger().info("║     AI-Powered Help System            ║");
        getLogger().info("╚═══════════════════════════════════════╝");
        getLogger().info("");
        
        // Initialize managers
        initializeManagers();
        
        // Extract default context files
        extractContextFiles();
        
        // Register commands
        registerCommands();
        
        getLogger().info("WDP-Help has been enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        // Save player data
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        
        // Shutdown AI service
        if (aiService != null) {
            aiService.shutdown();
        }
        
        getLogger().info("WDP-Help has been disabled.");
    }
    
    private void initializeManagers() {
        // Config
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        
        // Messages
        messageManager = new MessageManager(this);
        
        // Context
        contextManager = new ContextManager(this);
        
        // Player data
        playerDataManager = new PlayerDataManager(this);
        
        // AI Service
        aiService = new AIService(this);
        
        getLogger().info("All managers initialized.");
    }
    
    private void extractContextFiles() {
        // Extract context files if they don't exist
        String[] contextFiles = {
            "context/teleport-commands.yml",
            "context/economy-commands.yml",
            "context/discord-integration.yml",
            "context/home-system.yml",
            "context/skills-system.yml",
            "context/quests-system.yml",
            "context/mechanics-wanderer.yml",
            "context/mechanics-bases.yml",
            "context/server-info.yml"
        };
        
        for (String file : contextFiles) {
            java.io.File f = new java.io.File(getDataFolder(), file);
            if (!f.exists()) {
                saveResource(file, false);
                getLogger().info("Extracted context file: " + file);
            }
        }
        
        // Reload context after extraction
        contextManager.loadContextFiles();
    }
    
    private void registerCommands() {
        HelpCommand helpCommand = new HelpCommand(this);
        getCommand("help").setExecutor(helpCommand);
        getCommand("help").setTabCompleter(helpCommand);
        getLogger().info("Commands registered.");
    }
    
    /**
     * Reload the plugin configuration
     */
    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
        contextManager.loadContextFiles();
        aiService.reload();
        getLogger().info("Configuration reloaded.");
    }
    
    // ============ Getters ============
    
    public static WDPHelpPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public ContextManager getContextManager() {
        return contextManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public AIService getAIService() {
        return aiService;
    }
    
    // ============ Utility Methods ============
    
    /**
     * Translate hex colors in a string
     * Supports &#RRGGBB format
     */
    public static String translateHexColors(String message) {
        if (message == null) return "";
        
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(builder, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(builder);
        
        // Also translate legacy color codes
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
