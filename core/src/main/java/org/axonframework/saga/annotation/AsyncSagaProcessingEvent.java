/*
 * Copyright (c) 2010-2011. Axon Framework
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
class AsyncSagaProcessingEvent {

    private EventMessage publishedEvent;
    private HandlerConfiguration handler;
    private Class<? extends AbstractAnnotatedSaga> sagaType;
    private AbstractAnnotatedSaga newSaga;
    final AsyncSagaCreationElector elector = new AsyncSagaCreationElector();

    public EventMessage getPublishedEvent() {
        return publishedEvent;
    }

    public void setPublishedEvent(EventMessage publishedEvent) {
        this.publishedEvent = publishedEvent;
    }

    public HandlerConfiguration getHandler() {
        return handler;
    }

    public void setHandler(HandlerConfiguration handler) {
        this.handler = handler;
    }

    public AssociationValue getAssociationValue() {
        if (handler == null) {
            return null;
        }
        return handler.getAssociationValue();
    }

    public Class<? extends Saga> getSagaType() {
        return sagaType;
    }

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

    public void setNewSaga(AbstractAnnotatedSaga newSaga) {
        this.newSaga = newSaga;
    }

    public AbstractAnnotatedSaga getNewSaga() {
        return newSaga;
    }

    static class Factory implements EventFactory<AsyncSagaProcessingEvent> {
        @Override
        public AsyncSagaProcessingEvent newInstance() {
            return new AsyncSagaProcessingEvent();
        }
    }
}
