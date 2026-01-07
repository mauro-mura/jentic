package dev.jentic.examples.support.knowledge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Expands queries with synonyms to improve search recall.
 * Domain-specific for personal finance support.
 */
public class QueryExpander {
    
    // Synonym groups (all terms in a group are considered equivalent)
    private static final List<Set<String>> SYNONYM_GROUPS = List.of(
        // Account related
        Set.of("password", "pass", "pwd", "passcode", "credentials"),
        Set.of("reset", "change", "update", "modify", "recover"),
        Set.of("account", "profile", "user"),
        Set.of("login", "signin", "sign in", "log in", "access"),
        Set.of("logout", "signout", "sign out", "log out"),
        Set.of("register", "signup", "sign up", "create account", "join"),
        
        // Security
        Set.of("2fa", "two factor", "twofactor", "mfa", "two-factor", "authenticator"),
        Set.of("device", "phone", "mobile", "gadget"),
        Set.of("security", "secure", "protection", "safety"),
        Set.of("locked", "lock", "blocked", "disabled"),
        Set.of("unauthorized", "suspicious", "fraud", "fraudulent", "unknown"),
        
        // Transactions
        Set.of("transaction", "payment", "transfer", "charge"),
        Set.of("history", "log", "record", "activity", "recent"),
        Set.of("dispute", "contest", "challenge", "report"),
        Set.of("refund", "return", "reversal", "chargeback"),
        Set.of("export", "download", "save", "extract"),
        Set.of("pending", "processing", "in progress", "waiting"),
        
        // Budget
        Set.of("budget", "spending limit", "limit"),
        Set.of("spending", "expenses", "expenditure"),
        Set.of("alert", "notification", "warning", "notify"),
        Set.of("category", "type", "group"),
        Set.of("track", "monitor", "watch", "follow"),
        
        // Banking
        Set.of("bank", "financial institution", "institution"),
        Set.of("link", "connect", "add", "sync"),
        Set.of("balance", "amount", "total", "funds"),
        Set.of("statement", "summary", "report"),
        
        // General
        Set.of("help", "assist", "support"),
        Set.of("cancel", "delete", "remove", "close"),
        Set.of("fee", "charge", "cost", "price"),
        Set.of("premium", "paid", "subscription", "pro"),
        Set.of("free", "basic", "starter")
    );
    
    // Build reverse lookup: word -> set of synonyms
    private final Map<String, Set<String>> synonymLookup;
    
    public QueryExpander() {
        this.synonymLookup = buildSynonymLookup();
    }
    
    private Map<String, Set<String>> buildSynonymLookup() {
        Map<String, Set<String>> lookup = new HashMap<>();
        
        for (Set<String> group : SYNONYM_GROUPS) {
            for (String term : group) {
                // Normalize to lowercase
                String normalizedTerm = term.toLowerCase();
                // Get or create synonyms set (excluding the term itself)
                Set<String> synonyms = lookup.computeIfAbsent(normalizedTerm, k -> new HashSet<>());
                for (String synonym : group) {
                    if (!synonym.equalsIgnoreCase(term)) {
                        synonyms.add(synonym.toLowerCase());
                    }
                }
            }
        }
        
        return lookup;
    }
    
    /**
     * Expands a query by adding synonyms.
     * Returns the original query plus expanded terms.
     * 
     * @param query the original query
     * @return expanded query string
     */
    public String expand(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        
        String lowerQuery = query.toLowerCase();
        Set<String> expansions = new LinkedHashSet<>();
        expansions.add(query); // Keep original first
        
        // Check each term and multi-word phrases
        String[] words = lowerQuery.split("\\s+");
        
        // Single words
        for (String word : words) {
            Set<String> synonyms = synonymLookup.get(word);
            if (synonyms != null) {
                expansions.addAll(synonyms);
            }
        }
        
        // Two-word phrases
        for (int i = 0; i < words.length - 1; i++) {
            String phrase = words[i] + " " + words[i + 1];
            Set<String> synonyms = synonymLookup.get(phrase);
            if (synonyms != null) {
                expansions.addAll(synonyms);
            }
        }
        
        return String.join(" ", expansions);
    }
    
    /**
     * Gets synonyms for a single term.
     * 
     * @param term the term to look up
     * @return set of synonyms (may be empty)
     */
    public Set<String> getSynonyms(String term) {
        if (term == null) {
            return Set.of();
        }
        return synonymLookup.getOrDefault(term.toLowerCase(), Set.of());
    }
    
    /**
     * Expands a query and returns structured result.
     */
    public ExpansionResult expandWithDetails(String query) {
        if (query == null || query.isBlank()) {
            return new ExpansionResult(query, List.of(), query);
        }
        
        String lowerQuery = query.toLowerCase();
        List<Expansion> expansions = new ArrayList<>();
        Set<String> addedTerms = new LinkedHashSet<>();
        addedTerms.add(query);
        
        String[] words = lowerQuery.split("\\s+");
        
        for (String word : words) {
            Set<String> synonyms = synonymLookup.get(word);
            if (synonyms != null && !synonyms.isEmpty()) {
                expansions.add(new Expansion(word, synonyms));
                addedTerms.addAll(synonyms);
            }
        }
        
        String expandedQuery = String.join(" ", addedTerms);
        return new ExpansionResult(query, expansions, expandedQuery);
    }
    
    /**
     * Result of query expansion.
     */
    public record ExpansionResult(
        String originalQuery,
        List<Expansion> expansions,
        String expandedQuery
    ) {
        public boolean wasExpanded() {
            return !expansions.isEmpty();
        }
    }
    
    /**
     * Single term expansion.
     */
    public record Expansion(String term, Set<String> synonyms) {}
}
