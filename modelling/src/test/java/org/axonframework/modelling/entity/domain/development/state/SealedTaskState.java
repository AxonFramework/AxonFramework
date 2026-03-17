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

package org.axonframework.modelling.entity.domain.development.state;

import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.modelling.entity.domain.development.events.TaskAssigned;
import org.axonframework.modelling.entity.domain.development.events.TaskCompleted;
import org.axonframework.modelling.entity.domain.development.events.TaskCreated;

public sealed interface SealedTaskState {

    record InitialTask() implements SealedTaskState {

        @EventHandler
        private CreatedTask on(TaskCreated event) {
            return new CreatedTask(event.taskId());
        }
    }

    record CreatedTask(String taskId) implements SealedTaskState {

        @EventHandler
        private AssignedTask on(TaskAssigned event) {
            return new AssignedTask(event.taskId(), event.assignee());
        }
    }

    record AssignedTask(String taskId, String assignee) implements SealedTaskState {

        @EventHandler
        private CompletedTask on(TaskCompleted event) {
            return new CompletedTask(event.taskId(), event.resolution());
        }
    }

    record CompletedTask(String taskId, String resolution) implements SealedTaskState {

    }
}
