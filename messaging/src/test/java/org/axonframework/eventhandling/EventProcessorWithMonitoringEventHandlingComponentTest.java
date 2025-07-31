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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.axonframework.eventhandling.DomainEventTestUtils.createDomainEvent;
import static org.axonframework.eventhandling.DomainEventTestUtils.createDomainEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventProcessorWithMonitoringEventHandlingComponentTest {

    @Test
    void expectCallbackForAllMessages() throws Exception {
        List<DomainEventMessage<?>> events = createDomainEvents(2);
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

        EventHandlingComponent mockEventHandlingComponent = mock(EventHandlingComponent.class);
        when(mockEventHandlingComponent.handle(any(), any())).thenReturn(MessageStream.empty());
        when(mockEventHandlingComponent.supports(any())).thenReturn(true);
        when(mockEventHandlingComponent.sequenceIdentifierFor(any(), any())).thenAnswer(
                e -> e.getArgument(0, EventMessage.class).getIdentifier()
        );

        // Also test that the mechanism used to call the monitor can deal with the message in the unit of work being
        // modified during processing
        MessageHandlerInterceptor<EventMessage<?>> interceptor = new MessageHandlerInterceptor<>() {
            @Override
            public Object handle(
                    @Nonnull LegacyUnitOfWork<? extends EventMessage<?>> unitOfWork,
                    @Nonnull ProcessingContext context,
                    @Nonnull InterceptorChain interceptorChain)
                    throws Exception {
                unitOfWork.transformMessage(m -> createDomainEvent());
                return interceptorChain.proceedSync(
                        context);
            }

            @Override
            public <M extends EventMessage<?>, R extends Message<?>> MessageStream<R> interceptOnHandle(
                    @Nonnull M message,
                    @Nonnull ProcessingContext context,
                    @Nonnull InterceptorChain<M, R> interceptorChain) {
                var event = createDomainEvent();
                //noinspection unchecked
                return interceptorChain.proceed((M) event,
                                                context);
            }
        };
        var eventHandlingComponent = new InterceptingEventHandlingComponent(
                new MessageHandlerInterceptors(List.of(interceptor)),
                new MonitoringEventHandlingComponent(
                        messageMonitor,
                        mockEventHandlingComponent)
        );
        TestEventProcessor testSubject = new TestEventProcessor(eventHandlingComponent);

        testSubject.processInBatchingUnitOfWork(events);

        assertTrue(pending.isEmpty(), "Not all events were presented to monitor");
    }

    private static class TestEventProcessor implements EventProcessor {

        private static final SimpleUnitOfWorkFactory UNIT_OF_WORK_FACTORY = new SimpleUnitOfWorkFactory();
        private final ProcessorEventHandlingComponents processorEventHandlingComponents;

        private TestEventProcessor(EventHandlingComponent eventHandlingComponent) {
            this.processorEventHandlingComponents = new ProcessorEventHandlingComponents(
                    List.of(eventHandlingComponent)
            );
        }

        @Override
        public String getName() {
            return "test";
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

        void processInBatchingUnitOfWork(List<? extends EventMessage<?>> eventMessages) {
            var unitOfWork = UNIT_OF_WORK_FACTORY.create();
            unitOfWork.executeWithResult(ctx -> processorEventHandlingComponents.handle(
                    eventMessages, ctx).asCompletableFuture()
            ).join();
        }
    }
}

