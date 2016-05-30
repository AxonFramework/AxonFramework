/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.quickstart.saga;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.saga.EndSaga;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.quickstart.api.MarkToDoItemOverdueCommand;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.quickstart.api.ToDoItemDeadlineExpiredEvent;

import javax.inject.Inject;
import java.time.Duration;

/**
 * @author Allard Buijze
 */
public class ToDoSaga {

    private static final long serialVersionUID = 1798051388403504162L;

    @Inject
    private transient CommandGateway commandGateway;
    @Inject
    private transient EventScheduler eventScheduler;

    private ScheduleToken deadline;

    @StartSaga
    @SagaEventHandler(associationProperty = "todoId")
    public void onToDoItemCreated(ToDoItemCreatedEvent event) {
        deadline = eventScheduler.schedule(Duration.ofSeconds(2),
                                           new ToDoItemDeadlineExpiredEvent(event.getTodoId()));
    }

    @SagaEventHandler(associationProperty = "todoId")
    public void onDeadlineExpired(ToDoItemDeadlineExpiredEvent event) {
        commandGateway.send(new MarkToDoItemOverdueCommand(event.getTodoId()));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "todoId")
    public void onToDoItemCompleted(ToDoItemCompletedEvent event) {
        if (deadline != null) {
            eventScheduler.cancelSchedule(deadline);
        }
    }

}
