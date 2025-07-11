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

package org.axonframework.integrationtests.testsuite.administration.state.immutable;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.annotation.RoutingKey;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.events.TaskCompleted;

public record ImmutableTask(
        @RoutingKey
        String taskId,
        Boolean completed
) {
    @CommandHandler
    public void handle(CompleteTaskCommand command, EventAppender eventAppender) {
        if(this.completed) {
            throw new IllegalStateException("Task is already completed");
        }

        eventAppender.append(new TaskCompleted(command.identifier(), command.taskId()));
    }

    @EventSourcingHandler
    public ImmutableTask on(TaskCompleted event) {
        return new ImmutableTask(event.taskId(), true);
    }

    public String getTaskId() {
        return taskId;
    }

    public Boolean isCompleted() {
        return completed;
    }
}
