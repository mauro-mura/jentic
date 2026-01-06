package dev.jentic.examples.support.knowledge;

import dev.jentic.examples.support.model.SupportIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Knowledge store with TF-IDF based semantic search.
 * Provides better relevance ranking than simple keyword matching.
 */
public class SemanticKnowledgeStore implements KnowledgeStore {
    
    private static final Logger log = LoggerFactory.getLogger(SemanticKnowledgeStore.class);
    
    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final TFIDFScorer tfidfScorer = new TFIDFScorer();
    private boolean indexBuilt = false;
    
    @Override
    public void add(KnowledgeDocument document) {
        documents.put(document.id(), document);
        
        // Index document content for TF-IDF
        String content = buildIndexableContent(document);
        tfidfScorer.indexDocument(document.id(), content);
        
        indexBuilt = false; // Mark index as stale
    }
    
    @Override
    public Optional<KnowledgeDocument> getById(String id) {
        return Optional.ofNullable(documents.get(id));
    }
    
    @Override
    public List<KnowledgeDocument> search(String query, int maxResults) {
        ensureIndexBuilt();
        
        if (query == null || query.isBlank()) {
            return List.of();
        }
        
        // Get TF-IDF scores
        List<TFIDFScorer.ScoredDocument> tfidfResults = tfidfScorer.search(query, maxResults * 2);
        
        // Combine TF-IDF with keyword boost
        List<ScoredResult> results = new ArrayList<>();
        
        for (TFIDFScorer.ScoredDocument scored : tfidfResults) {
            KnowledgeDocument doc = documents.get(scored.docId());
            if (doc != null) {
                // Combine TF-IDF score with keyword relevance
                double keywordScore = doc.relevanceScore(query);
                double combinedScore = (scored.score() * 0.7) + (keywordScore * 0.3);
                results.add(new ScoredResult(doc, combinedScore));
            }
        }
        
        // Also include documents that match keywords but weren't in TF-IDF results
        Set<String> foundIds = tfidfResults.stream()
            .map(TFIDFScorer.ScoredDocument::docId)
            .collect(Collectors.toSet());
        
        for (KnowledgeDocument doc : documents.values()) {
            if (!foundIds.contains(doc.id())) {
                double keywordScore = doc.relevanceScore(query);
                if (keywordScore > 0.1) {
                    results.add(new ScoredResult(doc, keywordScore * 0.3));
                }
            }
        }
        
        // Sort by combined score and return top results
        return results.stream()
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(maxResults)
            .map(ScoredResult::document)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<KnowledgeDocument> searchByCategory(String query, SupportIntent category, int maxResults) {
        ensureIndexBuilt();
        
        // First filter by category, then rank by TF-IDF
        List<KnowledgeDocument> categoryDocs = documents.values().stream()
            .filter(doc -> doc.category() == category)
            .collect(Collectors.toList());
        
        if (query == null || query.isBlank()) {
            return categoryDocs.stream().limit(maxResults).collect(Collectors.toList());
        }
        
        // Score within category
        Map<String, Double> scores = tfidfScorer.scoreQuery(query);
        
        return categoryDocs.stream()
            .map(doc -> new ScoredResult(doc, scores.getOrDefault(doc.id(), 0.0)))
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(maxResults)
            .map(ScoredResult::document)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<KnowledgeDocument> getByCategory(SupportIntent category) {
        return documents.values().stream()
            .filter(doc -> doc.category() == category)
            .collect(Collectors.toList());
    }
    
    @Override
    public int size() {
        return documents.size();
    }
    
    /**
     * Builds the TF-IDF index. Called automatically on first search.
     */
    public void buildIndex() {
        tfidfScorer.buildIndex();
        indexBuilt = true;
        log.info("Built TF-IDF index: {} documents, {} terms", 
            tfidfScorer.getDocumentCount(), tfidfScorer.getVocabularySize());
    }
    
    /**
     * Returns search results with scores for debugging/analysis.
     */
    public List<ScoredResult> searchWithScores(String query, int maxResults) {
        ensureIndexBuilt();
        
        if (query == null || query.isBlank()) {
            return List.of();
        }
        
        List<TFIDFScorer.ScoredDocument> tfidfResults = tfidfScorer.search(query, maxResults * 2);
        List<ScoredResult> results = new ArrayList<>();
        
        for (TFIDFScorer.ScoredDocument scored : tfidfResults) {
            KnowledgeDocument doc = documents.get(scored.docId());
            if (doc != null) {
                double keywordScore = doc.relevanceScore(query);
                double combinedScore = (scored.score() * 0.7) + (keywordScore * 0.3);
                results.add(new ScoredResult(doc, combinedScore, scored.score(), keywordScore));
            }
        }
        
        return results.stream()
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(maxResults)
            .collect(Collectors.toList());
    }
    
    private void ensureIndexBuilt() {
        if (!indexBuilt) {
            buildIndex();
        }
    }
    
    /**
     * Builds indexable content from a document.
     * Combines title, content, and keywords with appropriate weighting.
     */
    private String buildIndexableContent(KnowledgeDocument doc) {
        StringBuilder sb = new StringBuilder();
        
        // Title weighted higher (repeat 3x)
        for (int i = 0; i < 3; i++) {
            sb.append(doc.title()).append(" ");
        }
        
        // Keywords weighted higher (repeat 2x)
        for (int i = 0; i < 2; i++) {
            for (String keyword : doc.keywords()) {
                sb.append(keyword).append(" ");
            }
        }
        
        // Full content
        sb.append(doc.content());
        
        return sb.toString();
    }
    
    /**
     * Scored search result with breakdown.
     */
    public record ScoredResult(
        KnowledgeDocument document,
        double score,
        double tfidfScore,
        double keywordScore
    ) {
        public ScoredResult(KnowledgeDocument document, double score) {
            this(document, score, score, 0.0);
        }
    }
}
