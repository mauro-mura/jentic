package dev.jentic.examples.support.knowledge;

import dev.jentic.examples.support.model.SupportIntent;

import java.util.List;
import java.util.Set;

/**
 * A knowledge document for the support FAQ system.
 */
public record KnowledgeDocument(
    String id,
    String title,
    String content,
    SupportIntent category,
    Set<String> keywords,
    List<String> relatedIds
) {
    public KnowledgeDocument(String id, String title, String content, 
            SupportIntent category, Set<String> keywords) {
        this(id, title, content, category, keywords, List.of());
    }
    
    /**
     * Calculates relevance score based on keyword matching.
     */
    public double relevanceScore(String query) {
        if (query == null || query.isBlank()) return 0.0;
        
        String lowerQuery = query.toLowerCase();
        String[] queryTerms = lowerQuery.split("\\s+");
        
        int matches = 0;
        int totalKeywords = keywords.size();
        
        for (String term : queryTerms) {
            // Check title
            if (title.toLowerCase().contains(term)) {
                matches += 2; // Title match weighted higher
            }
            // Check keywords
            for (String keyword : keywords) {
                if (keyword.toLowerCase().contains(term) || term.contains(keyword.toLowerCase())) {
                    matches++;
                }
            }
            // Check content
            if (content.toLowerCase().contains(term)) {
                matches++;
            }
        }
        
        // Normalize score
        int maxPossible = queryTerms.length * (2 + totalKeywords + 1);
        return maxPossible > 0 ? (double) matches / maxPossible : 0.0;
    }
}
