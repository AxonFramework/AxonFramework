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
package org.axonframework.messaging.annotation;

import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link WrappedMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class WrappedMessageHandlingMemberTest {

    private MessageHandlingMember<Object> mockedHandlingMember;
    private WrappedMessageHandlingMember<Object> testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        mockedHandlingMember = mock(MessageHandlingMember.class);

        testSubject = new WrappedMessageHandlingMember<Object>(mockedHandlingMember) {
        };
    }

    @Test
    void canHandleMessageType() {
        testSubject.canHandleMessageType(QueryMessage.class);
        verify(mockedHandlingMember).canHandleMessageType(QueryMessage.class);
    }

    @Test
    void attribute() {
        testSubject.attribute(HandlerAttributes.COMMAND_ROUTING_KEY);
        verify(mockedHandlingMember).attribute(HandlerAttributes.COMMAND_ROUTING_KEY);
    }
}