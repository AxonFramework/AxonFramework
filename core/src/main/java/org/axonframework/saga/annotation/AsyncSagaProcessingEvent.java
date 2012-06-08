/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
    private HandlerConfiguration handler;
    private Class<? extends AbstractAnnotatedSaga> sagaType;
    private AbstractAnnotatedSaga newSaga;
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
     * Sets the event that has been published on the EventBus. This is the event that will trigger Sagas.
     *
     * @param publishedEvent the event that has been published on the EventBus
     */
    public void setPublishedEvent(EventMessage publishedEvent) {
        this.publishedEvent = publishedEvent;
    }

    /**
     * Returns the handler that can process the published Event.
     *
     * @return the handler that can process the published Event
     */
    public HandlerConfiguration getHandler() {
        return handler;
    }

    /**
     * Sets the handler that can process the published Event.
     *
     * @param handler the handler that can process the published Event
     */
    public void setHandler(HandlerConfiguration handler) {
        this.handler = handler;
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
        return handler.getAssociationValue();
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
     * Sets the type of Saga being processed
     *
     * @param sagaType the type of Saga being processed
     */
    public void setSagaType(Class<? extends AbstractAnnotatedSaga> sagaType) {
        this.sagaType = sagaType;
    }

    /**
     * Clears the event for the next processing cycle.
     */
    public void clear() {
        handler = null;
        publishedEvent = null;
        sagaType = null;
        elector.clear();
        newSaga = null;
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
     * Sets the new Saga instance that should be used when processing an Event that creates a new Saga instance
     *
     * @param newSaga the new Saga instance
     */
    public void setNewSaga(AbstractAnnotatedSaga newSaga) {
        this.newSaga = newSaga;
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
     * The Factory class for AsyncSagaProcessingEvent instances.
     */
    static class Factory implements EventFactory<AsyncSagaProcessingEvent> {
        @Override
        public AsyncSagaProcessingEvent newInstance() {
            return new AsyncSagaProcessingEvent();
        }
    }
}
