package dev.jentic.adapters.a2a;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for A2A adapter.
 * 
 * @since 0.5.0
 */
public class A2AAdapterConfig {
    
    private String agentName = "jentic-agent";
    private String agentDescription = "Jentic Framework Agent";
    private String baseUrl = "http://localhost:8080";
    private String version = "1.0.0";
    private boolean streamingEnabled = false;
    private Duration timeout = Duration.ofMinutes(5);
    private List<Skill> skills = new ArrayList<>();
    
    public String getAgentName() {
        return agentName;
    }
    
    public A2AAdapterConfig agentName(String agentName) {
        this.agentName = agentName;
        return this;
    }
    
    public String getAgentDescription() {
        return agentDescription;
    }
    
    public A2AAdapterConfig agentDescription(String agentDescription) {
        this.agentDescription = agentDescription;
        return this;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public A2AAdapterConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }
    
    public String getVersion() {
        return version;
    }
    
    public A2AAdapterConfig version(String version) {
        this.version = version;
        return this;
    }
    
    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }
    
    public A2AAdapterConfig streamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
        return this;
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public A2AAdapterConfig timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public List<Skill> getSkills() {
        return skills;
    }
    
    public A2AAdapterConfig addSkill(Skill skill) {
        this.skills.add(skill);
        return this;
    }
    
    public A2AAdapterConfig skills(List<Skill> skills) {
        this.skills = new ArrayList<>(skills);
        return this;
    }
    
    /**
     * Represents an A2A skill/capability.
     */
    public record Skill(
        String id,
        String name,
        String description,
        List<String> tags
    ) {
        public Skill(String id, String name, String description) {
            this(id, name, description, List.of());
        }
    }
    
    public static A2AAdapterConfig create() {
        return new A2AAdapterConfig();
    }
}