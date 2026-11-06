package br.com.craftpanel.ai;

import br.com.craftpanel.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** The panel-wide Gemini configuration, kept in the settings table. The key is never sent back to the browser. */
public class AiSettings {
    private static final String API_KEY = "ai.gemini.apiKey";
    private static final String MODEL = "ai.gemini.model";
    private static final String THINKING = "ai.gemini.thinking";

    public record GeminiConfig(String apiKey, String model, boolean thinking) {}

    private final DatabaseManager db;

    public AiSettings(DatabaseManager db) {
        this.db = db;
    }

    public Optional<GeminiConfig> gemini() {
        Optional<String> key = get(API_KEY);
        Optional<String> model = get(MODEL);
        if (key.isEmpty() || model.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GeminiConfig(key.get(), model.get(), get(THINKING).map(Boolean::parseBoolean).orElse(false)));
    }

    public Optional<String> apiKey() {
        return get(API_KEY);
    }

    public Optional<String> model() {
        return get(MODEL);
    }

    public void saveApiKey(String apiKey) {
        set(API_KEY, apiKey);
    }

    public void saveModel(String model, boolean thinking) {
        set(MODEL, model);
        set(THINKING, String.valueOf(thinking));
    }

    public void clear() {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM settings WHERE key IN (?, ?, ?)")) {
            stmt.setString(1, API_KEY);
            stmt.setString(2, MODEL);
            stmt.setString(3, THINKING);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error clearing AI settings", e);
        }
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
