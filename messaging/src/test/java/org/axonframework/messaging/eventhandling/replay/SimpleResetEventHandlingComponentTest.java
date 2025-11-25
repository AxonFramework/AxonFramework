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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link SimpleResetEventHandlingComponent} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleResetEventHandlingComponentTest {

    private static final StubProcessingContext STUB_PROCESSING_CONTEXT = new StubProcessingContext();

    @Nested
    class SupportsResetTest {

        @Test
        void returnsFalseWhenNoResetHandlersRegistered() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());

            // then
            assertThat(component.supportsReset()).isFalse();
        }

        @Test
        void returnsTrueWhenResetHandlerIsRegistered() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> MessageStream.empty());

            // then
            assertThat(component.supportsReset()).isTrue();
        }

        @Test
        void returnsTrueWhenMultipleResetHandlersAreRegistered() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> MessageStream.empty());
            component.subscribe((resetContext, ctx) -> MessageStream.empty());

            // then
            assertThat(component.supportsReset()).isTrue();
        }
    }

    @Nested
    class HandleResetContextTest {

        @Test
        void invokesRegisteredResetHandler() {
            // given
            var resetHandlerInvoked = new AtomicBoolean(false);
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> {
                resetHandlerInvoked.set(true);
                return MessageStream.empty();
            });

            ResetContext resetContext = createResetContext("test-payload");

            // when
            component.handle(resetContext, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(resetHandlerInvoked).isTrue();
        }

        @Test
        void invokesAllRegisteredResetHandlersSequentially() {
            // given
            List<Integer> invocationOrder = new ArrayList<>();
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> {
                invocationOrder.add(1);
                return MessageStream.empty();
            });
            component.subscribe((resetContext, ctx) -> {
                invocationOrder.add(2);
                return MessageStream.empty();
            });
            component.subscribe((resetContext, ctx) -> {
                invocationOrder.add(3);
                return MessageStream.empty();
            });

            ResetContext resetContext = createResetContext(null);

            // when
            component.handle(resetContext, STUB_PROCESSING_CONTEXT).asCompletableFuture().join();

            // then
            assertThat(invocationOrder).containsExactly(1, 2, 3);
        }

        @Test
        void resetHandlerReceivesCorrectPayload() {
            // given
            var expectedPayload = "my-reset-payload";
            var capturedPayload = new AtomicReference<>();

            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> {
                capturedPayload.set(resetContext.payload());
                return MessageStream.empty();
            });

            ResetContext resetContext = createResetContext(expectedPayload);

            // when
            component.handle(resetContext, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(capturedPayload.get()).isEqualTo(expectedPayload);
        }

        @Test
        void returnsEmptyStreamWhenNoHandlersRegistered() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            ResetContext resetContext = createResetContext(null);

            // when
            var result = component.handle(resetContext, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(result.asCompletableFuture().join()).isNull();
        }

        @Test
        void returnsEmptyStreamAfterInvokingHandlers() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> MessageStream.empty());
            ResetContext resetContext = createResetContext(null);

            // when
            var result = component.handle(resetContext, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(result.asCompletableFuture().join()).isNull();
        }
    }

    @Nested
    class DelegationTest {

        @Test
        void delegatesEventHandlingToWrappedComponent() {
            // given
            var eventHandlerInvoked = new AtomicBoolean(false);
            var delegate = new SimpleEventHandlingComponent();
            delegate.subscribe(new QualifiedName(String.class), (event, ctx) -> {
                eventHandlerInvoked.set(true);
                return MessageStream.empty();
            });

            var component = new SimpleResetEventHandlingComponent(delegate);

            // when
            var sampleEvent = org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage("test");
            component.handle(sampleEvent, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(eventHandlerInvoked).isTrue();
        }

        @Test
        void delegatesSupportedEventsToWrappedComponent() {
            // given
            var expectedEventName = new QualifiedName(String.class);
            var delegate = new SimpleEventHandlingComponent();
            delegate.subscribe(expectedEventName, (event, ctx) -> MessageStream.empty());

            var component = new SimpleResetEventHandlingComponent(delegate);

            // then
            assertThat(component.supportedEvents()).contains(expectedEventName);
            assertThat(component.supports(expectedEventName)).isTrue();
        }
    }

    @Nested
    class FluentApiTest {

        @Test
        void subscribeReturnsThisForChaining() {
            // given
            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());

            // when
            var result = component.subscribe((resetContext, ctx) -> MessageStream.empty());

            // then
            assertThat(result).isSameAs(component);
        }

        @Test
        void allowsMultipleSubscriptionsViaChaining() {
            // given
            var handler1Invoked = new AtomicBoolean(false);
            var handler2Invoked = new AtomicBoolean(false);

            var component = new SimpleResetEventHandlingComponent(new SimpleEventHandlingComponent());
            component.subscribe((resetContext, ctx) -> {
                        handler1Invoked.set(true);
                        return MessageStream.empty();
                    })
                    .subscribe((resetContext, ctx) -> {
                        handler2Invoked.set(true);
                        return MessageStream.empty();
                    });

            ResetContext resetContext = createResetContext(null);

            // when
            component.handle(resetContext, STUB_PROCESSING_CONTEXT).asCompletableFuture().join();

            // then
            assertThat(handler1Invoked).isTrue();
            assertThat(handler2Invoked).isTrue();
        }
    }

    private static ResetContext createResetContext(Object payload) {
        return new GenericResetContext(new MessageType(ResetContext.class), payload);
    }
}
