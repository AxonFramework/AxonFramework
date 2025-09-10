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

package org.axonframework.messaging.responsetypes;

import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

class ConvertingResponseMessageTest {

    @Test
    void payloadIsConvertedToExpectedType() {
        QueryResponseMessage msg = new GenericQueryResponseMessage(
                new MessageType("query"), new String[]{"Some string result"}
        ).withMetadata(Metadata.with("test", "value"));
        QueryResponseMessage wrapped =
                new ConvertingResponseMessage<>(ResponseTypes.multipleInstancesOf(String.class), msg);

        assertEquals(List.class, wrapped.payloadType());
        assertEquals(singletonList("Some string result"), wrapped.payload());
        assertEquals("value", wrapped.metadata().get("test"));
    }

    @Test
    void illegalAccessPayloadWhenResultIsExceptional() {
        QueryResponseMessage msg = asResponseMessage(List.class, new RuntimeException());
        QueryResponseMessage wrapped =
                new ConvertingResponseMessage<>(ResponseTypes.multipleInstancesOf(String.class), msg);

        assertEquals(List.class, wrapped.payloadType());
        assertThrows(IllegalPayloadAccessException.class, wrapped::payload);
    }

    private static <R> QueryResponseMessage asResponseMessage(Class<R> declaredType, Throwable exception) {
        return new GenericQueryResponseMessage(new MessageType(exception.getClass()),
                                                 exception,
                                                 declaredType);
    }
}