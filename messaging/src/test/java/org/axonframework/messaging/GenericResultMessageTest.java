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

package org.axonframework.messaging;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import static org.axonframework.messaging.GenericResultMessage.asResultMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link GenericResultMessage}.
 *
 * @author Milan Savic
 */
class GenericResultMessageTest {

    @Test
    void exceptionalResult() {
        Throwable t = new Throwable("oops");
        ResultMessage<?> resultMessage = asResultMessage(t);
        try {
            resultMessage.getPayload();
        } catch (IllegalPayloadAccessException ipae) {
            assertEquals(t, ipae.getCause());
        }
    }

    @Test
    void exceptionSerialization() {
        Throwable expected = new Throwable("oops");
        ResultMessage<?> resultMessage = asResultMessage(expected);
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();
        SerializedObject<String> serializedObject =
                resultMessage.serializeExceptionResult(jacksonSerializer, String.class);
        RemoteExceptionDescription actual = jacksonSerializer.deserialize(serializedObject);
        assertEquals("java.lang.Throwable: oops", actual.getDescriptions().get(0));
    }
}
