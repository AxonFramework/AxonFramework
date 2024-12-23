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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link GenericIndexedEventMessage}.
 *
 * @author Steven van Beelen
 */
class GenericIndexedEventMessageTest {

    private static final MetaData TEST_META_DATA = MetaData.with("key", "value");
    private static final EventMessage<String> TEST_EVENT = GenericEventMessage.<String>asEventMessage("event")
                                                                              .withMetaData(TEST_META_DATA);
    private static final Index TEST_INDEX = new Index("key", "value");
    private static final Set<Index> TEST_INDICES = Set.of(TEST_INDEX);

    private GenericIndexedEventMessage<String> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new GenericIndexedEventMessage<>(TEST_EVENT, TEST_INDICES);
    }

    @Test
    void containsExpectedData() {
        assertEquals(TEST_EVENT.getIdentifier(), testSubject.getIdentifier());
        assertEquals(TEST_EVENT.getPayload(), testSubject.getPayload());
        assertEquals(TEST_META_DATA, testSubject.getMetaData());
        assertEquals(TEST_EVENT.getTimestamp(), testSubject.getTimestamp());
        assertEquals(TEST_INDICES, testSubject.indices());
    }

    @Test
    void withMetaDataReturnsTestSubjectIfGivenMetaDataMatches() {
        EventMessage<String> result = testSubject.withMetaData(TEST_META_DATA);

        assertEquals(testSubject.getMetaData(), result.getMetaData());
        assertEquals(testSubject, result);
    }

    @Test
    void withMetaDataReplacesTheOriginalMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");

        EventMessage<String> result = testSubject.withMetaData(testMetaData);

        assertNotEquals(testSubject.getMetaData(), result.getMetaData());
        assertEquals(testMetaData, result.getMetaData());
    }

    @Test
    void andMetaDataReturnsTestSubjectIfGivenMetaDataMatches() {
        EventMessage<String> result = testSubject.andMetaData(TEST_META_DATA);

        assertEquals(testSubject.getMetaData(), result.getMetaData());
        assertEquals(testSubject, result);
    }

    @Test
    void andMetaDataMergesTheGivenMetaDataWithTheOriginalMetaData() {
        MetaData testMetaData = MetaData.with("some-key", "some-value");
        MetaData expectedMetaData = testMetaData.mergedWith(testSubject.getMetaData());

        EventMessage<String> result = testSubject.andMetaData(testMetaData);

        assertNotEquals(testSubject.getMetaData(), result.getMetaData());
        assertEquals(expectedMetaData, result.getMetaData());
    }

    @Test
    void updateIndicesReturnsIndexedEventMessageWithChangedIndices() {
        Index testIndex = new Index("some-key", "some-value");

        Set<Index> result = testSubject.updateIndices(current -> {
                                           Set<Index> newIndices = new HashSet<>(current);
                                           newIndices.add(testIndex);
                                           return newIndices;
                                       })
                                       .indices();

        assertTrue(result.contains(testIndex));
        assertTrue(result.contains(TEST_INDEX));
    }
}