package org.axonframework.quickstart.api;

import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;

/**
 * Command used to mark an existing ToDoItem as completed.
 *
 * @author Jettro Coenradie
 */
public class MarkCompletedCommand {
    @TargetAggregateIdentifier
    private final String todoId;

    public MarkCompletedCommand(String todoId) {
        this.todoId = todoId;
    }

    public String getTodoId() {
        return todoId;
    }
}
