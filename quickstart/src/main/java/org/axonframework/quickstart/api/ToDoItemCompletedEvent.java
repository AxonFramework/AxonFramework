package org.axonframework.quickstart.api;

/**
 * The ToDoItem belonging to the provided aggregate identifier is completed.
 *
 * @author Jettro Coenradie
 */
public class ToDoItemCompletedEvent {
    private final String todoId;

    public ToDoItemCompletedEvent(String todoId) {
        this.todoId = todoId;
    }

    public String getTodoId() {
        return todoId;
    }
}
