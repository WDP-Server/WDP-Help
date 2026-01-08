package com.wdp.help.data;

/**
 * Represents a single help answer with metadata
 */
public class HelpAnswer {
    
    private final String question;
    private final String answer;
    private final String shortDescription;
    private final String title;
    private final long timestamp;
    private final int questionHash; // Hash of normalized question for duplicate detection
    
    public HelpAnswer(String question, String answer, String shortDescription, String title, long timestamp) {
        this.question = question;
        this.answer = answer;
        this.shortDescription = shortDescription;
        this.title = title;
        this.timestamp = timestamp;
        this.questionHash = normalizeQuestion(question).hashCode();
    }
    
    /**
     * Normalize question for duplicate detection (lowercase, trim, remove extra spaces)
     */
    private String normalizeQuestion(String q) {
        return q.toLowerCase().trim().replaceAll("\\s+", " ");
    }
    
    public String getQuestion() {
        return question;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public String getShortDescription() {
        return shortDescription;
    }
    
    public String getTitle() {
        return title;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getQuestionHash() {
        return questionHash;
    }
    
    /**
     * Check if this answer is for the same question
     */
    public boolean isSameQuestion(String otherQuestion) {
        return this.questionHash == normalizeQuestion(otherQuestion).hashCode();
    }
    
    @Override
    public String toString() {
        return "HelpAnswer{title='" + title + "', question='" + question + "'}";
    }
}
