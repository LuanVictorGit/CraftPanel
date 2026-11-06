package br.com.craftpanel.ai;

import br.com.craftpanel.database.DatabaseManager;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Saved chats. LangChain4j's chat memory reads and writes each conversation's messages through this store. */
public class ConversationStore implements ChatMemoryStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationStore.class);

    public record Conversation(String id, String userId, String serverId, String title, Instant createdAt, Instant updatedAt) {}

    private final DatabaseManager db;

    public ConversationStore(DatabaseManager db) {
        this.db = db;
    }

    public Conversation create(String userId, String serverId, String title) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation(UUID.randomUUID().toString(), userId, serverId, title, now, now);
        String sql = "INSERT INTO ai_conversations (id, user_id, server_id, title, messages_json, created_at, updated_at) VALUES (?, ?, ?, ?, '[]', ?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, conversation.id());
            stmt.setString(2, userId);
            stmt.setString(3, serverId);
            stmt.setString(4, title);
            stmt.setString(5, now.toString());
            stmt.setString(6, now.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating conversation", e);
        }
        return conversation;
    }

    public List<Conversation> list(String userId) {
        String sql = "SELECT id, user_id, server_id, title, created_at, updated_at FROM ai_conversations WHERE user_id = ? ORDER BY updated_at DESC LIMIT 100";
        List<Conversation> conversations = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing conversations", e);
        }
        return conversations;
    }

    /** A conversation only exists for its owner: other users get the same answer as for a missing id. */
    public Optional<Conversation> find(String userId, String id) {
        String sql = "SELECT id, user_id, server_id, title, created_at, updated_at FROM ai_conversations WHERE id = ? AND user_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading conversation", e);
        }
    }

    public boolean delete(String userId, String id) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM ai_conversations WHERE id = ? AND user_id = ?")) {
            stmt.setString(1, id);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting conversation", e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT messages_json FROM ai_conversations WHERE id = ?")) {
            stmt.setString(1, String.valueOf(memoryId));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(rs.getString(1)));
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.warn("Could not read messages of conversation {}: {}", memoryId, e.toString());
        }
        return new ArrayList<>();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE ai_conversations SET messages_json = ?, updated_at = ? WHERE id = ?")) {
            stmt.setString(1, ChatMessageSerializer.messagesToJson(messages));
            stmt.setString(2, Instant.now().toString());
            stmt.setString(3, String.valueOf(memoryId));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving conversation", e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        updateMessages(memoryId, List.of());
    }

    private static Conversation map(ResultSet rs) throws SQLException {
        return new Conversation(rs.getString("id"), rs.getString("user_id"), rs.getString("server_id"), rs.getString("title"),
                Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
}
