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

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import static org.axonframework.messaging.MessagingTestHelper.event;
import static org.junit.jupiter.api.Assertions.*;

class EventProcessorWithMonitoringEventHandlingComponentTest {

    @Test
    void expectCallbackForAllMessages() throws Exception {
        List<EventMessage<Integer>> events = EventTestUtils.createEvents(2);
        Set<EventMessage<Integer>> pending = new HashSet<>(events);
        MessageMonitor<EventMessage<?>> messageMonitor = (message) -> new MessageMonitor.MonitorCallback() {
            @Override
            public void reportSuccess() {
                if (!pending.contains(message)) {
                    fail("Message was presented to monitor twice: " + message + " or message was unknown.");
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

        EventHandlingComponent eventHandlingComponent = new SimpleEventHandlingComponent();
        eventHandlingComponent.subscribe(new QualifiedName(Integer.class), (e, c) -> MessageStream.empty());

        // Also test that the mechanism used to call the monitor can deal with the message in the unit of work being
        // modified during processing
        MessageHandlerInterceptor<EventMessage<?>> interceptor = (message, context, interceptorChain) ->
                interceptorChain.proceed(event(123), context);
        var interceptingEventHandlingComponent = new InterceptingEventHandlingComponent(
                List.of(), // List.of(interceptor),
                eventHandlingComponent
        );
        var decoratedEventHandlingComponent = new MonitoringEventHandlingComponent(
                messageMonitor,
                interceptingEventHandlingComponent
        );
        TestEventProcessor testSubject = new TestEventProcessor(decoratedEventHandlingComponent);

        testSubject.processInBatchingUnitOfWork(events);

        assertTrue(pending.isEmpty(), "Not all events were presented to monitor");
    }

    private static class TestEventProcessor implements EventProcessor {

        private final ProcessorEventHandlingComponents processorEventHandlingComponents;

        private TestEventProcessor(EventHandlingComponent eventHandlingComponent) {
            this.processorEventHandlingComponents = new ProcessorEventHandlingComponents(
                    List.of(eventHandlingComponent)
            );
        }

        @Override
        public String name() {
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

        void processInBatchingUnitOfWork(List<? extends EventMessage<?>> eventMessages)
                throws ExecutionException, InterruptedException, TimeoutException {
            var unitOfWork = UnitOfWorkTestUtils.aUnitOfWork();
            unitOfWork.executeWithResult(ctx -> processorEventHandlingComponents
                    .handle(eventMessages, ctx)
                    .asCompletableFuture()
            ).get(2, TimeUnit.SECONDS);
        }
    }
}

