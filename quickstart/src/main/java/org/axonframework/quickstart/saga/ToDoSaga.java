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

package org.axonframework.quickstart.saga;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.quickstart.api.MarkToDoItemOverdueCommand;
import org.axonframework.quickstart.api.ToDoItemCompletedEvent;
import org.axonframework.quickstart.api.ToDoItemCreatedEvent;
import org.axonframework.quickstart.api.ToDoItemDeadlineExpiredEvent;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.EndSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;
import org.joda.time.Duration;

import javax.annotation.Resource;

/**
 * @author Allard Buijze
 */
public class ToDoSaga extends AbstractAnnotatedSaga {

    private static final long serialVersionUID = 1798051388403504162L;

    private transient CommandGateway commandGateway;
    private transient EventScheduler eventScheduler;

    private ScheduleToken deadline;

    @StartSaga
    @SagaEventHandler(associationProperty = "todoId")
    public void onToDoItemCreated(ToDoItemCreatedEvent event) {
        deadline = eventScheduler.schedule(Duration.standardSeconds(2),
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

    @Resource
    public void setCommandGateway(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Resource
    public void setEventScheduler(EventScheduler eventScheduler) {
        this.eventScheduler = eventScheduler;
    }
}
