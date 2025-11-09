package dev.jentic.adapters.llm;

import dev.jentic.adapters.llm.openai.OpenAIProvider;
//import dev.jentic.adapters.llm.anthropic.AnthropicProvider;
//import dev.jentic.adapters.llm.ollama.OllamaProvider;

public final class LLMProviderFactory {
    
    private LLMProviderFactory() {}
    
    public static OpenAIProvider.Builder openai() {
        return OpenAIProvider.builder();
    }
    
//    public static AnthropicProvider.Builder anthropic() {
//        return AnthropicProvider.builder();
//    }
//
//    public static OllamaProvider.Builder ollama() {
//        return OllamaProvider.builder();
//    }
}
