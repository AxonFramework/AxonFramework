/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.fixture;

import org.jspecify.annotations.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.interception.CorrelationDataInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.Tag;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Configuration-level {@link EventStoreTransaction} wrapper that intercepts {@link #source(SourcingCondition)} calls
 * to add the test isolation tag to the sourcing criteria. The test identifier is read from the
 * {@link ProcessingContext}'s {@link CorrelationDataInterceptor#CORRELATION_DATA correlation data}.
 * <p>
 * When no test identifier is present in the processing context (e.g., when test isolation is not active), the
 * sourcing condition is passed through unchanged.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingEventStoreTransaction implements EventStoreTransaction {

    private final EventStoreTransaction delegate;
    private final ProcessingContext processingContext;

    /**
     * Constructs a new {@code IsolatingEventStoreTransaction}.
     *
     * @param delegate          The {@link EventStoreTransaction} to delegate to.
     * @param processingContext The {@link ProcessingContext} from which to extract the test identifier.
     */
    IsolatingEventStoreTransaction(EventStoreTransaction delegate, ProcessingContext processingContext) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate EventStoreTransaction may not be null");
        this.processingContext = Objects.requireNonNull(processingContext, "The ProcessingContext may not be null");
    }

    @Override
    public MessageStream<? extends EventMessage> source(SourcingCondition condition,
                                                        @Nullable Consumer<Position> resumePositionCallback) {
        var testId = extractTestId();
        if (testId == null) {
            return delegate.source(condition, resumePositionCallback);
        }
        var scopedCondition = addTestTagToCriteria(condition, testId);
        return delegate.source(scopedCondition, resumePositionCallback);
    }

    @Override
    public void appendEvent(EventMessage eventMessage) {
        delegate.appendEvent(eventMessage);
    }

    @Override
    public void onAppend(Consumer<EventMessage> callback) {
        delegate.onAppend(callback);
    }

    @Override
    public ConsistencyMarker appendPosition() {
        return delegate.appendPosition();
    }

    @Nullable
    private String extractTestId() {
        if (!processingContext.containsResource(CorrelationDataInterceptor.CORRELATION_DATA)) {
            return null;
        }
        return processingContext.getResource(CorrelationDataInterceptor.CORRELATION_DATA)
                                .get(TEST_ID_METADATA_KEY);
    }

    private static SourcingCondition addTestTagToCriteria(SourcingCondition condition, String testId) {
        var testTag = new Tag(TEST_ID_METADATA_KEY, testId);
        var originalCriteria = condition.criteria();
        var flattened = originalCriteria.flatten();

        EventCriteria scopedCriteria;
        if (flattened.isEmpty()) {
            scopedCriteria = EventCriteria.havingTags(testTag);
        } else {
            var scopedParts = flattened.stream()
                    .map(criterion -> addTagToCriterion(criterion, testTag))
                    .collect(Collectors.toSet());

            scopedCriteria = scopedParts.size() == 1
                    ? scopedParts.iterator().next()
                    : EventCriteria.either(scopedParts);
        }

        return SourcingCondition.conditionFor(condition.start(), scopedCriteria);
    }

    private static EventCriteria addTagToCriterion(EventCriterion criterion, Tag testTag) {
        var combinedTags = new HashSet<>(criterion.tags());
        combinedTags.add(testTag);
        var tagCriteria = EventCriteria.havingTags(combinedTags);
        if (criterion.types().isEmpty()) {
            return tagCriteria;
        }
        return tagCriteria.andBeingOneOfTypes(criterion.types());
    }
}
