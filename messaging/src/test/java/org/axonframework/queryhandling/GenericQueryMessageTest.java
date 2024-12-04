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

package org.axonframework.queryhandling;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating behaviour of the {@link GenericQueryMessage}.
 *
 * @author Steven van Beelen
 */
class GenericQueryMessageTest {

    @Test
    void queryNameResemblesPayloadClassName() {
        String testPayload = "payload";

        String result = QueryMessage.queryName(testPayload);

        assertEquals(String.class.getName(), result);
    }

    @Test
    void queryNameResemblesMessagePayloadTypeClassName() {
        QualifiedName testType = new QualifiedName("test", "query", "0.0.1");
        String testPayload = "payload";
        Message<?> testMessage = new GenericMessage<>(testType, testPayload);

        String result = QueryMessage.queryName(testMessage);

        assertEquals(String.class.getName(), result);
    }

    @Test
    void queryNameResemblesQueryMessageQueryName() {
        String expectedQueryName = "myQueryName";
        QueryMessage<String, String> testMessage = new GenericQueryMessage<>(
                new QualifiedName("test", "query", "0.0.1"), expectedQueryName, "payload", instanceOf(String.class)
        );

        String result = QueryMessage.queryName(testMessage);

        assertEquals(expectedQueryName, result);
    }
}