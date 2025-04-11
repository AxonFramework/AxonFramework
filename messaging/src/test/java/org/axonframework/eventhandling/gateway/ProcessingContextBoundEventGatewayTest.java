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

package org.axonframework.eventhandling.gateway;

import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.mockito.Mockito.*;

class ProcessingContextBoundEventGatewayTest {

    private final ProcessingContext mockProcessingContext = new StubProcessingContext();
    private final EventGateway mockDelegate = mock(EventGateway.class);
    private final ProcessingContextBoundEventGateway testSubject =
            new ProcessingContextBoundEventGateway(mockDelegate, mockProcessingContext);

    @Test
    void usesBoundProcessingContextToPublishToDelegateWithList() {
        // When
        testSubject.publish(List.of("test"));

        // Then
        verify(mockDelegate).publish(mockProcessingContext, List.of("test"));
    }

    @Test
    void usesBoundProcessingContextToPublishToDelegateWithVararg() {
        // When
        testSubject.publish("test");

        // Then
        verify(mockDelegate).publish(mockProcessingContext, List.of("test"));
    }

    @Test
    void usesProvidedProcessingContextToPublishToDelegateWithList() {
        // Given
        ProcessingContext processingContext = mock(ProcessingContext.class);

        // When
        testSubject.publish(processingContext, List.of("test"));

        // Then
        verify(mockDelegate).publish(processingContext, List.of("test"));
    }

    @Test
    void usesProvidedProcessingContextToPublishToDelegateWithVararg() {
        // Given
        ProcessingContext processingContext = mock(ProcessingContext.class);

        // When
        testSubject.publish(processingContext, "test");

        // Then
        verify(mockDelegate).publish(processingContext, List.of("test"));
    }

    @Test
    void forProcessingContextReturnsSelfWhenSameContext() {
        // When
        EventGateway result = testSubject.forProcessingContext(mockProcessingContext);

        // Then
        Assertions.assertSame(testSubject, result);
    }

    @Test
    void forProcessingContextReturnsNewInstanceWhenDifferentContext() {
        // Given
        ProcessingContext processingContext = mock(ProcessingContext.class);

        // When
        EventGateway result = testSubject.forProcessingContext(processingContext);

        // Then
        Assertions.assertNotSame(testSubject, result);
    }
}