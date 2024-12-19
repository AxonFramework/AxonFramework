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

package org.axonframework.messaging;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Context;
import org.axonframework.common.Context.ResourceKey;
import org.axonframework.common.ContextTestSuite;
import org.axonframework.common.SimpleContext;
import org.axonframework.messaging.MessageStream.Entry;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SimpleEntry}.
 *
 * @author Steven van Beelen
 */
class SimpleEntryTest extends ContextTestSuite<SimpleEntry<?>> {

    private static final Message<Object> TEST_MESSAGE =
            new GenericMessage<>(fromDottedName("test.message"), "some-payload");

    @Override
    public SimpleEntry<Message<?>> testSubject() {
        return new SimpleEntry<>(TEST_MESSAGE);
    }

    @Test
    void throwsAxonConfigurationExceptionForNullContext() {
        assertThrows(AxonConfigurationException.class, () -> new SimpleEntry<>(null, null));
    }

    @Test
    void containsExpectedData() {
        Message<Object> expected = TEST_MESSAGE;

        Entry<Message<Object>> testSubject = new SimpleEntry<>(expected);

        assertEquals(expected, testSubject.message());
    }

    @Test
    void mapsContainedMessageAndContextAsExpected() {
        Message<Object> expectedMessage = TEST_MESSAGE;
        MetaData expectedMetaData = MetaData.from(Map.of("key", "value"));
        String expectedResourceValue = "test";
        ResourceKey<String> expectedContextKey = ResourceKey.create(expectedResourceValue);
        Context testContext = new SimpleContext().withResource(expectedContextKey, expectedResourceValue);

        Entry<Message<Object>> testSubject = new SimpleEntry<>(expectedMessage, testContext);

        Entry<Message<Object>> result = testSubject.map(message -> message.withMetaData(expectedMetaData));

        assertNotEquals(expectedMessage, result.message());
        assertEquals(expectedMetaData, result.message().getMetaData());
        assertTrue(result.containsResource(expectedContextKey));
        assertEquals(expectedResourceValue, result.getResource(expectedContextKey));
    }
}