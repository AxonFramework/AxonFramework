/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import static org.axonframework.utils.EventTestUtils.createEvent;
import static org.axonframework.utils.EventTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractEventProcessorTest {

    @Test
    void expectCallbackForAllMessages() throws Exception {
        List<DomainEventMessage<?>> events = createEvents(2);
        Set<DomainEventMessage<?>> pending = new HashSet<>(events);
        MessageMonitor<EventMessage<?>> messageMonitor = (message) -> new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                if (!pending.contains(message)) {
                    fail("Message was presented to monitor twice: " + message);
                }
                pending.remove(message);
            }

            @Override
            public void reportFailure(Throwable cause) {
                fail("Test expects 'reportSuccess' to be called");
            }

            @Override
            public void reportIgnored() {
                fail("Test expects 'reportSuccess' to be called");
            }
        };

        EventMessageHandler mockHandler = mock(EventMessageHandler.class);
        EventHandlerInvoker eventHandlerInvoker = SimpleEventHandlerInvoker.builder()
                                                                           .eventHandlers(mockHandler)
                                                                           .build();
        TestEventProcessor testSubject = TestEventProcessor.builder()
                                                           .name("test")
                                                           .eventHandlerInvoker(eventHandlerInvoker)
                                                           .messageMonitor(messageMonitor)
                                                           .build();

        // Also test that the mechanism used to call the monitor can deal with the message in the unit of work being
        // modified during processing
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            unitOfWork.transformMessage(m -> createEvent());
            return interceptorChain.proceed();
        });

        testSubject.processInBatchingUnitOfWork(events);

        assertTrue(pending.isEmpty(), "Not all events were presented to monitor");
    }

    private static class TestEventProcessor extends AbstractEventProcessor {

        private TestEventProcessor(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public void start() {
        }

        @Override
        public void shutDown() {
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        void processInBatchingUnitOfWork(List<? extends EventMessage<?>> eventMessages) throws Exception {
            processInUnitOfWork(eventMessages, new BatchingUnitOfWork<>(eventMessages));
        }

        private static class Builder extends AbstractEventProcessor.Builder {

            public Builder() {
                super.rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE);
            }

            @Override
            public Builder name(@Nonnull String name) {
                super.name(name);
                return this;
            }

            @Override
            public Builder eventHandlerInvoker(@Nonnull EventHandlerInvoker eventHandlerInvoker) {
                super.eventHandlerInvoker(eventHandlerInvoker);
                return this;
            }

            @Override
            public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
                super.messageMonitor(messageMonitor);
                return this;
            }

            private TestEventProcessor build() {
                return new TestEventProcessor(this);
            }
        }
    }
}

