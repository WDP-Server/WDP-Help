package com.wdp.help.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wdp.help.WDPHelpPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player help data (recent questions, conversation history)
 */
public class PlayerDataManager {
    
    private final WDPHelpPlugin plugin;
    private final Gson gson;
    private final File dataFile;
    private final Map<UUID, PlayerHelpData> playerData;
    
    public PlayerDataManager(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "player_data.json");
        this.playerData = new HashMap<>();
        
        loadData();
    }
    
    /**
     * Load player data from file
     */
    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PlayerHelpData>>(){}.getType();
            Map<String, PlayerHelpData> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                for (Map.Entry<String, PlayerHelpData> entry : loaded.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        playerData.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        // Skip invalid UUIDs
                    }
                }
            }
            
            plugin.getLogger().info("Loaded data for " + playerData.size() + " players.");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load player data: " + e.getMessage());
        }
    }
    
    /**
     * Save all player data to file
     */
    public void saveAll() {
        try {
            // Convert UUID keys to strings for JSON
            Map<String, PlayerHelpData> toSave = new HashMap<>();
            for (Map.Entry<UUID, PlayerHelpData> entry : playerData.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(toSave, writer);
            }
            
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Saved data for " + playerData.size() + " players.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
        }
    }
    
    /**
     * Get player data, creating if necessary
     */
    public PlayerHelpData getData(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new PlayerHelpData());
    }
    
    /**
     * Add an answer to player's history
     */
    public void addAnswer(UUID uuid, String question, String answer, String shortDescription, String title) {
        PlayerHelpData data = getData(uuid);
        
        // Create new answer
        HelpAnswer helpAnswer = new HelpAnswer(question, answer, shortDescription, title, System.currentTimeMillis());
        
        // Add to history (will automatically maintain max size)
        data.addAnswer(helpAnswer, plugin.getConfigManager().getHistoryCount());
        
        // Track question frequency
        data.incrementQuestionCount(question.toLowerCase().trim());
        
        // Save data periodically (could be async in production)
        saveAll();
    }
    
    /**
     * Check if player has asked similar question multiple times
     */
    public int getQuestionCount(UUID uuid, String question) {
        PlayerHelpData data = getData(uuid);
        return data.getQuestionCount(question.toLowerCase().trim());
    }
    
    /**
     * Check if we should suggest using /help to see recent answers
     */
    public boolean shouldSuggestHelp(UUID uuid, String question) {
        int count = getQuestionCount(uuid, question);
        return count >= plugin.getConfigManager().getSuggestHelpAfter();
    }
}
