package org.axonframework.quickstart;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;

/**
 * Event handler that listens to both events and prints a message to the system output stream.
 *
 * @author Jettro Coenradie
 */
public class ToDoEventHandler {

    @EventHandler
    public void handle(ToDoItemCreatedEvent event) {
        System.out.println(String.format("We've got something to do: %s (%s)", event.getDescription(), event.getTodoId()));
    }

    @EventHandler
    public void handle(ToDoItemCompletedEvent event) {
        System.out.println(String.format("We've completed the task with id %s", event.getTodoId()));
    }
}
