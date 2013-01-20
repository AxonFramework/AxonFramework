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

package org.axonframework.saga.annotation;

import com.lmax.disruptor.EventFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;

/**
 * Placeholder for information required by the AsyncSagaEventProcessor for processing Events.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AsyncSagaProcessingEvent {

    private EventMessage publishedEvent;
    private SagaMethodMessageHandler handler;
    private Class<? extends AbstractAnnotatedSaga> sagaType;
    private AbstractAnnotatedSaga newSaga;
    private AssociationValue associationValue;
    private final AsyncSagaCreationElector elector = new AsyncSagaCreationElector();

    /**
     * Returns the event that has been published on the EventBus. This is the event that will trigger Sagas.
     *
     * @return the event that has been published on the EventBus
     */
    public EventMessage getPublishedEvent() {
        return publishedEvent;
    }

    /**
     * Returns the handler that can process the published Event.
     *
     * @return the handler that can process the published Event
     */
    public SagaMethodMessageHandler getHandler() {
        return handler;
    }

    /**
     * Returns the association value based on the handler.
     *
     * @return the association value based on the handler
     */
    public AssociationValue getAssociationValue() {
        if (handler == null) {
            return null;
        }
        return associationValue;
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
     * @param nextEvent           The EventMessage to process
     * @param nextSagaType        The type of Saga to process this EventMessage
     * @param nextHandler         The handler handling this message
     * @param nextSagaInstance The saga instance to use when a new saga is to be created
     */
    public void reset(EventMessage nextEvent, Class<? extends AbstractAnnotatedSaga> nextSagaType,
                      SagaMethodMessageHandler nextHandler, AbstractAnnotatedSaga nextSagaInstance) {
        this.elector.clear();
        this.publishedEvent = nextEvent;
        this.sagaType = nextSagaType;
        this.handler = nextHandler;
        this.newSaga = nextSagaInstance;
        this.associationValue = nextHandler.getAssociationValue(nextEvent);
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
