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
import io.axoniq.axonserver.grpc.event.dcb.Criterion;
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.StreamEventsRequest;
import io.axoniq.axonserver.grpc.event.dcb.Tag;
import io.axoniq.axonserver.grpc.event.dcb.TagsAndNamesCriterion;
import jakarta.annotation.Nonnull;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventCriterion;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.messaging.QualifiedName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class containing operations to convert Axon Framework's {@link SourcingCondition} and
 * {@link StreamingCondition} into an Axon Server {@link SourceEventsRequest} and {@link StreamEventsRequest}
 * respectively.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public abstract class ConditionConverter {

    /**
     * Converts the given {@code condition} into a {@link SourceEventsRequest}.
     * <p>
     * The {@link SourcingCondition#start()} translates to the
     * {@link SourceEventsRequest#getFromSequence() from sequence value}. The {@link SourcingCondition#criteria()} are
     * {@link EventCriteria#flatten() flattened} before being mapped to {@link Criterion}.
     *
     * @param condition The {@code SourcingCondition} to base the {@link SourceEventsRequest} on.
     * @return A {@code SourceEventsRequest} based on the given {@code condition}.
     */
    public static SourceEventsRequest convertSourcingCondition(@Nonnull SourcingCondition condition) {
        Objects.requireNonNull(condition, "The sourcing condition cannot be null.");
        return SourceEventsRequest.newBuilder()
                                  .setFromSequence(condition.start())
                                  .addAllCriterion(convertEventCriterion(condition.criteria().flatten()))
                                  .build();
    }

    /**
     * Converts the given {@code condition} into a {@link StreamEventsRequest}.
     * <p>
     * The {@link StreamingCondition#position()} translates to the
     * {@link StreamEventsRequest#getFromSequence() from sequence value}. The {@link StreamingCondition#criteria()} are
     * {@link EventCriteria#flatten() flattened} before being mapped to {@link Criterion}.
     *
     * @param condition The {@code StreamingCondition} to base the {@link StreamEventsRequest} on.
     * @return A {@code StreamEventsRequest} based on the given {@code condition}.
     */
    public static StreamEventsRequest convertStreamingCondition(@Nonnull StreamingCondition condition) {
        Objects.requireNonNull(condition, "The streaming condition cannot be null.");
        return StreamEventsRequest.newBuilder()
                                  .setFromSequence(condition.position().position().orElse(-1))
                                  .addAllCriterion(convertEventCriterion(condition.criteria().flatten()))
                                  .build();
    }

    private static List<Criterion> convertEventCriterion(Set<EventCriterion> eventCriterion) {
        return eventCriterion.stream()
                             .map(ConditionConverter::convertEventCriterion)
                             .toList();
    }

    private static Criterion convertEventCriterion(EventCriterion eventCriterion) {
        return Criterion.newBuilder()
                        .setTagsAndNames(TagsAndNamesCriterion.newBuilder()
                                                              .addAllTag(convertTags(eventCriterion.tags()))
                                                              .addAllName(convertTypes(eventCriterion.types()))
                                                              .build())
                        .build();
    }

    private static List<Tag> convertTags(Set<org.axonframework.eventsourcing.eventstore.Tag> tags) {
        return tags.stream()
                   .map(ConditionConverter::convertTag)
                   .toList();
    }

    private static Tag convertTag(org.axonframework.eventsourcing.eventstore.Tag tag) {
        return Tag.newBuilder()
                  .setKey(ByteString.copyFrom(tag.key(), StandardCharsets.UTF_8))
                  .setValue(ByteString.copyFrom(tag.value(), StandardCharsets.UTF_8))
                  .build();
    }

    private static List<String> convertTypes(Set<QualifiedName> types) {
        return types.stream().map(QualifiedName::name).toList();
    }

    private ConditionConverter() {
        // Utility class
    }
}
