/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.quickstart.handler;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

/**
 * @author Jettro Coenradie
 */
public class ToDoItem {

    @AggregateIdentifier
    private String id;

    public ToDoItem() {
    }

    public ToDoItem(String id, String description) {
        apply(new ToDoItemCreatedEvent(id, description));
    }

    public void markCompleted() {
        apply(new ToDoItemCompletedEvent(id));
    }

    @EventSourcingHandler
    protected void handle(EventMessage eventMessage) {
        if (eventMessage.getPayloadType().equals(ToDoItemCreatedEvent.class)) {
            ToDoItemCreatedEvent event = (ToDoItemCreatedEvent) eventMessage.getPayload();
            this.id = event.getTodoId();
        }
    }
}
