/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.quickstart.annotated;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.annotation.Timestamp;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.joda.time.DateTime;

/**
 * Event handler that listens to both events and prints a message to the system output stream.
 *
 * @author Jettro Coenradie
 */
public class ToDoEventHandler {

    @EventHandler
    public void handle(ToDoItemCreatedEvent event, @Timestamp DateTime time) {
        System.out.println(String.format("We've got something to do: %s (%s, created at %s)",
                                         event.getDescription(),
                                         event.getTodoId(),
                                         time.toString("d-M-y H:m")));
    }

    @EventHandler
    public void handle(ToDoItemCompletedEvent event) {
        System.out.println(String.format("We've completed the task with id %s", event.getTodoId()));
    }
}
