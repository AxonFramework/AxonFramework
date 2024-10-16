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
import org.axonframework.common.ContextTestSuite;
import org.axonframework.messaging.MessageStream.Entry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SimpleEntry}.
 *
 * @author Steven van Beelen
 */
class SimpleEntryTest extends ContextTestSuite<SimpleEntry<?>> {

    @Override
    public SimpleEntry<Message<?>> testSubject() {
        return new SimpleEntry<>(GenericMessage.asMessage("some-payload"));
    }

    @Test
    void throwsAxonConfigurationExceptionForNullContext() {
        assertThrows(AxonConfigurationException.class, () -> new SimpleEntry<>(null, null));
    }

    @Test
    void containsExpectedData() {
        Message<Object> expected = GenericMessage.asMessage("some-payload");

        Entry<Message<Object>> testSubject = new SimpleEntry<>(expected);

        assertEquals(expected, testSubject.message());
    }

    @Test
    void mapsContainedMessageAsExpected() {
        Message<Object> expected = GenericMessage.asMessage("some-payload");
        MetaData expectedMetaData = MetaData.from(Map.of("key", "value"));

        Entry<Message<Object>> testSubject = new SimpleEntry<>(expected);

        Entry<Message<Object>> result = testSubject.map(message -> message.withMetaData(expectedMetaData));

        assertNotEquals(expected, result.message());
        assertEquals(expectedMetaData, result.message().getMetaData());
    }
}