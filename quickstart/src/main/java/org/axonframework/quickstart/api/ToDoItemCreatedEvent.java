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

    @Override
    public String toString() {
        return "ToDoItemCreatedEvent(" + todoId + ", '" + description + "')";
    }
}
