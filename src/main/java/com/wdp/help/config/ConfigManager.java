package com.wdp.help.config;

import com.wdp.help.WDPHelpPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration manager for WDP-Help
 */
public class ConfigManager {
    
    private final WDPHelpPlugin plugin;
    
    // AI Settings
    private String baseUrl;
    private String apiKey;
    private String model;
    private boolean openRouterEnabled;
    private String siteUrl;
    private String siteTitle;
    private int maxTokens;
    private double temperature;
    private int timeout;
    private boolean streamEnabled;
    
    // Context Settings
    private String contextDirectory;
    private int maxContextLength;
    private int historyCount;
    private int suggestHelpAfter;
    private int relevanceThreshold;
    
    // Display Settings
    private String header;
    private String footer;
    private String aiPrefix;
    private String thinkingInitial;
    private String[] dotsPattern;
    private int animationSpeed;
    private int messageDelay;
    private int messageInterval;
    private String toolPrefix;
    private String toolSuffix;
    
    // Debug
    private boolean debugEnabled;
    private boolean logRequests;
    private boolean logResponses;
    
    // Thread Settings
    private boolean threadWarningEnabled;
    private int maxThreads;
    private int threadQueueSize;
    
    public ConfigManager(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    
    /**
     * Reload configuration from file
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        
        // AI Settings
        baseUrl = config.getString("ai.base-url", "https://openrouter.ai/api/v1");
        apiKey = config.getString("ai.api-key", "YOUR_API_KEY_HERE");
        model = config.getString("ai.model", "openai/gpt-4o-mini");
        openRouterEnabled = config.getBoolean("ai.openrouter.enabled", true);
        siteUrl = config.getString("ai.openrouter.site-url", "https://wdpserver.com");
        siteTitle = config.getString("ai.openrouter.site-title", "WDP-Server");
        maxTokens = config.getInt("ai.request.max-tokens", 1024);
        temperature = config.getDouble("ai.request.temperature", 0.7);
        timeout = config.getInt("ai.request.timeout", 30);
        streamEnabled = config.getBoolean("ai.request.stream", true);
        
        // Context Settings
        contextDirectory = config.getString("context.directory", "context");
        maxContextLength = config.getInt("context.max-length", 15000);
        historyCount = config.getInt("context.history.count", 5);
        suggestHelpAfter = config.getInt("context.history.suggest-help-after", 3);
        relevanceThreshold = config.getInt("context.history.relevance-threshold", 6);
        
        // Display Settings
        header = config.getString("display.header", "&#FFD700━━━━ &#FFFFFF&lWDP Help &#FFD700━━━━");
        footer = config.getString("display.footer", "&#FFD700━━━━━━━━━━━━━━━━━━━━");
        aiPrefix = config.getString("display.ai-prefix", "&#55FFFF");
        thinkingInitial = config.getString("display.thinking.initial", "&#AAAAAA● &#FFFFFFThinking...");
        dotsPattern = config.getStringList("display.thinking.dots-pattern").toArray(new String[0]);
        if (dotsPattern.length == 0) {
            dotsPattern = new String[]{"&#FFD700● &#AAAAAA○ ○", "&#AAAAAA○ &#FFD700● &#AAAAAA○", "&#AAAAAA○ ○ &#FFD700●"};
        }
        animationSpeed = config.getInt("display.thinking.animation-speed", 5);
        messageDelay = config.getInt("display.thinking.message-delay", 5);
        messageInterval = config.getInt("display.thinking.message-interval", 3);
        toolPrefix = config.getString("display.tool.prefix", "&#FFAA00⚡ ");
        toolSuffix = config.getString("display.tool.suffix", "");
        
        // Debug
        debugEnabled = config.getBoolean("debug.enabled", false);
        logRequests = config.getBoolean("debug.log-requests", false);
        logResponses = config.getBoolean("debug.log-responses", false);
        
        // Thread Settings
        threadWarningEnabled = config.getBoolean("thread.warning-enabled", true);
        maxThreads = config.getInt("thread.max-threads", 10);
        threadQueueSize = config.getInt("thread.queue-size", 50);
    }
    
    /**
     * Build HTTP headers for API requests
     */
    public Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        
        if (openRouterEnabled && baseUrl.contains("openrouter")) {
            headers.put("HTTP-Referer", siteUrl);
            headers.put("X-Title", siteTitle);
        }
        
        return headers;
    }
    
    /**
     * Check if API key is configured
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE");
    }
    
    // ============ Getters ============
    
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public boolean isOpenRouterEnabled() { return openRouterEnabled; }
    public String getSiteUrl() { return siteUrl; }
    public String getSiteTitle() { return siteTitle; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public int getTimeout() { return timeout; }
    public boolean isStreamEnabled() { return streamEnabled; }
    
    public String getContextDirectory() { return contextDirectory; }
    public int getMaxContextLength() { return maxContextLength; }
    public int getHistoryCount() { return historyCount; }
    public int getSuggestHelpAfter() { return suggestHelpAfter; }
    public int getRelevanceThreshold() { return relevanceThreshold; }
    
    public String getHeader() { return header; }
    public String getFooter() { return footer; }
    public String getAiPrefix() { return aiPrefix; }
    public String getThinkingInitial() { return thinkingInitial; }
    public String[] getDotsPattern() { return dotsPattern; }
    public int getAnimationSpeed() { return animationSpeed; }
    public int getMessageDelay() { return messageDelay; }
    public int getMessageInterval() { return messageInterval; }
    public String getToolPrefix() { return toolPrefix; }
    public String getToolSuffix() { return toolSuffix; }
    
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isLogRequests() { return logRequests; }
    public boolean isLogResponses() { return logResponses; }
    
    public boolean isThreadWarningEnabled() { return threadWarningEnabled; }
    public int getMaxThreads() { return maxThreads; }
    public int getThreadQueueSize() { return threadQueueSize; }
}
