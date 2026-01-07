package dev.jentic.examples.support.knowledge;

import dev.jentic.examples.support.model.SupportIntent;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hybrid knowledge store combining TF-IDF and vector embeddings.
 * Uses weighted combination of both methods for better relevance.
 * 
 * Score = (tfidfWeight * tfidfScore) + (embeddingWeight * embeddingScore)
 */
public class HybridKnowledgeStore implements KnowledgeStore {
    
    private static final Logger log = LoggerFactory.getLogger(HybridKnowledgeStore.class);
    
    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final TFIDFScorer tfidfScorer = new TFIDFScorer();
    private final InMemoryVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    // Weights for combining scores
    private final double tfidfWeight;
    private final double embeddingWeight;
    
    private boolean tfidfIndexBuilt = false;
    private boolean embeddingsIndexed = false;
    
    /**
     * Creates a hybrid store with embeddings enabled.
     */
    public HybridKnowledgeStore(EmbeddingModel embeddingModel, int dimensions) {
        this(embeddingModel, dimensions, 0.4, 0.6); // Default: 40% TF-IDF, 60% embeddings
    }
    
    /**
     * Creates a hybrid store with custom weights.
     */
    public HybridKnowledgeStore(EmbeddingModel embeddingModel, int dimensions, 
            double tfidfWeight, double embeddingWeight) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = new InMemoryVectorStore(dimensions);
        this.tfidfWeight = tfidfWeight;
        this.embeddingWeight = embeddingWeight;
    }
    
    /**
     * Creates a hybrid store without embeddings (TF-IDF only fallback).
     */
    public HybridKnowledgeStore() {
        this.embeddingModel = null;
        this.vectorStore = null;
        this.tfidfWeight = 1.0;
        this.embeddingWeight = 0.0;
    }
    
    @Override
    public void add(KnowledgeDocument document) {
        documents.put(document.id(), document);
        
        // Index for TF-IDF
        String content = buildIndexableContent(document);
        tfidfScorer.indexDocument(document.id(), content);
        
        tfidfIndexBuilt = false;
        embeddingsIndexed = false;
    }
    
    /**
     * Builds embeddings index for all documents.
     * Call after all documents are added.
     */
    public void buildIndex() {
        // Build TF-IDF index
        tfidfScorer.buildIndex();
        tfidfIndexBuilt = true;
        log.info("Built TF-IDF index: {} documents", tfidfScorer.getDocumentCount());
        
        // Build embeddings index if model available
        if (embeddingModel != null && vectorStore != null) {
            int indexed = 0;
            for (KnowledgeDocument doc : documents.values()) {
                try {
                    String content = buildIndexableContent(doc);
                    Embedding embedding = embeddingModel.embed(content).content();
                    vectorStore.store(doc.id(), embedding.vector(), 
                        Map.of("title", doc.title(), "category", doc.category().code()));
                    indexed++;
                } catch (Exception e) {
                    log.warn("Failed to embed document {}: {}", doc.id(), e.getMessage());
                }
            }
            embeddingsIndexed = true;
            log.info("Built embeddings index: {} documents", indexed);
        }
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
        Map<String, Double> tfidfScores = tfidfScorer.scoreQuery(query);
        
        // Get embedding scores if available
        Map<String, Double> embeddingScores = new HashMap<>();
        if (embeddingModel != null && vectorStore != null && embeddingsIndexed) {
            try {
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                List<InMemoryVectorStore.SearchResult> vectorResults = 
                    vectorStore.search(queryEmbedding.vector(), maxResults * 2, 0.3);
                
                for (InMemoryVectorStore.SearchResult result : vectorResults) {
                    embeddingScores.put(result.id(), result.score());
                }
            } catch (Exception e) {
                log.warn("Embedding search failed, using TF-IDF only: {}", e.getMessage());
            }
        }
        
        // Combine scores
        Set<String> allIds = new HashSet<>();
        allIds.addAll(tfidfScores.keySet());
        allIds.addAll(embeddingScores.keySet());
        
        List<ScoredResult> results = new ArrayList<>();
        for (String id : allIds) {
            KnowledgeDocument doc = documents.get(id);
            if (doc != null) {
                double tfidf = tfidfScores.getOrDefault(id, 0.0);
                double embedding = embeddingScores.getOrDefault(id, 0.0);
                double combined = (tfidfWeight * tfidf) + (embeddingWeight * embedding);
                results.add(new ScoredResult(doc, combined, tfidf, embedding));
            }
        }
        
        return results.stream()
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(maxResults)
            .map(ScoredResult::document)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<KnowledgeDocument> searchByCategory(String query, SupportIntent category, int maxResults) {
        ensureIndexBuilt();
        
        List<KnowledgeDocument> categoryDocs = documents.values().stream()
            .filter(doc -> doc.category() == category)
            .collect(Collectors.toList());
        
        if (query == null || query.isBlank()) {
            return categoryDocs.stream().limit(maxResults).collect(Collectors.toList());
        }
        
        // Score within category using hybrid approach
        Map<String, Double> tfidfScores = tfidfScorer.scoreQuery(query);
        Map<String, Double> embeddingScores = new HashMap<>();
        
        if (embeddingModel != null && vectorStore != null && embeddingsIndexed) {
            try {
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                List<InMemoryVectorStore.SearchResult> vectorResults = 
                    vectorStore.search(queryEmbedding.vector(), maxResults * 2, 0.3);
                
                for (InMemoryVectorStore.SearchResult result : vectorResults) {
                    embeddingScores.put(result.id(), result.score());
                }
            } catch (Exception e) {
                log.debug("Embedding search failed: {}", e.getMessage());
            }
        }
        
        return categoryDocs.stream()
            .map(doc -> {
                double tfidf = tfidfScores.getOrDefault(doc.id(), 0.0);
                double embedding = embeddingScores.getOrDefault(doc.id(), 0.0);
                double combined = (tfidfWeight * tfidf) + (embeddingWeight * embedding);
                return new ScoredResult(doc, combined, tfidf, embedding);
            })
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
     * Returns detailed search results with score breakdown.
     */
    public List<ScoredResult> searchWithScores(String query, int maxResults) {
        ensureIndexBuilt();
        
        if (query == null || query.isBlank()) {
            return List.of();
        }
        
        Map<String, Double> tfidfScores = tfidfScorer.scoreQuery(query);
        Map<String, Double> embeddingScores = new HashMap<>();
        
        if (embeddingModel != null && vectorStore != null && embeddingsIndexed) {
            try {
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                List<InMemoryVectorStore.SearchResult> vectorResults = 
                    vectorStore.search(queryEmbedding.vector(), maxResults * 2, 0.3);
                
                for (InMemoryVectorStore.SearchResult result : vectorResults) {
                    embeddingScores.put(result.id(), result.score());
                }
            } catch (Exception e) {
                log.debug("Embedding search failed: {}", e.getMessage());
            }
        }
        
        Set<String> allIds = new HashSet<>();
        allIds.addAll(tfidfScores.keySet());
        allIds.addAll(embeddingScores.keySet());
        
        return allIds.stream()
            .map(id -> {
                KnowledgeDocument doc = documents.get(id);
                if (doc == null) return null;
                double tfidf = tfidfScores.getOrDefault(id, 0.0);
                double embedding = embeddingScores.getOrDefault(id, 0.0);
                double combined = (tfidfWeight * tfidf) + (embeddingWeight * embedding);
                return new ScoredResult(doc, combined, tfidf, embedding);
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
            .limit(maxResults)
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if embeddings are enabled and indexed.
     */
    public boolean isEmbeddingsEnabled() {
        return embeddingModel != null && embeddingsIndexed;
    }
    
    private void ensureIndexBuilt() {
        if (!tfidfIndexBuilt) {
            buildIndex();
        }
    }
    
    private String buildIndexableContent(KnowledgeDocument doc) {
        StringBuilder sb = new StringBuilder();
        
        // Title (weighted)
        for (int i = 0; i < 3; i++) {
            sb.append(doc.title()).append(" ");
        }
        
        // Keywords (weighted)
        for (int i = 0; i < 2; i++) {
            for (String keyword : doc.keywords()) {
                sb.append(keyword).append(" ");
            }
        }
        
        // Content
        sb.append(doc.content());
        
        return sb.toString();
    }
    
    /**
     * Scored result with breakdown.
     */
    public record ScoredResult(
        KnowledgeDocument document,
        double score,
        double tfidfScore,
        double embeddingScore
    ) {}
}
