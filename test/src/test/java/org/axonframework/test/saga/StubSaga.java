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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.EndSaga;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.SagaLifecycle;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allard Buijze
 */
public class StubSaga {

    private static final int TRIGGER_DURATION_MINUTES = 10;
    private transient StubGateway stubGateway;
    private transient EventBus eventBus;
    private transient EventScheduler scheduler;
    private List<Object> handledEvents = new ArrayList<>();
    private ScheduleToken timer;

    @StartSaga
    @SagaEventHandler(associationProperty = "identifier")
    public void handleSagaStart(TriggerSagaStartEvent event, EventMessage<TriggerSagaStartEvent> message) {
        handledEvents.add(event);
        timer = scheduler.schedule(Duration.ofMinutes(TRIGGER_DURATION_MINUTES),
                                   new GenericEventMessage<>(new TimerTriggeredEvent(event.getIdentifier())));
    }

    @StartSaga(forceNew = true)
    @SagaEventHandler(associationProperty = "identifier")
    public void handleForcedSagaStart(ForceTriggerSagaStartEvent event) {
        handledEvents.add(event);
        timer = scheduler.schedule(Duration.ofMinutes(TRIGGER_DURATION_MINUTES),
                                   new GenericEventMessage<>(new TimerTriggeredEvent(event.getIdentifier())));
    }

    @SagaEventHandler(associationProperty = "identifier")
    public void handleEvent(TriggerExistingSagaEvent event) {
        handledEvents.add(event);
        eventBus.publish(new GenericEventMessage<>(new SagaWasTriggeredEvent(this)));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "identifier")
    public void handleEndEvent(TriggerSagaEndEvent event) {
        handledEvents.add(event);
    }

    @SagaEventHandler(associationProperty = "identifier")
    public void handleFalseEvent(TriggerExceptionWhileHandlingEvent event) {
        handledEvents.add(event);
        throw new RuntimeException("This is a mock exception");
    }

    @SagaEventHandler(associationProperty = "identifier")
    public void handleTriggerEvent(TimerTriggeredEvent event) {
        handledEvents.add(event);
        String result = stubGateway.send("Say hi!");
        if (result != null) {
            stubGateway.send(result);
        }
    }

    @SagaEventHandler(associationProperty = "identifier")
    public void handleResetTriggerEvent(ResetTriggerEvent event) {
        handledEvents.add(event);
        scheduler.cancelSchedule(timer);
        timer = scheduler.schedule(Duration.ofMinutes(TRIGGER_DURATION_MINUTES),
                                   new GenericEventMessage<>(new TimerTriggeredEvent(event.getIdentifier())));
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public EventScheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(EventScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void setStubGateway(StubGateway stubGateway) {
        this.stubGateway = stubGateway;
    }

    public void associateWith(String key, String value) {
        SagaLifecycle.associateWith(key, value);
    }

    public void removeAssociationWith(String key, String value) {
        SagaLifecycle.removeAssociationWith(key, value);
    }

    public void end() {
        SagaLifecycle.end();
    }
}
