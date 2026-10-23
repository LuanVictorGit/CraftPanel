package br.com.craftpanel.tasks;

import br.com.craftpanel.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystemException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);

    public interface TaskListener {
        void onTaskUpdated(Task task);
    }

    private final DatabaseManager db;
    private final ExecutorService executor;
    private final Map<String, Task> activeTasks = new ConcurrentHashMap<>();
    private final List<TaskListener> listeners = new CopyOnWriteArrayList<>();

    public TaskManager(DatabaseManager db) {
        this.db = db;
        failInterruptedTasks();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "craftpanel-task");
            t.setDaemon(true);
            return t;
        });
    }

    /** Stops the workers: queued tasks are dropped and running ones interrupted; leftovers are failed on the next start. */
    public void shutdown() {
        executor.shutdownNow();
    }

    // Tasks still QUEUED/RUNNING in the database were cut off by a stop or reload and would otherwise show forever.
    private void failInterruptedTasks() {
        String reason = "Interrompida por reinício do servidor";
        String sql = "UPDATE tasks SET state = ?, message = ?, error = ?, completed_at = ? WHERE state IN (?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Task.State.FAILED.name());
            stmt.setString(2, reason);
            stmt.setString(3, reason);
            stmt.setString(4, Instant.now().toString());
            stmt.setString(5, Task.State.QUEUED.name());
            stmt.setString(6, Task.State.RUNNING.name());
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                LOGGER.warn("Marked {} interrupted task(s) as failed", updated);
            }
        } catch (SQLException e) {
            LOGGER.error("Error marking interrupted tasks as failed", e);
        }
    }

    public void addListener(TaskListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaskListener listener) {
        listeners.remove(listener);
    }

    public Task submit(String type, String targetId, String initialMessage, TaskRunnable runnable) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Task task = new Task(id, type, targetId, Task.State.QUEUED, 0, initialMessage, null, now, null);
        activeTasks.put(id, task);
        saveTask(task);
        notifyUpdate(task);

        executor.submit(() -> {
            updateTask(id, Task.State.RUNNING, 5, "Iniciando processamento...", null);
            try {
                runnable.run(
                        pct -> updateTask(id, Task.State.RUNNING, pct, null, null),
                        msg -> updateTask(id, Task.State.RUNNING, -1, msg, null)
                );
                updateTask(id, Task.State.COMPLETED, 100, "Concluído com sucesso", null);
            } catch (Throwable e) {
                LOGGER.error("Task {} failed", id, e);
                // FileSystemException messages are just a path ("C:\Users\..."), so keep the exception type.
                String reason = e.getMessage() == null || e instanceof FileSystemException
                        ? e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "")
                        : e.getMessage();
                updateTask(id, Task.State.FAILED, -1, "Falha na execução: " + reason, reason);
            }
        });

        return task;
    }

    public Optional<Task> getTask(String id) {
        Task active = activeTasks.get(id);
        if (active != null) {
            return Optional.of(active);
        }

        String sql = "SELECT id, type, target_id, state, progress, message, error, created_at, completed_at FROM tasks WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching task", e);
        }
        return Optional.empty();
    }

    public List<Task> listRecentTasks(int limit) {
        List<Task> list = new ArrayList<>();
        String sql = "SELECT id, type, target_id, state, progress, message, error, created_at, completed_at FROM tasks ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing tasks", e);
        }
        return list;
    }

    private synchronized void updateTask(String id, Task.State state, int progress, String message, String error) {
        Task current = activeTasks.get(id);
        if (current == null) {
            current = getTask(id).orElse(null);
            if (current == null) return;
        }

        int finalProgress = (progress >= 0) ? progress : current.progress();
        String finalMessage = (message != null) ? message : current.message();
        String finalError = (error != null) ? error : current.error();
        Instant completedAt = (state == Task.State.COMPLETED || state == Task.State.FAILED || state == Task.State.CANCELLED)
                ? Instant.now()
                : current.completedAt();

        Task updated = new Task(id, current.type(), current.targetId(), state, finalProgress, finalMessage, finalError, current.createdAt(), completedAt);
        activeTasks.put(id, updated);
        updateTaskDb(updated);
        notifyUpdate(updated);

        if (state == Task.State.COMPLETED || state == Task.State.FAILED || state == Task.State.CANCELLED) {
            activeTasks.remove(id);
        }
    }

    private void saveTask(Task task) {
        String sql = "INSERT INTO tasks (id, type, target_id, state, progress, message, error, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, task.id());
            stmt.setString(2, task.type());
            stmt.setString(3, task.targetId());
            stmt.setString(4, task.state().name());
            stmt.setInt(5, task.progress());
            stmt.setString(6, task.message());
            stmt.setString(7, task.error());
            stmt.setString(8, task.createdAt().toString());
            stmt.setString(9, task.completedAt() != null ? task.completedAt().toString() : null);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Error inserting task into db", e);
        }
    }

    private void updateTaskDb(Task task) {
        String sql = "UPDATE tasks SET state = ?, progress = ?, message = ?, error = ?, completed_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, task.state().name());
            stmt.setInt(2, task.progress());
            stmt.setString(3, task.message());
            stmt.setString(4, task.error());
            stmt.setString(5, task.completedAt() != null ? task.completedAt().toString() : null);
            stmt.setString(6, task.id());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Error updating task in db", e);
        }
    }

    private void notifyUpdate(Task task) {
        for (TaskListener listener : listeners) {
            try {
                listener.onTaskUpdated(task);
            } catch (Exception e) {
                LOGGER.error("Error notifying task listener", e);
            }
        }
    }

    private Task mapTask(ResultSet rs) throws SQLException {
        String compStr = rs.getString("completed_at");
        return new Task(
                rs.getString("id"),
                rs.getString("type"),
                rs.getString("target_id"),
                Task.State.valueOf(rs.getString("state")),
                rs.getInt("progress"),
                rs.getString("message"),
                rs.getString("error"),
                Instant.parse(rs.getString("created_at")),
                compStr != null ? Instant.parse(compStr) : null
        );
    }

    @FunctionalInterface
    public interface TaskRunnable {
        void run(Consumer<Integer> progress, Consumer<String> message) throws Exception;
    }
}
