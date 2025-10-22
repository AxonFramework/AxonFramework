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

package org.axonframework.queryhandling.distributed;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayloadConvertingQueryBusConnectorTest {

    private static final String ORIGINAL_PAYLOAD = "original";
    private static final byte[] CONVERTED_PAYLOAD = ORIGINAL_PAYLOAD.getBytes();
    private static final MessageType QUERY_TYPE = new MessageType("TestQuery");
    private static final MessageType RESPONSE_TYPE = new MessageType("TestResponse");

    private QueryBusConnector mockDelegate;
    private Converter mockConverter;
    private PayloadConvertingQueryBusConnector testSubject;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(QueryBusConnector.class);
        mockConverter = mock(Converter.class);
        testSubject = new PayloadConvertingQueryBusConnector(
                mockDelegate, new DelegatingMessageConverter(mockConverter), byte[].class
        );
    }

    @Test
    void constructorRequiresNonNullDelegate() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingQueryBusConnector(
                null, new DelegatingMessageConverter(mockConverter), byte[].class
        ));
    }

    @Test
    void constructorRequiresNonNullConverter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> new PayloadConvertingQueryBusConnector(mockDelegate, null, byte[].class));
    }

    @Test
    void constructorRequiresNonNullTargetType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new PayloadConvertingQueryBusConnector(
                mockDelegate, new DelegatingMessageConverter(mockConverter), null
        ));
    }

    @Test
    void convertingCallbackConvertsSuccessResultMessage() {
        // Given
        QueryBusConnector.Handler originalHandler = mock(QueryBusConnector.Handler.class);
        testSubject.onIncomingQuery(originalHandler);

        ArgumentCaptor<QueryBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.Handler.class);
        verify(mockDelegate).onIncomingQuery(handlerCaptor.capture());

        QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "ignored", RESPONSE_TYPE);
        QueryBusConnector.ResultCallback originalCallback = mock(QueryBusConnector.ResultCallback.class);

        handlerCaptor.getValue().query(testQuery, originalCallback);

        ArgumentCaptor<QueryBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.ResultCallback.class);
        verify(originalHandler).query(eq(testQuery), callbackCaptor.capture());

        QueryBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        QueryResponseMessage resultMessage = new GenericQueryResponseMessage(RESPONSE_TYPE, ORIGINAL_PAYLOAD);
        when(mockConverter.convert(ORIGINAL_PAYLOAD, (Type) byte[].class)).thenReturn(CONVERTED_PAYLOAD);

        convertingCallback.onSuccess(resultMessage);

        // Then
        ArgumentCaptor<QueryResponseMessage> messageCaptor = ArgumentCaptor.forClass(QueryResponseMessage.class);
        verify(originalCallback).onSuccess(messageCaptor.capture());

        Message convertedMessage = messageCaptor.getValue();
        assertArrayEquals(CONVERTED_PAYLOAD, (byte[]) convertedMessage.payload());
        assertEquals(resultMessage.type(), convertedMessage.type());
        assertEquals(resultMessage.metadata(), convertedMessage.metadata());
    }

    @Test
    void convertingCallbackHandlesNullResultMessage() {
        // Given
        QueryBusConnector.Handler originalHandler = mock(QueryBusConnector.Handler.class);
        testSubject.onIncomingQuery(originalHandler);

        ArgumentCaptor<QueryBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.Handler.class);
        verify(mockDelegate).onIncomingQuery(handlerCaptor.capture());

        QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "ignored", RESPONSE_TYPE);
        QueryBusConnector.ResultCallback originalCallback = mock(QueryBusConnector.ResultCallback.class);

        handlerCaptor.getValue().query(testQuery, originalCallback);

        ArgumentCaptor<QueryBusConnector.ResultCallback> callbackCaptor = ArgumentCaptor.forClass(QueryBusConnector.ResultCallback.class);
        verify(originalHandler).query(eq(testQuery), callbackCaptor.capture());

        QueryBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        convertingCallback.onSuccess(null);

        // Then
        verify(originalCallback).onSuccess(null);
        verify(mockConverter, never()).convert(any(), any());
    }

    @Test
    void convertingCallbackHandlesMessageWithNullPayload() {
        // Given
        QueryBusConnector.Handler originalHandler = mock(QueryBusConnector.Handler.class);
        testSubject.onIncomingQuery(originalHandler);

        ArgumentCaptor<QueryBusConnector.Handler> handlerCaptor = ArgumentCaptor.forClass(QueryBusConnector.Handler.class);
        verify(mockDelegate).onIncomingQuery(handlerCaptor.capture());

        QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "ignored", RESPONSE_TYPE);
        QueryBusConnector.ResultCallback originalCallback = mock(QueryBusConnector.ResultCallback.class);

        handlerCaptor.getValue().query(testQuery, originalCallback);

        ArgumentCaptor<QueryBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.ResultCallback.class);
        verify(originalHandler).query(eq(testQuery), callbackCaptor.capture());

        QueryBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        QueryResponseMessage resultMessageWithNullPayload = new GenericQueryResponseMessage(RESPONSE_TYPE, null);
        convertingCallback.onSuccess(resultMessageWithNullPayload);

        // Then
        verify(originalCallback).onSuccess(resultMessageWithNullPayload);
        verify(mockConverter, never()).convert(any(), any());
    }

    @Test
    void preservesResponseMetadataWhenConverting() {
        // Given
        Metadata originalMetadata = Metadata.with("key", "value");
        QueryBusConnector.Handler originalHandler = mock(QueryBusConnector.Handler.class);
        testSubject.onIncomingQuery(originalHandler);

        ArgumentCaptor<QueryBusConnector.Handler> handlerCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.Handler.class);
        verify(mockDelegate).onIncomingQuery(handlerCaptor.capture());

        QueryMessage testQuery = new GenericQueryMessage(QUERY_TYPE, "ignored", RESPONSE_TYPE);
        QueryBusConnector.ResultCallback originalCallback = mock(QueryBusConnector.ResultCallback.class);

        handlerCaptor.getValue().query(testQuery, originalCallback);

        ArgumentCaptor<QueryBusConnector.ResultCallback> callbackCaptor =
                ArgumentCaptor.forClass(QueryBusConnector.ResultCallback.class);
        verify(originalHandler).query(eq(testQuery), callbackCaptor.capture());

        QueryBusConnector.ResultCallback convertingCallback = callbackCaptor.getValue();

        // When
        QueryResponseMessage resultMessage = new GenericQueryResponseMessage(RESPONSE_TYPE, ORIGINAL_PAYLOAD, originalMetadata);
        when(mockConverter.convert(ORIGINAL_PAYLOAD, byte[].class)).thenReturn(CONVERTED_PAYLOAD);

        convertingCallback.onSuccess(resultMessage);

        // Then
        ArgumentCaptor<QueryResponseMessage> messageCaptor = ArgumentCaptor.forClass(QueryResponseMessage.class);
        verify(originalCallback).onSuccess(messageCaptor.capture());

        QueryResponseMessage captured = messageCaptor.getValue();
        assertEquals(originalMetadata, captured.metadata());
    }
}
