/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.entity.domain.todo;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel;
import org.axonframework.modelling.entity.domain.todo.commands.CreateTodoItem;
import org.axonframework.modelling.entity.domain.todo.commands.FinishTodoItem;

/**
 * A simple entity designed for representing the most minimal usecase of the
 * {@link AnnotatedEntityMetamodel}. This is to ensure that even without bells
 * and whistles, the {@link AnnotatedEntityMetamodel} functions as expected.
 */
public class TodoItem {

    private String id;
    private String description;
    private boolean completed;

    @CommandHandler
    public void handle(CreateTodoItem command) {
        if (this.id != null) {
            throw new IllegalStateException("Todo item already created");
        }

        this.id = command.id();
        this.description = command.description();
        this.completed = false;
    }

    @CommandHandler
    public void handle(FinishTodoItem command) {
        if (this.id == null || !this.id.equals(command.todoItemId())) {
            throw new IllegalStateException("Todo item not found");
        }
        if (this.completed) {
            throw new IllegalStateException("Todo item already completed");
        }
        this.completed = true;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }
}
