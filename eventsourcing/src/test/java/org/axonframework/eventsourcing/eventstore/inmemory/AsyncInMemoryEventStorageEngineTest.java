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

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AppendConditionAssertionException;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.Index;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StorageEngineTestSuite;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.eventsourcing.eventstore.EventCriteria.hasIndex;
import static org.axonframework.eventsourcing.eventstore.SourcingCondition.conditionFor;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Test class validating the {@link SimpleEventStore} together with the {@link AsyncInMemoryEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
class AsyncInMemoryEventStorageEngineTest extends StorageEngineTestSuite<AsyncInMemoryEventStorageEngine> {

    @Override
    protected AsyncInMemoryEventStorageEngine buildStorageEngine() {
        return new AsyncInMemoryEventStorageEngine();
    }

    /**
     * By sourcing twice within a given UnitOfWork, the DefaultEventStoreTransaction combines the AppendConditions. By
     * following this up with an appendEvent invocation, the in-memory EventStorageEngine will throw an
     * AppendConditionAssertionException as intended.
     */
    @Test
    void appendEventsThrowsAppendConditionAssertionExceptionWhenToManyIndicesAreGiven() {
        SourcingCondition firstCondition = conditionFor(TEST_CRITERIA);
        SourcingCondition secondCondition = conditionFor(hasIndex(new Index("aggregateId", "other-aggregate-id")));

        CompletableFuture<AsyncEventStorageEngine.AppendTransaction> result = testSubject.appendEvents(AppendCondition.from(
                firstCondition.combine(secondCondition)));

        await().atMost(Duration.ofMillis(500))
               .pollDelay(Duration.ofMillis(25))
               .untilAsserted(() -> {
                   assertTrue(result.isCompletedExceptionally());
                   assertInstanceOf(AppendConditionAssertionException.class, result.exceptionNow());
               });
    }
}
