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

package org.axonframework.messaging.core;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.MessageStream.Entry;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SimpleEntry}.
 *
 * @author Steven van Beelen
 */
class SimpleEntryTest extends ContextTestSuite<SimpleEntry<?>> {

    private static final Message TEST_MESSAGE =
            new GenericMessage(new MessageType("message"), "some-payload");

    @Override
    public SimpleEntry<Message> testSubject() {
        return new SimpleEntry<>(TEST_MESSAGE);
    }

    @Test
    void throwsAxonConfigurationExceptionForNullContext() {
        assertThrows(AxonConfigurationException.class, () -> new SimpleEntry<>(null, null));
    }

    @Test
    void containsExpectedData() {
        Message expected = TEST_MESSAGE;

        Entry<Message> testSubject = new SimpleEntry<>(expected);

        assertEquals(expected, testSubject.message());
    }

    @Test
    void mapsContainedMessageAndContextAsExpected() {
        Message expectedMessage = TEST_MESSAGE;
        Metadata expectedMetadata = Metadata.from(Map.of("key", "value"));
        String expectedResourceValue = "test";
        ResourceKey<String> expectedContextKey = ResourceKey.withLabel(expectedResourceValue);
        Context testContext = Context.empty().withResource(expectedContextKey, expectedResourceValue);

        Entry<Message> testSubject = new SimpleEntry<>(expectedMessage, testContext);

        Entry<Message> result = testSubject.map(message -> message.withMetadata(expectedMetadata));

        assertNotEquals(expectedMessage, result.message());
        assertEquals(expectedMetadata, result.message().metadata());
        assertTrue(result.containsResource(expectedContextKey));
        assertEquals(expectedResourceValue, result.getResource(expectedContextKey));
    }
}