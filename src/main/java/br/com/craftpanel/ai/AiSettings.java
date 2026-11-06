package br.com.craftpanel.ai;

import br.com.craftpanel.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The panel-wide AI configuration, kept in the settings table. Each provider keeps its own key and model, so switching
 * back does not ask for them again. Keys are never sent back to the browser.
 */
public class AiSettings {
    private static final String PROVIDER = "ai.provider";

    public record AiConfig(AiProvider provider, String apiKey, String model, boolean thinking) {}

    private final DatabaseManager db;

    public AiSettings(DatabaseManager db) {
        this.db = db;
    }

    /** The provider and model the chat uses, if that provider has been set up. */
    public Optional<AiConfig> active() {
        AiProvider provider = provider();
        Optional<String> key = apiKey(provider);
        Optional<String> model = model(provider);
        if (key.isEmpty() || model.isEmpty()) {
            return Optional.empty();
        }
        boolean thinking = get(thinkingKey(provider)).map(Boolean::parseBoolean).orElse(false);
        return Optional.of(new AiConfig(provider, key.get(), model.get(), thinking));
    }

    /** Panels configured before providers existed have only a Gemini key, so Gemini is the default. */
    public AiProvider provider() {
        return get(PROVIDER).flatMap(AiProvider::fromId).orElse(AiProvider.GEMINI);
    }

    public void saveProvider(AiProvider provider) {
        set(PROVIDER, provider.id());
    }

    public Optional<String> apiKey(AiProvider provider) {
        return get(apiKeyKey(provider));
    }

    public Optional<String> model(AiProvider provider) {
        return get(modelKey(provider));
    }

    public void saveApiKey(AiProvider provider, String apiKey) {
        set(apiKeyKey(provider), apiKey);
    }

    public void saveModel(AiProvider provider, String model, boolean thinking) {
        set(modelKey(provider), model);
        set(thinkingKey(provider), String.valueOf(thinking));
    }

    public void clear(AiProvider provider) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM settings WHERE key IN (?, ?, ?)")) {
            stmt.setString(1, apiKeyKey(provider));
            stmt.setString(2, modelKey(provider));
            stmt.setString(3, thinkingKey(provider));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error clearing AI settings", e);
        }
    }

    private static String apiKeyKey(AiProvider provider) {
        return "ai." + provider.id() + ".apiKey";
    }

    private static String modelKey(AiProvider provider) {
        return "ai." + provider.id() + ".model";
    }

    private static String thinkingKey(AiProvider provider) {
        return "ai." + provider.id() + ".thinking";
    }

    private Optional<String> get(String key) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("value")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading AI settings", e);
        }
    }

    private void set(String key, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving AI settings", e);
        }
    }
}
