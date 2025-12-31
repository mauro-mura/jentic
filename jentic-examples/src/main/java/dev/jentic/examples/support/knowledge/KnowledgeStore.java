package dev.jentic.examples.support.knowledge;

import dev.jentic.examples.support.model.SupportIntent;

import java.util.List;
import java.util.Optional;

/**
 * Interface for knowledge document storage and retrieval.
 */
public interface KnowledgeStore {
    
    /**
     * Adds a document to the store.
     */
    void add(KnowledgeDocument document);
    
    /**
     * Retrieves a document by ID.
     */
    Optional<KnowledgeDocument> getById(String id);
    
    /**
     * Searches for documents matching the query.
     * Returns top-k results ordered by relevance.
     */
    List<KnowledgeDocument> search(String query, int topK);
    
    /**
     * Searches within a specific category.
     */
    List<KnowledgeDocument> searchByCategory(String query, SupportIntent category, int topK);
    
    /**
     * Gets all documents in a category.
     */
    List<KnowledgeDocument> getByCategory(SupportIntent category);
    
    /**
     * Returns total document count.
     */
    int size();
}
