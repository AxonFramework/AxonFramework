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

package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordingLoggingErrorHandlerTest {

    @Test
    void testEmptyOnCreation() {
        RecordingLoggingErrorHandler testSubject = new RecordingLoggingErrorHandler();

        assertFalse(testSubject.getException().isPresent());
    }

    @Test
    void testLogExceptionOnError() {
        RecordingLoggingErrorHandler testSubject = new RecordingLoggingErrorHandler();
        EventMessageHandler eventMessageHandler = mock(EventMessageHandler.class);
        doReturn(StubSaga.class).when(eventMessageHandler).getTargetType();
        IllegalArgumentException expectedException = new IllegalArgumentException("This argument is illegal");

        testSubject.onError(expectedException, new GenericEventMessage<>("test"), eventMessageHandler);

        assertTrue(testSubject.getException().isPresent());
        assertEquals(expectedException, testSubject.getException().get());
    }

    @Test
    void testClearExceptionOnStartRecording() {
        RecordingLoggingErrorHandler testSubject = new RecordingLoggingErrorHandler();
        EventMessageHandler eventMessageHandler = mock(EventMessageHandler.class);
        doReturn(StubSaga.class).when(eventMessageHandler).getTargetType();
        IllegalArgumentException expectedException = new IllegalArgumentException("This argument is illegal");

        testSubject.onError(expectedException, new GenericEventMessage<>("test"), eventMessageHandler);
        testSubject.startRecording();

        assertFalse(testSubject.getException().isPresent());
    }
}