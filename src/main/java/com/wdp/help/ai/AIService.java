package com.wdp.help.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdp.help.WDPHelpPlugin;
import com.wdp.help.config.ConfigManager;
import com.wdp.help.context.ContextFile;
import com.wdp.help.data.HelpAnswer;
import com.wdp.help.data.PlayerHelpData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * AI Service for handling OpenRouter/OpenAI API requests
 * Supports streaming responses for real-time chat updates
 */
public class AIService {
    
    private final WDPHelpPlugin plugin;
    private final Gson gson;
    private ExecutorService executor;
    private int maxThreads;
    private int maxQueueSize;
    private boolean warningEnabled;
    
    private static final String SYSTEM_PROMPT = """
        You are a confident and knowledgeable helper for the WDP Minecraft Server. Answer player questions with authority and clarity.
        
        CRITICAL COMMUNICATION NOTE:
        Players CANNOT reply to your answers. This is a one-way help system. Never ask them to clarify, send messages, or provide feedback. 
        If something is unclear, make your best educated guess based on context and provide a complete answer.
        
        TONE & STYLE:
        1. Be BRIEF and DIRECT - get to the point quickly
        2. Use clear, simple language suitable for all English levels
        3. Be confident, not cautious or hedging
        4. NO tips, suggestions, or additional information sections
        5. NO disclaimers or caveats - just answer
        6. If you need more information, use the fetch_context tool
        
        ANSWER LENGTH:
        - Keep answers to 2-4 sentences maximum
        - One paragraph, maybe two for complex topics
        - Just the essential information the player needs
        
        RESPONSE STRUCTURE (MUST be valid JSON only):
        {
          "answer": "brief direct answer with light formatting",
          "short_description": "One-sentence summary (max 10 words)",
          "title": "3-5 word topic title",
          "relevance_score": 10
        }
        
        Relevance scoring:
        - 10 = WDP-specific topics (events, systems, commands)
        - 7-9 = Server gameplay mechanics
        - 4-6 = General Minecraft knowledge
        - 1-3 = Tangentially related
        - 0 = Completely off-topic
        
        FORMATTING - KEEP IT MINIMAL:
        - DO NOT use color codes. Plain text only.
        - For commands: just write /command (do not color them) and explain the arguments like /command§7[argument]§f
        - For numbered lists: use simple format like "1. First step\n2. Second step\n3. Third step"
        - Use \n only for actual line breaks between ideas
        - NO fancy formatting, NO colors, NO emphasis
        - The answer should be almost entirely plain text 
        
        DISCORD LINK NOTE:
        The player's Discord link status is provided for context only. ONLY mention they should link Discord if they ask about features that require it (like cross-server perms). Don't volunteer this info.
        
        EXAMPLE BRIEF ANSWER:
        "Use §6/hint§f to get your current clue for the Wanderer's location. Hints update daily at 15:00 server time."
        "1. use /start to begin your adventure. 2. Complete quests to earn rewards. 3. Join a team for multiplayer fun!"
        
        WRONG (too colorful, too long):
        "Use §6/hint§3 to get your current clue. §e Here are tips: §a bring gear§f, §a team up§f, §a check Discord§f. First link your Discord with §6/discord link§f for protection!"
        "1. use /start to begin your adventure. This command initializes your quest log and gives you your first task. 2. As you complete quests, you'll earn rewards like XP and items. 3. Consider joining a team to tackle harder challenges together. Don't forget to check our Discord server for events and updates!"

        SERVER CONTEXT BELOW:
        """;
    
    public AIService(WDPHelpPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        initializeExecutor();
    }
    
    private void initializeExecutor() {
        ConfigManager config = plugin.getConfigManager();
        warningEnabled = config.isThreadWarningEnabled();
        maxThreads = config.getMaxThreads();
        maxQueueSize = config.getThreadQueueSize();
        
        this.executor = new ThreadPoolExecutor(
            2, 
            maxThreads, 
            30L, 
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(maxQueueSize),
            (r, exec) -> {
                if (warningEnabled) {
                    plugin.getLogger().warning("[AI-SERVICE] Task queue full, dropping request. Active: " + 
                        ((ThreadPoolExecutor) exec).getActiveCount() + ", Queue: " + 
                        exec.getQueue().size());
                }
            }
        );
    }
    
    private void logThreadWarning(String operation) {
        if (!warningEnabled) return;
        
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        int active = tpe.getActiveCount();
        int queued = tpe.getQueue().size();
        double usagePercent = (double) active / maxThreads * 100;
        
        if (usagePercent >= 80) {
            plugin.getLogger().warning("[AI-SERVICE] High thread usage during " + operation + 
                ": active=" + active + "/" + maxThreads + " (" + String.format("%.1f", usagePercent) + 
                "%), queued=" + queued);
        }
    }
    
    public void reload() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        initializeExecutor();
    }
    
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    public CompletableFuture<Void> askQuestion(
            UUID playerUUID,
            String question,
            Consumer<String> onChunk,
            Consumer<String> onToolUse,
            Consumer<AIResponse> onComplete,
            Consumer<String> onError
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                logThreadWarning("askQuestion");
                ConfigManager config = plugin.getConfigManager();
                
                if (!config.isApiKeyConfigured()) {
                    onError.accept("API key not configured!");
                    return;
                }
                
                String context = buildContext(playerUUID);
                
                JsonArray messages = new JsonArray();
                
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", SYSTEM_PROMPT + "\n\n" + context);
                messages.add(systemMessage);
                
                PlayerHelpData playerData = plugin.getPlayerDataManager().getData(playerUUID);
                if (playerData != null) {
                    for (HelpAnswer answer : playerData.getRecentAnswers()) {
                        JsonObject userMsg = new JsonObject();
                        userMsg.addProperty("role", "user");
                        userMsg.addProperty("content", answer.getQuestion());
                        messages.add(userMsg);
                        
                        JsonObject assistantMsg = new JsonObject();
                        assistantMsg.addProperty("role", "assistant");
                        assistantMsg.addProperty("content", answer.getShortDescription());
                        messages.add(assistantMsg);
                    }
                }
                
                JsonObject userQuestion = new JsonObject();
                userQuestion.addProperty("role", "user");
                userQuestion.addProperty("content", question);
                messages.add(userQuestion);
                
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", config.getModel());
                requestBody.add("messages", messages);
                requestBody.addProperty("max_tokens", config.getMaxTokens());
                requestBody.addProperty("temperature", config.getTemperature());
                requestBody.addProperty("stream", config.isStreamEnabled());
                
                JsonObject responseFormat = new JsonObject();
                responseFormat.addProperty("type", "json_object");
                requestBody.add("response_format", responseFormat);
                
                JsonArray tools = buildTools();
                if (tools.size() > 0) {
                    requestBody.add("tools", tools);
                }
                
                if (config.isLogRequests()) {
                    plugin.getLogger().info("AI Request: " + gson.toJson(requestBody));
                }
                
                if (config.isStreamEnabled()) {
                    streamRequest(requestBody, onChunk, onToolUse, onComplete, onError);
                } else {
                    nonStreamRequest(requestBody, onChunk, onComplete, onError);
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("AI Service error: " + e.getMessage());
                e.printStackTrace();
                onError.accept("An error occurred: " + e.getMessage());
            }
        }, executor);
    }
    
    private String buildContext(UUID playerUUID) {
        StringBuilder context = new StringBuilder();
        
        context.append("=== Player Information ===\n");
        
        try {
            org.bukkit.plugin.Plugin discordPlugin = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
            if (discordPlugin != null && discordPlugin.isEnabled()) {
                Object accountLinkManager = discordPlugin.getClass().getMethod("getAccountLinkManager").invoke(discordPlugin);
                Object discordId = accountLinkManager.getClass().getMethod("getDiscordId", UUID.class).invoke(accountLinkManager, playerUUID);
                
                if (discordId != null) {
                    context.append("Discord Status: Linked (verified account)\n");
                } else {
                    context.append("Discord Status: Not linked (use /discord link to connect)\n");
                }
            }
        } catch (Exception e) {
        }
        
        context.append("\n");
        
        List<ContextFile> defaultFiles = plugin.getContextManager().getDefaultContextFiles();
        for (ContextFile file : defaultFiles) {
            context.append("=== ").append(file.getTitle()).append(" ===\n");
            context.append(file.getContent()).append("\n\n");
        }
        
        ConfigManager config = plugin.getConfigManager();
        if (context.length() > config.getMaxContextLength()) {
            context = new StringBuilder(context.substring(0, config.getMaxContextLength()));
            context.append("\n[Context truncated...]");
        }
        
        return context.toString();
    }
    
    private JsonArray buildTools() {
        JsonArray tools = new JsonArray();
        
        List<String> extraContextNames = plugin.getContextManager().getExtraContextNames();
        if (extraContextNames.isEmpty()) {
            return tools;
        }
        
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        
        JsonObject function = new JsonObject();
        function.addProperty("name", "fetch_context");
        function.addProperty("description", "Fetch additional context information about specific server features. Use this when you need more details to answer a question. Available contexts: " + String.join(", ", extraContextNames));
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        JsonObject contextNameProp = new JsonObject();
        contextNameProp.addProperty("type", "string");
        contextNameProp.addProperty("description", "The name of the context to fetch. Available: " + String.join(", ", extraContextNames));
        
        JsonArray enumValues = new JsonArray();
        for (String name : extraContextNames) {
            enumValues.add(name);
        }
        contextNameProp.add("enum", enumValues);
        
        properties.add("context_name", contextNameProp);
        parameters.add("properties", properties);
        
        JsonArray required = new JsonArray();
        required.add("context_name");
        parameters.add("required", required);
        
        function.add("parameters", parameters);
        tool.add("function", function);
        
        tools.add(tool);
        
        return tools;
    }
    
    private void streamRequest(
            JsonObject requestBody,
            Consumer<String> onChunk,
            Consumer<String> onToolUse,
            Consumer<AIResponse> onComplete,
            Consumer<String> onError
    ) throws IOException {
        ConfigManager config = plugin.getConfigManager();
        
        URL url = new URL(config.getBaseUrl() + "/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(config.getTimeout() * 1000);
            connection.setReadTimeout(config.getTimeout() * 1000);
            
            for (Map.Entry<String, String> header : config.buildHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readErrorStream(connection);
                handleApiError(responseCode, errorBody, onError);
                return;
            }
            
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder toolCallName = new StringBuilder();
            StringBuilder toolCallArgs = new StringBuilder();
            String toolCallId = null;
            boolean isToolCall = false;
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        
                        if (data.equals("[DONE]")) {
                            break;
                        }
                        
                        if (data.startsWith(": OPENROUTER")) {
                            continue;
                        }
                        
                        try {
                            JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                            
                            if (chunk.has("error")) {
                                String errorMsg = chunk.getAsJsonObject("error").get("message").getAsString();
                                onError.accept(errorMsg);
                                return;
                            }
                            
                            if (chunk.has("choices")) {
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices.size() > 0) {
                                    JsonObject choice = choices.get(0).getAsJsonObject();
                                    if (choice.has("delta")) {
                                        JsonObject delta = choice.getAsJsonObject("delta");
                                        
                                        if (delta.has("tool_calls")) {
                                            isToolCall = true;
                                            JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
                                            if (toolCalls.size() > 0) {
                                                JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
                                                
                                                if (toolCall.has("id")) {
                                                    toolCallId = toolCall.get("id").getAsString();
                                                }
                                                
                                                if (toolCall.has("function")) {
                                                    JsonObject function = toolCall.getAsJsonObject("function");
                                                    if (function.has("name")) {
                                                        toolCallName.append(function.get("name").getAsString());
                                                    }
                                                    if (function.has("arguments")) {
                                                        toolCallArgs.append(function.get("arguments").getAsString());
                                                    }
                                                }
                                            }
                                        }
                                        
                                        if (delta.has("content")) {
                                            String content = delta.get("content").getAsString();
                                            fullResponse.append(content);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            if (config.isDebugEnabled()) {
                                plugin.getLogger().warning("Malformed chunk: " + data);
                            }
                        }
                    }
                }
            }
            
            if (isToolCall && toolCallId != null) {
                String functionName = toolCallName.toString();
                String arguments = toolCallArgs.toString().trim();
                
                if (functionName.isEmpty()) {
                    plugin.getLogger().warning("Tool call detected but function name is empty");
                    onError.accept("error.api-error");
                    return;
                }
                
                if (!arguments.startsWith("{") || !arguments.endsWith("}")) {
                    plugin.getLogger().warning("Tool call arguments incomplete or malformed: " + arguments);
                    isToolCall = false;
                } else {
                    plugin.getLogger().info("[TOOL CALL] Function: " + functionName + " | Args: " + arguments);
                    
                    String[] funMessages = {
                        "Diving into my knowledge vault...",
                        "Searching the ancient scrolls...",
                        "Summoning extra information...",
                        "Checking the secret files...",
                        "Gathering more details...",
                        "Digging deeper for you..."
                    };
                    String toolMsg = funMessages[(int)(Math.random() * funMessages.length)];
                    onToolUse.accept(toolMsg);
                    plugin.getLogger().info("[TOOL MESSAGE] Sent to user: " + toolMsg);
                    
                    String toolResult = executeTool(functionName, arguments);
                    
                    JsonArray messages = requestBody.getAsJsonArray("messages");
                    
                    JsonObject assistantMessage = new JsonObject();
                    assistantMessage.addProperty("role", "assistant");
                    assistantMessage.addProperty("content", "");
                    
                    JsonArray toolCallsArray = new JsonArray();
                    JsonObject toolCallObj = new JsonObject();
                    toolCallObj.addProperty("id", toolCallId);
                    toolCallObj.addProperty("type", "function");
                    JsonObject functionObj = new JsonObject();
                    functionObj.addProperty("name", functionName);
                    functionObj.addProperty("arguments", arguments);
                    toolCallObj.add("function", functionObj);
                    toolCallsArray.add(toolCallObj);
                    assistantMessage.add("tool_calls", toolCallsArray);
                    
                    messages.add(assistantMessage);
                    
                    JsonObject toolMessage = new JsonObject();
                    toolMessage.addProperty("role", "tool");
                    toolMessage.addProperty("tool_call_id", toolCallId);
                    toolMessage.addProperty("content", toolResult);
                    messages.add(toolMessage);
                    
                    JsonObject newRequestBody = new JsonObject();
                    newRequestBody.addProperty("model", config.getModel());
                    newRequestBody.add("messages", messages);
                    newRequestBody.addProperty("max_tokens", config.getMaxTokens());
                    newRequestBody.addProperty("temperature", config.getTemperature());
                    newRequestBody.addProperty("stream", config.isStreamEnabled());
                    
                    JsonObject responseFormat = new JsonObject();
                    responseFormat.addProperty("type", "json_object");
                    newRequestBody.add("response_format", responseFormat);
                    
                    streamRequest(newRequestBody, onChunk, onToolUse, onComplete, onError);
                    return;
                }
            }
            
            String jsonResponse = fullResponse.toString().trim();
            
            if (config.isDebugEnabled()) {
                plugin.getLogger().info("AI Raw Response: " + jsonResponse);
            }
            
            String displayText = "";
            String shortDescription = "No summary available";
            String title = "Question";
            int relevanceScore = 10;
            
            if (jsonResponse.isEmpty()) {
                plugin.getLogger().warning("Empty JSON response received");
                onError.accept("error.api-error");
                return;
            }
            
            if (!jsonResponse.startsWith("{") || !jsonResponse.endsWith("}")) {
                plugin.getLogger().warning("Invalid JSON format - doesn't start with { or end with }");
                plugin.getLogger().warning("Response starts with: " + jsonResponse.substring(0, Math.min(50, jsonResponse.length())));
                onError.accept("error.api-error");
                return;
            }
            
            try {
                JsonObject responseObj = JsonParser.parseString(jsonResponse).getAsJsonObject();
                
                if (responseObj.has("answer")) {
                    displayText = responseObj.get("answer").getAsString();
                }
                if (responseObj.has("short_description")) {
                    shortDescription = responseObj.get("short_description").getAsString();
                }
                if (responseObj.has("title")) {
                    title = responseObj.get("title").getAsString();
                }
                if (responseObj.has("relevance_score")) {
                    relevanceScore = responseObj.get("relevance_score").getAsInt();
                    relevanceScore = Math.max(0, Math.min(10, relevanceScore));
                }
                
                if (config.isDebugEnabled()) {
                    plugin.getLogger().info("Parsed - Answer length: " + displayText.length() + ", Title: " + title + ", Short: " + shortDescription + ", Relevance: " + relevanceScore);
                }
                
            } catch (Exception e) {
                plugin.getLogger().severe("[JSON PARSE ERROR] " + e.getMessage());
                plugin.getLogger().severe("FULL RESPONSE LENGTH: " + jsonResponse.length() + " chars");
                plugin.getLogger().severe("FULL RESPONSE: " + jsonResponse);
                
                String extracted = extractJsonFromResponse(jsonResponse);
                if (extracted == null || extracted.isEmpty()) {
                    plugin.getLogger().severe("[EXTRACTION FAILED] Could not find valid JSON in response");
                    plugin.getLogger().severe("Will retry request with adjusted parameters");
                    onError.accept("error.api-error");
                    return;
                }
                
                plugin.getLogger().info("[EXTRACTION SUCCESS] Extracted " + extracted.length() + " chars");
                try {
                    JsonObject responseObj = JsonParser.parseString(extracted).getAsJsonObject();
                    if (responseObj.has("answer")) {
                        displayText = responseObj.get("answer").getAsString();
                        if (responseObj.has("short_description")) {
                            shortDescription = responseObj.get("short_description").getAsString();
                        }
                        if (responseObj.has("title")) {
                            title = responseObj.get("title").getAsString();
                        }
                        if (responseObj.has("relevance_score")) {
                            relevanceScore = responseObj.get("relevance_score").getAsInt();
                        }
                    } else {
                        onError.accept("error.api-error");
                        return;
                    }
                } catch (Exception e2) {
                    plugin.getLogger().severe("[EXTRACTION PARSE FAILED] " + e2.getMessage());
                    onError.accept("error.api-error");
                    return;
                }
            }
            
            if (!displayText.isEmpty()) {
                onChunk.accept(displayText);
            }
            
            AIResponse response = new AIResponse(displayText, shortDescription, title, relevanceScore);
            
            if (config.isLogResponses()) {
                plugin.getLogger().info("AI Response: " + fullResponse);
            }
            
            onComplete.accept(response);
            
        } finally {
            connection.disconnect();
        }
    }
    
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        
        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');
        
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return response.substring(firstBrace, lastBrace + 1);
        }
        
        return null;
    }
    
    private String executeTool(String functionName, String arguments) {
        if (!functionName.equals("fetch_context")) {
            return "Unknown function: " + functionName;
        }
        
        try {
            if (arguments == null || arguments.isEmpty()) {
                return "Error: No arguments provided to fetch_context";
            }
            
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            if (!args.has("context_name")) {
                return "Error: context_name not provided in arguments";
            }
            
            String contextName = args.get("context_name").getAsString();
            
            ContextFile file = plugin.getContextManager().getContextFile(contextName);
            if (file == null) {
                return "Context not found: " + contextName + ". Available contexts may not include this topic.";
            }
            
            return "=== " + file.getTitle() + " ===\n" + file.getContent();
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to execute tool: " + e.getMessage());
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Arguments were: " + arguments);
            }
            return "Error fetching context: " + e.getMessage();
        }
    }
    
    private void nonStreamRequest(
            JsonObject requestBody,
            Consumer<String> onChunk,
            Consumer<AIResponse> onComplete,
            Consumer<String> onError
    ) throws IOException {
        ConfigManager config = plugin.getConfigManager();
        
        URL url = new URL(config.getBaseUrl() + "/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(config.getTimeout() * 1000);
            connection.setReadTimeout(config.getTimeout() * 1000);
            
            for (Map.Entry<String, String> header : config.buildHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readErrorStream(connection);
                handleApiError(responseCode, errorBody, onError);
                return;
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            String jsonContent = jsonResponse.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
            
            String displayText = "";
            String shortDescription = "No summary available";
            String title = "Question";
            int relevanceScore = 10;
            
            try {
                JsonObject responseObj = JsonParser.parseString(jsonContent).getAsJsonObject();
                if (responseObj.has("answer")) {
                    displayText = responseObj.get("answer").getAsString();
                }
                if (responseObj.has("short_description")) {
                    shortDescription = responseObj.get("short_description").getAsString();
                }
                if (responseObj.has("title")) {
                    title = responseObj.get("title").getAsString();
                }
                if (responseObj.has("relevance_score")) {
                    relevanceScore = responseObj.get("relevance_score").getAsInt();
                    relevanceScore = Math.max(0, Math.min(10, relevanceScore));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to parse JSON: " + e.getMessage());
                displayText = jsonContent;
            }
            
            onChunk.accept(displayText);
            
            AIResponse aiResponse = new AIResponse(displayText, shortDescription, title, relevanceScore);
            onComplete.accept(aiResponse);
            
        } finally {
            connection.disconnect();
        }
    }
    
    private String readErrorStream(HttpURLConnection connection) {
        try {
            if (connection.getErrorStream() != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line);
                }
                reader.close();
                return error.toString();
            }
        } catch (Exception e) {
        }
        return "";
    }
    
    private void handleApiError(int responseCode, String errorBody, Consumer<String> onError) {
        String errorKey;
        switch (responseCode) {
            case 401:
                errorKey = "error.invalid-key";
                break;
            case 429:
                errorKey = "error.rate-limit";
                break;
            case 408:
                errorKey = "error.timeout";
                break;
            default:
                errorKey = "error.api-error";
                break;
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().warning("API Error " + responseCode + ": " + errorBody);
        }
        
        onError.accept(errorKey);
    }
    
    public static class AIResponse {
        private final String displayText;
        private final String shortDescription;
        private final String title;
        private final int relevanceScore;
        
        public AIResponse(String displayText, String shortDescription, String title, int relevanceScore) {
            this.displayText = displayText;
            this.shortDescription = shortDescription.isEmpty() ? "No summary available" : shortDescription;
            this.title = title.isEmpty() ? "Question" : title;
            this.relevanceScore = Math.max(0, Math.min(10, relevanceScore));
        }
        
        public String getDisplayText() { return displayText; }
        public String getShortDescription() { return shortDescription; }
        public String getTitle() { return title; }
        public int getRelevanceScore() { return relevanceScore; }
    }
}
