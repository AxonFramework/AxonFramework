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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.messaging.MessageType;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MetaDataBasedTagResolver}.
 *
 * @author Mateusz Nowak
 */
class MetaDataBasedTagResolverTest {

    private static final String META_DATA_KEY = "testKey";
    private static final GenericEventMessage<String> TEST_EVENT = new GenericEventMessage<>(
            new MessageType("test", "event", "0.0.1"),
            "payload",
            Map.of(META_DATA_KEY, "testValue")
    );

    @Test
    void resolveReturnsExpectedTagWhenMetaDataKeyExists() {
        // given
        MetaDataBasedTagResolver testSubject = new MetaDataBasedTagResolver(META_DATA_KEY);

        // when
        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        // then
        assertEquals(1, result.size());
        assertTrue(result.contains(new Tag(META_DATA_KEY, "testValue")));
    }

    @Test
    void resolveReturnsEmptySetWhenMetaDataKeyDoesNotExist() {
        // given
        MetaDataBasedTagResolver testSubject = new MetaDataBasedTagResolver("nonExistentKey");

        // when
        Set<Tag> result = testSubject.resolve(TEST_EVENT);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void constructorThrowsNullPointerExceptionForNullMetaDataKey() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new MetaDataBasedTagResolver(null));
    }
}