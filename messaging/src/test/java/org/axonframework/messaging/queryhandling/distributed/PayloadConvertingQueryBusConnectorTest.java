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

package org.axonframework.messaging.queryhandling.distributed;

import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.queryhandling.distributed.PayloadConvertingQueryBusConnector;
import org.axonframework.messaging.queryhandling.distributed.QueryBusConnector;
import org.junit.jupiter.api.*;

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
}
