package com.wdp.help.context;

/**
 * Represents a context file with its metadata and content
 */
public class ContextFile {
    
    private final String name;
    private final String title;
    private final String content;
    private final boolean includedByDefault;
    private final int priority;
    private final String description;
    
    public ContextFile(String name, String title, String content, boolean includedByDefault, int priority, String description) {
        this.name = name;
        this.title = title;
        this.content = content;
        this.includedByDefault = includedByDefault;
        this.priority = priority;
        this.description = description;
    }
    
    /**
     * Get the file name (without extension)
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the display title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Get the content (markdown/plain text)
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Check if this file should be included in default context
     */
    public boolean isIncludedByDefault() {
        return includedByDefault;
    }
    
    /**
     * Get the priority (lower = higher priority)
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * Get the description
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return "ContextFile{name='" + name + "', title='" + title + "', default=" + includedByDefault + ", priority=" + priority + "}";
    }
}
