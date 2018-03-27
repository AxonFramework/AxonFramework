/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.consumer;

import org.apache.kafka.common.TopicPartition;
import org.assertj.core.util.Lists;
import org.junit.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;

/**
 * Tests for {@link KafkaTrackingToken}
 *
 * @author Nakul Mishra
 */
public class KafkaTrackingTokenTest {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testNullTokenShouldBeEmpty() {
        assertTrue(KafkaTrackingToken.isEmpty(null));
    }

    @Test
    public void testTokenWithEmptyPartitions() {
        assertTrue(KafkaTrackingToken.isEmpty(emptyToken()));
    }

    @Test
    public void testTokenWithPartitions() {
        assertTrue(KafkaTrackingToken.isNotEmpty(nonEmptyToken()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAdvanceToInvalidPartition() {
        KafkaTrackingToken.newInstance(Collections.emptyMap()).advancedTo(-1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAdvanceToInvalidOffset() {
        KafkaTrackingToken.newInstance(Collections.emptyMap()).advancedTo(0, -1);
    }

    @Test
    public void testAdvanceToLaterTimestamp() {
        KafkaTrackingToken start = KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 0L);
            put(1, 0L);
        }});
        KafkaTrackingToken subject = start.advancedTo(0, 1L);
        assertNotSame(subject, start);
        assertEquals(Long.valueOf(1), subject.getPartitionPositions().get(0));
        assertKnownEventIds(subject, 0, 1);
    }

    @Test
    public void testAdvanceToOlderTimestamp() {
        KafkaTrackingToken start = KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 1L);
            put(1, 0L);
        }});
        KafkaTrackingToken subject = start.advancedTo(0, 0L);
        assertNotSame(subject, start);
        assertEquals(Long.valueOf(0), subject.getPartitionPositions().get(0));
        assertKnownEventIds(subject, 0, 1);
    }

    @Test
    public void testTopicPartitionCreation() {
        assertEquals(new TopicPartition("foo", 0), KafkaTrackingToken.partition("foo", 0));
    }

    @Test
    public void testRestoringKafkaPartitionsFromExistingToken() {
        KafkaTrackingToken existingToken = KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 0L);
            put(2, 10L);
        }});
        Collection<TopicPartition> expected = Lists.newArrayList(new TopicPartition("bar", 0),
                                                                 new TopicPartition("bar", 2));
        assertEquals(expected, KafkaTrackingToken.partitions("bar", existingToken));
    }

    private KafkaTrackingToken emptyToken() {
        return KafkaTrackingToken.newInstance(Collections.emptyMap());
    }

    private KafkaTrackingToken nonEmptyToken() {
        return KafkaTrackingToken.newInstance(new HashMap<Integer, Long>() {{
            put(0, 0L);
        }});
    }

    private static void assertKnownEventIds(KafkaTrackingToken token, Integer... expectedKnownIds) {
        assertEquals(Stream.of(expectedKnownIds).collect(toSet()),
                     new HashSet<>(token.getPartitionPositions().keySet()));
    }
}