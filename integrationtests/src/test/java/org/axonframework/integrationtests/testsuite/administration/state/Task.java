/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.testsuite.administration.state;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.events.TaskCompleted;
import org.axonframework.modelling.command.EntityId;

public class Task {
    @EntityId
    private String taskId;
    private String description;
    private Boolean completed;

    public Task(String taskId, String description) {
        this.taskId = taskId;
        this.description = description;
        this.completed = false;
    }

    @CommandHandler
    public void handle(CompleteTaskCommand command, EventAppender eventAppender) {
        if(this.completed) {
            throw new IllegalStateException("Task is already completed");
        }

        eventAppender.append(new TaskCompleted(command.identifier(), command.taskId()));
    }

    @EventSourcingHandler
    public void on(TaskCompleted event) {
        this.completed = true;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isCompleted() {
        return completed;
    }
}
