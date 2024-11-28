/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.serialization;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.junit.jupiter.api.*;

import java.lang.reflect.Type;
import java.util.Map;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SerializingPayloadConverterTest {

    private Serializer serializer;

    private SerializingPayloadConverter testSubject;

    @BeforeEach
    void setUp() {
        serializer = mock();

        testSubject = new SerializingPayloadConverter(serializer);
    }

    @Test
    void convertDelegatesToSerializer() {
        when(serializer.convert(any(), any(Type.class))).thenReturn("test");
        Message<Object> original = new GenericMessage<>(fromDottedName("test.message"), "test", Map.of("key", "value"));

        Message<String> actual = testSubject.convertPayload(original, String.class);

        assertEquals(original.getIdentifier(), actual.getIdentifier());
        assertEquals(original.getMetaData(), actual.getMetaData());
        assertEquals("test", original.getPayload());
    }
}