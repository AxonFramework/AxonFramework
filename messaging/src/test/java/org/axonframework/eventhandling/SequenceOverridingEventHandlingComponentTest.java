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
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link SequenceOverridingEventHandlingComponent}.
 */
class SequenceOverridingEventHandlingComponentTest {

    @Test
    void sequenceIdentifierForUsesPolicyWhenItProvidesSequence() {
        //given
        var policySequenceId = "policy-sequence-id";
        var delegateSequenceId = "delegate-sequence-id";
        SequencingPolicy policy = event -> Optional.of(policySequenceId);
        var delegate = getEventHandlingComponentWithSequenceId(delegateSequenceId);
        var testSubject = new SequenceOverridingEventHandlingComponent(policy, delegate);
        var testEvent = new GenericEventMessage<>(
                new MessageType("TestEvent"), 
                "test-payload"
        );

        //when
        var result = testSubject.sequenceIdentifierFor(testEvent, new StubProcessingContext());

        //then
        assertThat(result).isEqualTo(policySequenceId);
    }

    @Test
    void sequenceIdentifierForUsesDelegateWhenPolicyReturnsEmpty() {
        //given
        var delegateSequenceId = "delegate-sequence-id";
        SequencingPolicy policy = event -> Optional.empty();
        EventHandlingComponent delegate = getEventHandlingComponentWithSequenceId(delegateSequenceId);
        var testSubject = new SequenceOverridingEventHandlingComponent(policy, delegate);
        var testEvent = new GenericEventMessage<>(
                new MessageType("TestEvent"), 
                "test-payload"
        );

        //when
        var result = testSubject.sequenceIdentifierFor(testEvent, new StubProcessingContext());

        //then
        assertThat(result).isEqualTo(delegateSequenceId);
    }

    @Nonnull
    private EventHandlingComponent getEventHandlingComponentWithSequenceId(String delegateSequenceId) {
        return new EventHandlingComponent() {
            @Nonnull
            @Override
            public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
                return delegateSequenceId;
            }

            @Override
            public Set<QualifiedName> supportedEvents() {
                return Set.of();
            }

            @Override
            public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                             @Nonnull ProcessingContext context) {
                return MessageStream.empty();
            }

            @Override
            public EventHandlerRegistry subscribe(@Nonnull QualifiedName name, @Nonnull EventHandler eventHandler) {
                return this;
            }

            @Override
            public EventHandlerRegistry subscribe(@Nonnull Set<QualifiedName> names,
                                                  @Nonnull EventHandler eventHandler) {
                return this;
            }

            @Override
            public EventHandlerRegistry subscribe(@Nonnull EventHandlingComponent handlingComponent) {
                return this;
            }
        };
    }
}
