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

package org.axonframework.axonserver.connector.event;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.event.dcb.ConsistencyCondition;
import io.axoniq.axonserver.grpc.event.dcb.Criterion;
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.StreamEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.TagsAndNamesCriterion;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.GlobalIndexPositions;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ConditionConverter}.
 *
 * @author Steven van Beelen
 */
class ConditionConverterTest {

    private static final int START = 1337;

    @Test
    void convertAppendConditionThrowsNullPointerExceptionForNullAppendCondition() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> ConditionConverter.convertAppendCondition(null));
    }

    @Test
    void convertAppendConditionConstructsConsistencyConditionAsExpected() {
        // given...
        AppendCondition testCondition = AppendCondition.withCriteria(
                EventCriteria.havingTags(
                                     Tag.of("key1OnCriterion1", "value1OnCriterion1"),
                                     Tag.of("key2OnCriterion1", "value2OnCriterion1")
                             )
                             .andBeingOneOfTypes(new QualifiedName("name1OnCriterion1"))
                             .or()
                             .havingTags(Tag.of("key1OnCriterion2", "value1OnCriterion2"))
                             .andBeingOneOfTypes(
                                     new QualifiedName("name1OnCriterion2"),
                                     new QualifiedName("name2OnCriterion2")
                             )
                             .or()
                             .havingTags(Tag.of("key1OnCriterion3", "value1OnCriterion3"))
        ).withMarker(new GlobalIndexConsistencyMarker(START));
        // when...
        ConsistencyCondition result = ConditionConverter.convertAppendCondition(testCondition);
        // then...
        assertEquals(START, result.getConsistencyMarker());
        List<Criterion> resultCriterion = result.getCriterionList();
        assertEquals(3, resultCriterion.size());
        validateCriterion(resultCriterion.getFirst().getTagsAndNames());
        validateCriterion(resultCriterion.get(1).getTagsAndNames());
        validateCriterion(resultCriterion.getLast().getTagsAndNames());
    }

    @Test
    void convertSourcingConditionThrowsNullPointerExceptionForNullSourcingCondition() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> ConditionConverter.convertSourcingCondition(null));
    }

    @Test
    void convertSourcingConditionConstructsSourceEventRequestAsExpected() {
        // given...
        SourcingCondition testCondition = SourcingCondition.conditionFor(
                GlobalIndexPositions.of(START),
                EventCriteria.havingTags(
                                     Tag.of("key1OnCriterion1", "value1OnCriterion1"),
                                     Tag.of("key2OnCriterion1", "value2OnCriterion1")
                             )
                             .andBeingOneOfTypes(new QualifiedName("name1OnCriterion1"))
                             .or()
                             .havingTags(Tag.of("key1OnCriterion2", "value1OnCriterion2"))
                             .andBeingOneOfTypes(
                                     new QualifiedName("name1OnCriterion2"),
                                     new QualifiedName("name2OnCriterion2")
                             )
                             .or()
                             .havingTags(Tag.of("key1OnCriterion3", "value1OnCriterion3"))
        );
        // when...
        SourceEventsRequest result = ConditionConverter.convertSourcingCondition(testCondition);
        // then...
        assertEquals(START, result.getFromSequence());
        List<Criterion> resultCriterion = result.getCriterionList();
        assertEquals(3, resultCriterion.size());
        validateCriterion(resultCriterion.getFirst().getTagsAndNames());
        validateCriterion(resultCriterion.get(1).getTagsAndNames());
        validateCriterion(resultCriterion.getLast().getTagsAndNames());
    }

    @Test
    void convertStreamingConditionThrowsNullPointerExceptionForNullStreamingCondition() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> ConditionConverter.convertStreamingCondition(null));
    }

    @Test
    void convertStreamingConditionConstructsSourceEventRequestAsExpected() {
        // given...
        StreamingCondition testCondition = StreamingCondition.conditionFor(
                new GlobalSequenceTrackingToken(START),
                EventCriteria.havingTags(
                                     Tag.of("key1OnCriterion1", "value1OnCriterion1"),
                                     Tag.of("key2OnCriterion1", "value2OnCriterion1")
                             )
                             .andBeingOneOfTypes(new QualifiedName("name1OnCriterion1"))
                             .or()
                             .havingTags(Tag.of("key1OnCriterion2", "value1OnCriterion2"))
                             .andBeingOneOfTypes(
                                     new QualifiedName("name1OnCriterion2"),
                                     new QualifiedName("name2OnCriterion2")
                             )
                             .or()
                             .havingTags(Tag.of("key1OnCriterion3", "value1OnCriterion3"))
        );
        // when...
        StreamEventsRequest result = ConditionConverter.convertStreamingCondition(testCondition);
        // then...
        assertEquals(START, result.getFromSequence());
        List<Criterion> resultCriterion = result.getCriterionList();
        assertEquals(3, resultCriterion.size());
        validateCriterion(resultCriterion.getFirst().getTagsAndNames());
        validateCriterion(resultCriterion.get(1).getTagsAndNames());
        validateCriterion(resultCriterion.getLast().getTagsAndNames());
    }

    private static void validateCriterion(TagsAndNamesCriterion criterion) {
        if (criterion.getNameCount() == 1) {
            String name = criterion.getNameList()
                                   .getFirst();
            assertEquals("name1OnCriterion1", name);
            List<io.axoniq.axonserver.grpc.event.dcb.Tag> tags = criterion.getTagList();
            assertEquals(2, tags.size());
            assertTrue(tags.contains(
                    io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                           .setKey(ByteString.copyFrom("key1OnCriterion1",
                                                                                       StandardCharsets.UTF_8))
                                                           .setValue(ByteString.copyFrom("value1OnCriterion1",
                                                                                         StandardCharsets.UTF_8))
                                                           .build()
            ));
            assertTrue(tags.contains(
                    io.axoniq.axonserver.grpc.event.dcb.Tag.newBuilder()
                                                           .setKey(ByteString.copyFrom("key2OnCriterion1",
                                                                                       StandardCharsets.UTF_8))
                                                           .setValue(ByteString.copyFrom("value2OnCriterion1",
                                                                                         StandardCharsets.UTF_8))
                                                           .build()
            ));
        } else if (criterion.getNameCount() == 2) {
            List<String> names = criterion.getNameList();
            assertTrue(names.contains("name1OnCriterion2"));
            assertTrue(names.contains("name2OnCriterion2"));
            assertEquals(1, criterion.getTagCount());
            io.axoniq.axonserver.grpc.event.dcb.Tag firstTag = criterion.getTagList().getFirst();
            assertEquals("key1OnCriterion2", firstTag.getKey().toString(StandardCharsets.UTF_8));
            assertEquals("value1OnCriterion2", firstTag.getValue().toString(StandardCharsets.UTF_8));
        } else if (criterion.getNameCount() == 0) {
            assertTrue(criterion.getNameList().isEmpty());
            assertEquals(1, criterion.getTagCount());
            io.axoniq.axonserver.grpc.event.dcb.Tag firstTag = criterion.getTagList().getFirst();
            assertEquals("key1OnCriterion3", firstTag.getKey().toString(StandardCharsets.UTF_8));
            assertEquals("value1OnCriterion3", firstTag.getValue().toString(StandardCharsets.UTF_8));
        } else {
            fail("This validation method only expects criterion with 0, 1, or 2 names!");
        }
    }
}