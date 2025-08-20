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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link DefaultEventHandlingComponentBuilder} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DefaultEventHandlingComponentBuilderTest {

    private static final StubProcessingContext STUB_PROCESSING_CONTEXT = new StubProcessingContext();
    private final EventHandlingComponentBuilder.SequencingPolicyPhase builder = new DefaultEventHandlingComponentBuilder(new SimpleEventHandlingComponent());

    @Nested
    class SequencingPolicyTest {

        @Test
        void shouldApplySequencingPolicyAndReturnRequiredEventHandlerPhase() {
            // given
            var expectedIdentifier = "sequenceId";
            SequencingPolicy sequencingPolicy = event -> Optional.of(expectedIdentifier);

            // when
            var component = builder.sequencingPolicy(sequencingPolicy)
                                   .handles(new QualifiedName(String.class), (e, c) -> MessageStream.empty())
                                   .build();

            // then
            var sampleEvent = EventTestUtils.asEventMessage("sample");
            assertThat(
                    component.sequenceIdentifierFor(sampleEvent, STUB_PROCESSING_CONTEXT)).isSameAs(expectedIdentifier);
        }
    }

    @Nested
    class SequenceIdentifierTest {

        @Test
        void shouldApplySequencingPolicyAndReturnRequiredEventHandlerPhase() {
            // given
            var expectedIdentifier = "sequenceId";

            // when
            var component = builder.sequenceIdentifier(e -> expectedIdentifier)
                                   .handles(new QualifiedName(String.class), (e, c) -> MessageStream.empty())
                                   .build();

            // then
            var sampleEvent = EventTestUtils.asEventMessage("sample");
            assertThat(
                    component.sequenceIdentifierFor(sampleEvent, STUB_PROCESSING_CONTEXT)).isSameAs(expectedIdentifier);
        }
    }

    @Nested
    class HandlesTest {

        @Test
        void shouldRegisterEventHandlers() {
            // given
            var handler1Invoked = new AtomicBoolean();
            var handler2Invoked = new AtomicBoolean();
            var component = builder.handles(new QualifiedName(String.class), (e, c) -> {
                                          handler1Invoked.set(true);
                                          return MessageStream.empty();
                                      })
                                   .handles(new QualifiedName(String.class), (e, c) -> {
                                          handler2Invoked.set(true);
                                          return MessageStream.empty();
                                      })
                                   .build();

            //  when
            EventMessage<String> sampleMessage = EventTestUtils.asEventMessage("Message1");
            component.handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(handler1Invoked).isTrue();
            assertThat(handler2Invoked).isTrue();
        }
    }

    @Nested
    class DecoratedTest {

        @Test
        void shouldDecorateEventHandlingComponent() {
            // given
            SampleDecoration component = (SampleDecoration)
                    builder.handles(new QualifiedName(String.class), (e, c) -> MessageStream.empty())
                           .decorated(SampleDecoration::new)
                           .build();

            // when
            EventMessage<String> sampleMessage = EventTestUtils.asEventMessage("Message1");
            component.handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            // then
            assertThat(component.invoked).isTrue();
        }


        static class SampleDecoration extends DelegatingEventHandlingComponent {

            AtomicBoolean invoked = new AtomicBoolean();

            public SampleDecoration(@Nonnull EventHandlingComponent delegate) {
                super(delegate);
            }

            @Nonnull
            @Override
            public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                             @Nonnull ProcessingContext context) {
                invoked.set(true);
                return super.handle(event, context);
            }
        }
    }
}