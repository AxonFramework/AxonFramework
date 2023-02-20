/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ChildForwardingCommandMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class ChildForwardingCommandMessageHandlingMemberTest {

    private MessageHandlingMember<Object> childMember;

    private ChildForwardingCommandMessageHandlingMember<Object, Object> testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        childMember = mock(MessageHandlingMember.class);

        testSubject = new ChildForwardingCommandMessageHandlingMember<>(
                Collections.emptyList(), childMember, (msg, parent) -> parent
        );
    }

    @Test
    void canHandleMessageTypeIsDelegatedToChildHandler() {
        when(childMember.canHandleMessageType(any())).thenReturn(true);

        assertTrue(testSubject.canHandleMessageType(CommandMessage.class));

        verify(childMember).canHandleMessageType(CommandMessage.class);
    }

    @Test
    void hasAnnotationIsDelegatedToChildHandler() {
        when(childMember.hasAnnotation(any())).thenReturn(true);

        assertTrue(testSubject.hasAnnotation(CommandHandler.class));

        verify(childMember).hasAnnotation(CommandHandler.class);
    }

    @Test
    void attributeIsDelegatedToChildHandler() {
        AggregateCreationPolicy expectedPolicy = AggregateCreationPolicy.NEVER;
        when(childMember.attribute(HandlerAttributes.AGGREGATE_CREATION_POLICY))
                .thenReturn(Optional.of(expectedPolicy));

        Optional<Object> result = testSubject.attribute(HandlerAttributes.AGGREGATE_CREATION_POLICY);

        assertTrue(result.isPresent());
        assertEquals(expectedPolicy, result.get());

        verify(childMember).attribute(HandlerAttributes.AGGREGATE_CREATION_POLICY);
    }
}