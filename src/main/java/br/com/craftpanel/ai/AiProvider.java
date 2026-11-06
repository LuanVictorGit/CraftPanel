package br.com.craftpanel.ai;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** The services the panel's chat can run on. */
public enum AiProvider {
    GEMINI("gemini", "Gemini") {
        @Override
        public List<AiModel> listModels(String apiKey) throws IOException, InterruptedException {
            return GeminiModels.list(apiKey);
        }

        @Override
        public Optional<AiModel> preferred(List<AiModel> models) {
            return GeminiModels.preferred(models);
        }
    },
    OPENROUTER("openrouter", "OpenRouter") {
        @Override
        public List<AiModel> listModels(String apiKey) throws IOException, InterruptedException {
            return OpenRouterModels.list(apiKey);
        }

        @Override
        public Optional<AiModel> preferred(List<AiModel> models) {
            return OpenRouterModels.preferred(models);
        }
    };

    private final String id;
    private final String label;

    AiProvider(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Lists the chat models this key can use, failing if the key is rejected. */
    public abstract List<AiModel> listModels(String apiKey) throws IOException, InterruptedException;

    public abstract Optional<AiModel> preferred(List<AiModel> models);

    public static Optional<AiProvider> fromId(String id) {
        for (AiProvider provider : values()) {
            if (provider.id.equals(id)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
