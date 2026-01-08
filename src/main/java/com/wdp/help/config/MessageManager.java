package com.wdp.help.config;

import com.wdp.help.WDPHelpPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Message manager for WDP-Help
 * Handles all player-facing messages with hex color support
 */
public class MessageManager {
    
    private final WDPHelpPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    
    // Cached values
    private String prefix;
    private List<String> thinkingMessages;
    
    public MessageManager(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    /**
     * Load messages from file
     */
    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Load default values from plugin resources
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            messages.setDefaults(defaultConfig);
        }
        
        // Cache commonly used values
        prefix = messages.getString("prefix", "&#FFD700[Help]&#FFFFFF ");
        thinkingMessages = messages.getStringList("thinking-messages");
        if (thinkingMessages.isEmpty()) {
            thinkingMessages = new ArrayList<>();
            thinkingMessages.add("&#AAAAAAThinking...");
        }
    }
    
    /**
     * Reload messages from file
     */
    public void reload() {
        loadMessages();
    }
    
    /**
     * Get a message by key
     */
    public String get(String key) {
        String message = messages.getString(key, "Message not found: " + key);
        return WDPHelpPlugin.translateHexColors(message);
    }
    
    /**
     * Get a message with placeholders replaced
     */
    public String get(String key, Object... replacements) {
        String message = get(key);
        
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                String placeholder = "{" + replacements[i] + "}";
                String value = String.valueOf(replacements[i + 1]);
                message = message.replace(placeholder, value);
            }
        }
        
        return message;
    }
    
    /**
     * Get a list of messages
     */
    public List<String> getList(String key) {
        List<String> list = messages.getStringList(key);
        List<String> translated = new ArrayList<>();
        
        for (String line : list) {
            translated.add(WDPHelpPlugin.translateHexColors(line));
        }
        
        return translated;
    }
    
    /**
     * Get message with prefix
     */
    public String getPrefixed(String key) {
        return WDPHelpPlugin.translateHexColors(prefix) + get(key);
    }
    
    /**
     * Get message with prefix and placeholders
     */
    public String getPrefixed(String key, Object... replacements) {
        return WDPHelpPlugin.translateHexColors(prefix) + get(key, replacements);
    }
    
    /**
     * Get the prefix
     */
    public String getPrefix() {
        return WDPHelpPlugin.translateHexColors(prefix);
    }
    
    /**
     * Get thinking messages list
     */
    public List<String> getThinkingMessages() {
        List<String> translated = new ArrayList<>();
        for (String msg : thinkingMessages) {
            translated.add(WDPHelpPlugin.translateHexColors(msg));
        }
        return translated;
    }
    
    /**
     * Get a random thinking message
     */
    public String getRandomThinkingMessage() {
        if (thinkingMessages.isEmpty()) {
            return WDPHelpPlugin.translateHexColors("&#AAAAAAThinking...");
        }
        int index = (int) (Math.random() * thinkingMessages.size());
        return WDPHelpPlugin.translateHexColors(thinkingMessages.get(index));
    }
}
