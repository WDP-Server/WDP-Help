package com.wdp.help.context;

import com.wdp.help.WDPHelpPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages context files for the AI
 * Context files have YAML headers with settings, followed by plain text content
 */
public class ContextManager {
    
    private final WDPHelpPlugin plugin;
    private final Map<String, ContextFile> contextFiles;
    
    public ContextManager(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        this.contextFiles = new HashMap<>();
        loadContextFiles();
    }
    
    /**
     * Load all context files from the context directory
     */
    public void loadContextFiles() {
        contextFiles.clear();
        
        File contextDir = new File(plugin.getDataFolder(), plugin.getConfigManager().getContextDirectory());
        if (!contextDir.exists()) {
            contextDir.mkdirs();
            return;
        }
        
        File[] files = contextDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;
        
        for (File file : files) {
            try {
                ContextFile contextFile = parseContextFile(file);
                if (contextFile != null) {
                    contextFiles.put(contextFile.getName(), contextFile);
                    if (plugin.getConfigManager().isDebugEnabled()) {
                        plugin.getLogger().info("Loaded context file: " + contextFile.getName() + 
                                " (default: " + contextFile.isIncludedByDefault() + ")");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load context file: " + file.getName() + " - " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded " + contextFiles.size() + " context files.");
    }
    
    /**
     * Parse a context file
     * Format:
     * ---
     * title: "Title Here"
     * included-by-default: true
     * priority: 1
     * description: "Brief description"
     * ---
     * 
     * # Markdown content below
     * Plain text content with markdown formatting...
     */
    private ContextFile parseContextFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        StringBuilder yamlHeader = new StringBuilder();
        boolean inHeader = false;
        boolean headerComplete = false;
        int headerDelimiterCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    headerDelimiterCount++;
                    if (headerDelimiterCount == 1) {
                        inHeader = true;
                        continue;
                    } else if (headerDelimiterCount == 2) {
                        inHeader = false;
                        headerComplete = true;
                        continue;
                    }
                }
                
                if (inHeader) {
                    yamlHeader.append(line).append("\n");
                } else if (headerComplete) {
                    content.append(line).append("\n");
                } else {
                    // No header, treat entire file as content
                    content.append(line).append("\n");
                }
            }
        }
        
        // Parse YAML header
        String title = file.getName().replace(".yml", "").replace(".yaml", "");
        boolean includedByDefault = false;
        int priority = 10;
        String description = "";
        
        if (yamlHeader.length() > 0) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(yamlHeader.toString());
                
                title = yaml.getString("title", title);
                includedByDefault = yaml.getBoolean("included-by-default", false);
                priority = yaml.getInt("priority", 10);
                description = yaml.getString("description", "");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse YAML header in " + file.getName() + ": " + e.getMessage());
            }
        }
        
        String name = file.getName().replace(".yml", "").replace(".yaml", "");
        return new ContextFile(name, title, content.toString().trim(), includedByDefault, priority, description);
    }
    
    /**
     * Get all context files that should be included by default
     */
    public List<ContextFile> getDefaultContextFiles() {
        return contextFiles.values().stream()
                .filter(ContextFile::isIncludedByDefault)
                .sorted((a, b) -> a.getPriority() - b.getPriority())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all context files (including extra)
     */
    public List<ContextFile> getAllContextFiles() {
        return new ArrayList<>(contextFiles.values());
    }
    
    /**
     * Get names of extra context files (not included by default)
     */
    public List<String> getExtraContextNames() {
        return contextFiles.values().stream()
                .filter(f -> !f.isIncludedByDefault())
                .map(ContextFile::getName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific context file by name
     */
    public ContextFile getContextFile(String name) {
        return contextFiles.get(name);
    }
    
    /**
     * Get combined context content for default files
     */
    public String getDefaultContext() {
        StringBuilder sb = new StringBuilder();
        for (ContextFile file : getDefaultContextFiles()) {
            sb.append("### ").append(file.getTitle()).append(" ###\n");
            sb.append(file.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
