package com.wdp.help.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores help data for a single player
 */
public class PlayerHelpData {
    
    private List<HelpAnswer> recentAnswers;
    private Map<String, Integer> questionCounts;
    
    public PlayerHelpData() {
        this.recentAnswers = new ArrayList<>();
        this.questionCounts = new HashMap<>();
    }
    
    /**
     * Get recent answers (most recent first)
     */
    public List<HelpAnswer> getRecentAnswers() {
        return new ArrayList<>(recentAnswers);
    }
    
    /**
     * Add an answer, maintaining max size
     */
    public void addAnswer(HelpAnswer answer, int maxSize) {
        // Add to front (most recent first)
        recentAnswers.add(0, answer);
        
        // Trim to max size
        while (recentAnswers.size() > maxSize) {
            recentAnswers.remove(recentAnswers.size() - 1);
        }
    }
    
    /**
     * Get question count
     */
    public int getQuestionCount(String normalizedQuestion) {
        return questionCounts.getOrDefault(normalizedQuestion, 0);
    }
    
    /**
     * Increment question count
     */
    public void incrementQuestionCount(String normalizedQuestion) {
        int current = questionCounts.getOrDefault(normalizedQuestion, 0);
        questionCounts.put(normalizedQuestion, current + 1);
    }
    
    /**
     * Check if player has any recent answers
     */
    public boolean hasRecentAnswers() {
        return !recentAnswers.isEmpty();
    }
}
