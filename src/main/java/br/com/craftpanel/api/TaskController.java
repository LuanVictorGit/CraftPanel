package br.com.craftpanel.api;

import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.List;

public class TaskController {
    private final TaskManager taskManager;

    public TaskController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void list(Context ctx) {
        AuthMiddleware.requireUser(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        List<Task> tasks = taskManager.listRecentTasks(limit);
        ctx.json(tasks);
    }

    public void get(Context ctx) {
        AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        Task task = taskManager.getTask(id).orElseThrow(() -> new NotFoundResponse("Task not found"));
        ctx.json(task);
    }
}
