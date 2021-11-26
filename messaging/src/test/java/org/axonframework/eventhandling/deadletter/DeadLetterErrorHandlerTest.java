/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventExecutionException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DeadLetterErrorHandler}.
 *
 * @author Steven van Beelen
 */
class DeadLetterErrorHandlerTest {

    private static final EventMessage<Object> TEST_EVENT = GenericEventMessage.asEventMessage("some-payload");

    private DeadLetterQueue<EventMessage<?>> deadLetterQueue;

    private DeadLetterErrorHandler testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        deadLetterQueue = mock(DeadLetterQueue.class);

        testSubject = DeadLetterErrorHandler.builder()
                                            .deadLetterQueue(deadLetterQueue)
                                            .build();
    }

    @Test
    void testOnErrorAddsDeadLetter() throws Exception {
        String expectedSequenceIdentifier = "seqId";
        RuntimeException expectedFailure = new RuntimeException();

        Exception testException = new EventExecutionException(
                "some-message", expectedFailure, expectedSequenceIdentifier, "processingGroup"
        );

        testSubject.onError(testException, TEST_EVENT, mock(EventMessageHandler.class));

        //noinspection unchecked
        ArgumentCaptor<DeadLetter<EventMessage<?>>> deadLetterCaptor = ArgumentCaptor.forClass(DeadLetter.class);

        verify(deadLetterQueue).add(deadLetterCaptor.capture());

        DeadLetter<EventMessage<?>> result = deadLetterCaptor.getValue();

        assertEquals(expectedSequenceIdentifier, result.sequenceIdentifier());
        assertEquals(TEST_EVENT, result.deadLetter());
        assertEquals(expectedFailure, result.failure());
    }

    @Test
    void testOnErrorIgnoresNonEventExecutionExceptions() throws Exception {
        testSubject.onError(new RuntimeException(), TEST_EVENT, mock(EventMessageHandler.class));

        verifyNoInteractions(deadLetterQueue);
    }

    @Test
    void testBuildWithNullDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetterErrorHandler.Builder testSubject = DeadLetterErrorHandler.builder();

        assertThrows(AxonConfigurationException.class, () -> testSubject.deadLetterQueue(null));
    }

    @Test
    void testBuildWithoutDeadLetterQueueThrowsAxonConfigurationException() {
        DeadLetterErrorHandler.Builder testSubject = DeadLetterErrorHandler.builder();

        assertThrows(AxonConfigurationException.class, testSubject::build);
    }
}