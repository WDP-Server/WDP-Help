package com.wdp.help.display;

import com.wdp.help.WDPHelpPlugin;
import com.wdp.help.config.ConfigManager;
import com.wdp.help.config.MessageManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles the chat display for AI responses
 * Features:
 * - Live streaming text display
 * - Animated thinking indicator
 * - Rotating waiting messages
 * - Tool usage display
 * - Clean scrolling view
 */
public class ChatDisplay {
    
    private final WDPHelpPlugin plugin;
    private final Player player;
    private final ConfigManager config;
    private final MessageManager messages;
    private final Random random;
    
    // Display state
    private final StringBuilder currentText;
    private final List<String> displayedLines;
    private boolean isThinking;
    private boolean hasStartedReceiving;
    private String lastThinkingMessage;
    
    // Animation tasks
    private BukkitTask thinkingTask;
    private BukkitTask messageRotationTask;
    
    // Animation state
    private final AtomicInteger dotPosition;
    private final AtomicInteger messageIndex;
    private final AtomicLong startTime;
    
    // Display constants
    private static final int CLEAR_LINES = 15; // Lines of blank space above content
    private static final int MAX_LINE_LENGTH = 50; // Max chars per line for wrapping
    private static final Pattern COMMAND_PATTERN = Pattern.compile("(/[a-zA-Z0-9_]+(?:\\s+[a-zA-Z0-9_]+)?)"); // Matches /command or /command arg
    
    public ChatDisplay(WDPHelpPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.random = new Random();
        
        this.currentText = new StringBuilder();
        this.displayedLines = new ArrayList<>();
        this.isThinking = false;
        this.hasStartedReceiving = false;
        this.lastThinkingMessage = "";
        
        this.dotPosition = new AtomicInteger(0);
        this.messageIndex = new AtomicInteger(0);
        this.startTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * Show the header
     */
    public void showHeader() {
        player.sendMessage("");
        player.sendMessage(WDPHelpPlugin.translateHexColors(config.getHeader()));
        player.sendMessage("");
    }
    
    /**
     * Show the footer
     */
    public void showFooter() {
        player.sendMessage("");
        player.sendMessage(WDPHelpPlugin.translateHexColors(config.getFooter()));
        player.sendMessage("");
    }
    
    /**
     * Start the thinking animation
     */
    public void startThinkingAnimation() {
        isThinking = true;
        startTime.set(System.currentTimeMillis());
        
        // Initial thinking message
        sendThinkingLine();
        
        // Start dot animation
        thinkingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isThinking || hasStartedReceiving) {
                return;
            }
            
            // Update dot position
            dotPosition.set((dotPosition.get() + 1) % config.getDotsPattern().length);
            
            // Refresh thinking display
            sendThinkingLine();
        }, config.getAnimationSpeed(), config.getAnimationSpeed());
        
        // Start message rotation after delay
        int delayTicks = config.getMessageDelay() * 20;
        int intervalTicks = config.getMessageInterval() * 20;
        
        messageRotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isThinking || hasStartedReceiving) {
                return;
            }
            
            long elapsed = System.currentTimeMillis() - startTime.get();
            if (elapsed < config.getMessageDelay() * 1000L) {
                return; // Still in initial delay
            }
            
            // Pick random message
            List<String> thinkingMsgs = messages.getThinkingMessages();
            if (!thinkingMsgs.isEmpty()) {
                messageIndex.set(random.nextInt(thinkingMsgs.size()));
                sendThinkingLine();
            }
        }, delayTicks, intervalTicks);
    }
    
    /**
     * Send the current thinking line (clear screen each frame for smooth animation)
     */
    private void sendThinkingLine() {
        if (!isThinking || player == null || !player.isOnline()) {
            return;
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player == null || !player.isOnline()) {
                return;
            }
            
            // Clear screen with blank lines
            for (int i = 0; i < CLEAR_LINES; i++) {
                player.sendMessage("");
            }
            
            // Show header
            player.sendMessage(WDPHelpPlugin.translateHexColors(config.getHeader()));
            player.sendMessage("");
            
            // Build thinking message
            String[] patterns = config.getDotsPattern();
            String dots = patterns[dotPosition.get() % patterns.length];
            
            long elapsed = System.currentTimeMillis() - startTime.get();
            String message;
            
            if (elapsed < config.getMessageDelay() * 1000L) {
                // Still in initial phase - just show dots
                message = WDPHelpPlugin.translateHexColors(dots + " " + config.getThinkingInitial().replace("&#AAAAAA● &#FFFFFF", ""));
            } else {
                // Show rotating message with dots
                List<String> thinkingMsgs = messages.getThinkingMessages();
                String statusMsg = thinkingMsgs.isEmpty() ? "Thinking..." : thinkingMsgs.get(messageIndex.get() % thinkingMsgs.size());
                message = WDPHelpPlugin.translateHexColors(dots + " " + statusMsg);
            }
            
            // Send thinking message
            player.sendMessage(message);
            lastThinkingMessage = message;
        });
    }
    
    
    /**
     * Stop the thinking animation
     */
    public void stopThinkingAnimation() {
        isThinking = false;
        
        if (thinkingTask != null) {
            thinkingTask.cancel();
            thinkingTask = null;
        }
        
        if (messageRotationTask != null) {
            messageRotationTask.cancel();
            messageRotationTask = null;
        }
        
        lastThinkingMessage = "";
    }
    
    /**
     * Append text to the current response
     */
    public void appendText(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        
        // Mark that we've started receiving
        if (!hasStartedReceiving) {
            hasStartedReceiving = true;
            // Clear screen to remove thinking animation
            clear();
            // Show header again
            showHeader();
        }
        
        // Append to current text
        currentText.append(chunk);
        
        // Send the text to player immediately with clickable commands
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player != null && player.isOnline()) {
                // Handle newlines first - split by actual newlines
                String[] lines = chunk.split("\\\\n");
                for (String line : lines) {
                    // Prepend white color code to maintain text color across newlines
                    line = "§f" + line;
                    // Word wrap each line if needed
                    List<String> wrappedLines = wordWrap(line, MAX_LINE_LENGTH);
                    for (String wrappedLine : wrappedLines) {
                        sendClickableMessage(wrappedLine);
                    }
                }
            }
        });
    }
    
    /**
     * Send a message with clickable commands and color codes
     */
    private void sendClickableMessage(String text) {
        if (text.isEmpty()) {
            // Empty line - send blank for spacing
            player.sendMessage("");
            return;
        }
        
        Matcher matcher = COMMAND_PATTERN.matcher(text);
        
        if (!matcher.find()) {
            // No commands found, send as regular message with color codes
            String colored = text.replace("§", "&"); // Convert § to & for color translation
            String prefixNoColor = WDPHelpPlugin.translateHexColors(config.getAiPrefix()).replaceAll("§[0-9a-f]", ""); // Strip color from prefix
            player.sendMessage(prefixNoColor + colored);
            return;
        }
        
        // Build clickable text component
        matcher.reset();
        String colored = text.replace("§", "&"); // Convert § to & for color translation
        String prefixNoColor = WDPHelpPlugin.translateHexColors(config.getAiPrefix()).replaceAll("§[0-9a-f]", ""); // Strip color from prefix
        TextComponent message = new TextComponent(prefixNoColor);
        
        int lastEnd = 0;
        while (matcher.find()) {
            // Add text before command (with colors)
            if (matcher.start() > lastEnd) {
                String beforeText = colored.substring(lastEnd, matcher.start());
                message.addExtra(new TextComponent(WDPHelpPlugin.translateHexColors(beforeText)));
            }
            
            // Add clickable command
            String command = matcher.group(1);
            TextComponent commandComponent = new TextComponent(command);
            commandComponent.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
            commandComponent.setBold(true);
            commandComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
            commandComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text("Click to use: " + command)));
            
            message.addExtra(commandComponent);
            lastEnd = matcher.end();
        }
        
        // Add remaining text (with colors)
        if (lastEnd < colored.length()) {
            String remainingText = colored.substring(lastEnd);
            message.addExtra(new TextComponent(WDPHelpPlugin.translateHexColors(remainingText)));
        }
        
        player.spigot().sendMessage(message);
    }
    
    /**
     * Show a tool usage message
     */
    public void showToolMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player != null && player.isOnline()) {
                String displayMsg = config.getToolPrefix() + message + config.getToolSuffix();
                player.sendMessage(WDPHelpPlugin.translateHexColors(displayMsg));
            }
        });
    }
    
    /**
     * Complete the display
     */
    public void complete() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player != null && player.isOnline()) {
                player.sendMessage("");
            }
        });
    }
    
    /**
     * Word wrap text to specified width
     */
    private List<String> wordWrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        
        // Split by existing newlines first
        String[] paragraphs = text.split("\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                lines.add("");
                continue;
            }
            
            // Wrap long lines
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();
            
            for (String word : words) {
                if (currentLine.length() + word.length() + 1 > maxWidth) {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString().trim());
                        currentLine = new StringBuilder();
                    }
                }
                
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
            
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString().trim());
            }
        }
        
        return lines;
    }
    
    /**
     * Clear the display (send blank lines)
     */
    public void clear() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player != null && player.isOnline()) {
                for (int i = 0; i < CLEAR_LINES; i++) {
                    player.sendMessage("");
                }
            }
        });
    }
}
