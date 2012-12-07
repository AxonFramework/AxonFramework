package org.axonframework.quickstart.api;

/**
 * A new ToDoItem is created
 *
 * @author Jettro Coenradie
 */
public class ToDoItemCreatedEvent {
    private final String todoId;
    private final String description;

    public ToDoItemCreatedEvent(String todoId, String description) {
        this.todoId = todoId;
        this.description = description;
    }

    public String getTodoId() {
        return todoId;
    }

    public String getDescription() {
        return description;
    }
}
