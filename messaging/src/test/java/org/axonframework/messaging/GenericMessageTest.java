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

package org.axonframework.messaging;

import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.correlation.ThrowingCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.serialization.ConversionException;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test correct operations of the {@link GenericMessage} class.
 *
 * @author Rene de Waele
 */
class GenericMessageTest extends MessageTestSuite<Message> {

    private final Map<String, String> correlationData = Metadata.from(Collections.singletonMap("foo", "bar"));

    private LegacyUnitOfWork<?> unitOfWork;

    @BeforeEach
    void setUp() {
        unitOfWork = mock(LegacyUnitOfWork.class);
        when(unitOfWork.getCorrelationData()).thenAnswer(invocation -> correlationData);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @Override
    protected Message buildDefaultMessage() {
        return new GenericMessage(TEST_IDENTIFIER, TEST_TYPE, TEST_PAYLOAD, TEST_PAYLOAD_TYPE, TEST_METADATA);
    }

    @Override
    protected <P> Message buildMessage(@Nullable P payload) {
        return new GenericMessage(new MessageType(ObjectUtils.nullSafeTypeOf(payload)), payload);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    void correlationDataAddedToNewMessage() {
        Message testMessage = new GenericMessage(new MessageType("message"), new Object());
        assertEquals(correlationData, new HashMap<>(testMessage.metadata()));

        Metadata newMetadata = Metadata.from(Collections.singletonMap("what", "ever"));
        Message testMessageWithMetadata =
                new GenericMessage(new MessageType("message"), new Object(), newMetadata);
        assertEquals(newMetadata.mergedWith(correlationData), testMessageWithMetadata.metadata());
    }

    @Test
    void whenCorrelationDataProviderThrowsException_thenCatchException() {
        unitOfWork = new LegacyDefaultUnitOfWork<>(
                new GenericEventMessage(new MessageType("event"), "Input 1")
        );
        CurrentUnitOfWork.set(unitOfWork);
        unitOfWork.registerCorrelationDataProvider(new ThrowingCorrelationDataProvider());
        ConversionException exception = new ConversionException("foo");

        Message result = new GenericMessage(new MessageType("exception"), exception);

        assertNotNull(result);
    }
}
