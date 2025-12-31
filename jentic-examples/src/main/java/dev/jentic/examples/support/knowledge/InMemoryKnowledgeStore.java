package dev.jentic.examples.support.knowledge;

import dev.jentic.examples.support.model.SupportIntent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of KnowledgeStore.
 * Uses simple keyword matching for relevance scoring.
 */
public class InMemoryKnowledgeStore implements KnowledgeStore {
    
    private final Map<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    
    @Override
    public void add(KnowledgeDocument document) {
        documents.put(document.id(), document);
    }
    
    @Override
    public Optional<KnowledgeDocument> getById(String id) {
        return Optional.ofNullable(documents.get(id));
    }
    
    @Override
    public List<KnowledgeDocument> search(String query, int topK) {
        return documents.values().stream()
            .map(doc -> new ScoredDocument(doc, doc.relevanceScore(query)))
            .filter(sd -> sd.score > 0.0)
            .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
            .limit(topK)
            .map(ScoredDocument::document)
            .toList();
    }
    
    @Override
    public List<KnowledgeDocument> searchByCategory(String query, SupportIntent category, int topK) {
        return documents.values().stream()
            .filter(doc -> doc.category() == category)
            .map(doc -> new ScoredDocument(doc, doc.relevanceScore(query)))
            .filter(sd -> sd.score > 0.0)
            .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
            .limit(topK)
            .map(ScoredDocument::document)
            .toList();
    }
    
    @Override
    public List<KnowledgeDocument> getByCategory(SupportIntent category) {
        return documents.values().stream()
            .filter(doc -> doc.category() == category)
            .toList();
    }
    
    @Override
    public int size() {
        return documents.size();
    }
    
    private record ScoredDocument(KnowledgeDocument document, double score) {}
}
