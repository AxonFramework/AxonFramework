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

import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStoreTransaction;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.interception.CorrelationDataInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.EventCriterion;
import org.axonframework.messaging.eventstreaming.Tag;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

class IsolatingEventStoreTransactionTest {

    @Nested
    class Source {

        @Test
        void addsTestTagToSimpleCriteria() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = contextWithTestId("test-1");

            var condition = SourcingCondition.conditionFor(
                    EventCriteria.havingTags(Tag.of("aggregateId", "123"))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(condition);

            // then
            var scopedCondition = capturedCondition.get();
            var flattened = scopedCondition.criteria().flatten();
            assertThat(flattened).hasSize(1);

            var criterion = flattened.iterator().next();
            assertThat(criterion.tags()).containsExactlyInAnyOrder(
                    Tag.of("aggregateId", "123"),
                    Tag.of(TEST_ID_METADATA_KEY, "test-1")
            );
        }

        @Test
        void addsTestTagToOrCriteria() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = contextWithTestId("test-2");

            var condition = SourcingCondition.conditionFor(
                    EventCriteria.havingTags(Tag.of("a", "1"))
                                 .or(EventCriteria.havingTags(Tag.of("b", "2")))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(condition);

            // then
            var scopedCondition = capturedCondition.get();
            var flattened = scopedCondition.criteria().flatten();
            assertThat(flattened).hasSize(2);

            for (EventCriterion criterion : flattened) {
                assertThat(criterion.tags()).contains(Tag.of(TEST_ID_METADATA_KEY, "test-2"));
            }
        }

        @Test
        void addsTestTagToTypedCriteria() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = contextWithTestId("test-3");

            var condition = SourcingCondition.conditionFor(
                    EventCriteria.havingTags(Tag.of("a", "1"))
                                 .andBeingOneOfTypes(new QualifiedName("SomeEvent"))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(condition);

            // then
            var scopedCondition = capturedCondition.get();
            var flattened = scopedCondition.criteria().flatten();
            assertThat(flattened).hasSize(1);

            var criterion = flattened.iterator().next();
            assertThat(criterion.tags()).contains(Tag.of(TEST_ID_METADATA_KEY, "test-3"));
            assertThat(criterion.types()).containsExactly(new QualifiedName("SomeEvent"));
        }

        @Test
        void passesThroughWhenNoCorrelationData() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = new SimpleTestProcessingContext();

            var originalCondition = SourcingCondition.conditionFor(
                    EventCriteria.havingTags(Tag.of("a", "1"))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(originalCondition);

            // then — condition passed unchanged
            var scopedCondition = capturedCondition.get();
            var flattened = scopedCondition.criteria().flatten();
            assertThat(flattened).hasSize(1);

            var criterion = flattened.iterator().next();
            assertThat(criterion.tags()).containsExactly(Tag.of("a", "1"));
        }

        @Test
        void passesThroughWhenCorrelationDataPresentButNoTestId() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = new SimpleTestProcessingContext();
            context.putResource(CorrelationDataInterceptor.CORRELATION_DATA, Map.of("other", "value"));

            var originalCondition = SourcingCondition.conditionFor(
                    EventCriteria.havingTags(Tag.of("a", "1"))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(originalCondition);

            // then — condition passed unchanged
            var scopedCondition = capturedCondition.get();
            var flattened = scopedCondition.criteria().flatten();
            assertThat(flattened).hasSize(1);

            var criterion = flattened.iterator().next();
            assertThat(criterion.tags()).containsExactly(Tag.of("a", "1"));
        }

        @Test
        void preservesStartPosition() {
            // given
            var capturedCondition = new AtomicReference<SourcingCondition>();
            var delegate = capturingTransaction(capturedCondition);
            var context = contextWithTestId("test-pos");
            var startPosition = Position.START;

            var condition = SourcingCondition.conditionFor(
                    startPosition,
                    EventCriteria.havingTags(Tag.of("a", "1"))
            );

            // when
            var scoped = new IsolatingEventStoreTransaction(delegate, context);
            scoped.source(condition);

            // then
            assertThat(capturedCondition.get().start()).isSameAs(Position.START);
        }
    }

    private static SimpleTestProcessingContext contextWithTestId(String testId) {
        var context = new SimpleTestProcessingContext();
        context.putResource(
                CorrelationDataInterceptor.CORRELATION_DATA,
                Map.of(TEST_ID_METADATA_KEY, testId)
        );
        return context;
    }

    private static EventStoreTransaction capturingTransaction(AtomicReference<SourcingCondition> captured) {
        return new EventStoreTransaction() {
            @Override
            public MessageStream<? extends EventMessage> source(SourcingCondition condition,
                                                                @Nullable Consumer<Position> callback) {
                captured.set(condition);
                return MessageStream.empty();
            }

            @Override
            public void appendEvent(EventMessage eventMessage) {
            }

            @Override
            public void onAppend(Consumer<EventMessage> callback) {
            }

            @Override
            public ConsistencyMarker appendPosition() {
                return ConsistencyMarker.ORIGIN;
            }
        };
    }

    /**
     * Minimal {@link ProcessingContext} for testing resource storage.
     */
    static class SimpleTestProcessingContext implements ProcessingContext {

        private final ConcurrentHashMap<ResourceKey<?>, Object> resources = new ConcurrentHashMap<>();

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public ProcessingLifecycle on(ProcessingLifecycle.Phase phase,
                                      Function<ProcessingContext, CompletableFuture<?>> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingLifecycle onError(ProcessingLifecycle.ErrorHandler action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingLifecycle whenComplete(Consumer<ProcessingContext> action) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsResource(ResourceKey<?> key) {
            return resources.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T getResource(ResourceKey<T> key) {
            return (T) resources.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T putResource(ResourceKey<T> key, T resource) {
            return (T) resources.put(key, resource);
        }

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T updateResource(ResourceKey<T> key, UnaryOperator<T> updater) {
            return (T) resources.compute(key, (k, v) -> updater.apply((T) v));
        }

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T putResourceIfAbsent(ResourceKey<T> key, T resource) {
            return (T) resources.putIfAbsent(key, resource);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeResourceIfAbsent(ResourceKey<T> key, Supplier<T> supplier) {
            return (T) resources.computeIfAbsent(key, k -> supplier.get());
        }

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T removeResource(ResourceKey<T> key) {
            return (T) resources.remove(key);
        }

        @Override
        public <T> boolean removeResource(ResourceKey<T> key, T expectedResource) {
            return resources.remove(key, expectedResource);
        }

        @Override
        public Map<ResourceKey<?>, Object> resources() {
            return Map.copyOf(resources);
        }

        @Override
        public <C> C component(Class<C> type, @Nullable String name) {
            throw new UnsupportedOperationException();
        }
    }
}
