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

package org.axonframework.saga.annotation;

import com.lmax.disruptor.EventFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaCreationPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Placeholder for information required by the AsyncSagaEventProcessor for processing Events.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AsyncSagaProcessingEvent {

    private EventMessage publishedEvent;
    private final List<SagaMethodMessageHandler> handlers = new ArrayList<>();
    private Class<? extends AbstractAnnotatedSaga> sagaType;
    private AbstractAnnotatedSaga newSaga;
    private final AsyncSagaCreationElector elector = new AsyncSagaCreationElector();
    private SagaMethodMessageHandler creationHandler;
    private AssociationValue initialAssociationValue;
    private final Set<AssociationValue> associationValues = new HashSet<>();

    /**
     * Returns the event that has been published on the EventBus. This is the event that will trigger Sagas.
     *
     * @return the event that has been published on the EventBus
     */
    public EventMessage<?> getPublishedEvent() {
        return publishedEvent;
    }

    /**
     * Returns the handler that can process the published Event.
     *
     * @return the handler that can process the published Event
     */
    public List<SagaMethodMessageHandler> getHandlers() {
        return handlers;
    }

    /**
     * Returns the type of Saga being processed.
     *
     * @return the type of Saga being processed
     */
    public Class<? extends Saga> getSagaType() {
        return sagaType;
    }

    /**
     * Forces the current thread to wait for the voting to complete if it is responsible for creating the Saga. As soon
     * as an invocation has been recorded, the waiting thread is released.
     *
     * @param didEventInvocation  indicates whether the current processor found a Saga to process
     * @param processorCount      The total number of processors expected to cast a vote
     * @param ownsNewSagaInstance Indicates whether the current processor "owns" the to-be-created saga instance.
     * @return <code>true</code> if the current processor should create the new instance, <code>false</code> otherwise.
     */
    public boolean waitForSagaCreationVote(boolean didEventInvocation, int processorCount,
                                           boolean ownsNewSagaInstance) {
        return elector.waitForSagaCreationVote(didEventInvocation, processorCount, ownsNewSagaInstance);
    }

    /**
     * Returns the new Saga instance that should be used when processing an Event that creates a new Saga instance
     *
     * @return the new Saga instance
     */
    public AbstractAnnotatedSaga getNewSaga() {
        return newSaga;
    }

    /**
     * Reset this entry for processing a new EventMessage
     *
     * @param nextEvent        The EventMessage to process
     * @param nextSagaType     The type of Saga to process this EventMessage
     * @param nextHandlers     The handlers potentially handling this message
     * @param nextSagaInstance The saga instance to use when a new saga is to be created
     */
    public void reset(EventMessage nextEvent, Class<? extends AbstractAnnotatedSaga> nextSagaType,
                      List<SagaMethodMessageHandler> nextHandlers, AbstractAnnotatedSaga nextSagaInstance) {
        this.elector.clear();
        this.publishedEvent = nextEvent;
        this.sagaType = nextSagaType;
        this.handlers.clear();
        this.handlers.addAll(nextHandlers);
        this.creationHandler = SagaMethodMessageHandler.noHandler();
        this.initialAssociationValue = null;
        this.associationValues.clear();
        for (SagaMethodMessageHandler handler : handlers) {
            if (!this.creationHandler.isHandlerAvailable() && handler.getCreationPolicy() != SagaCreationPolicy.NONE) {
                this.creationHandler = handler;
                this.initialAssociationValue = creationHandler.getAssociationValue(nextEvent);
            }
            this.associationValues.add(handler.getAssociationValue(nextEvent));
        }
        this.newSaga = nextSagaInstance;
    }

    /**
     * Returns the event handler which is used to create a new saga instance based on the incoming event.
     *
     * @return a SagaMethodMessageHandler instance describing the creation rules
     */
    public SagaMethodMessageHandler getCreationHandler() {
        return creationHandler;
    }

    /**
     * Returns the association to assign to an event when handling an incoming event that creates a Saga. If the
     * incoming event is not a creation event, this method returns <code>null</code>.
     *
     * @return the association to assign to an event when handling an incoming event that creates a Saga
     */
    public AssociationValue getInitialAssociationValue() {
        return initialAssociationValue;
    }

    /**
     * Returns all association values that could potentially link a saga instance with the incoming event.
     *
     * @return all association values that could potentially link a saga instance with the incoming event
     */
    public Set<AssociationValue> getAssociationValues() {
        return associationValues;
    }

    /**
     * The Factory class for AsyncSagaProcessingEvent instances.
     */
    static class Factory implements EventFactory<AsyncSagaProcessingEvent> {

        @Override
        public AsyncSagaProcessingEvent newInstance() {
            return new AsyncSagaProcessingEvent();
        }
    }
}
