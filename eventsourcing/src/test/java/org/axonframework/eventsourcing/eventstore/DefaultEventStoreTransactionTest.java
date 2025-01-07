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

import org.axonframework.eventsourcing.StubProcessingContext;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import static org.axonframework.eventsourcing.eventstore.EventCriteria.anyEvent;
import static org.junit.jupiter.api.Assertions.*;

class DefaultEventStoreTransactionTest {

    public static final Context.ResourceKey<Object> APPEND_CONDITION_KEY = Context.ResourceKey.withLabel(
            "appendCondition");
    private AsyncEventStorageEngine eventStorageEngine;
    private EventStoreTransaction eventStoreTransaction;
    private ProcessingContext processingContext;

    private static SourcingCondition aSourcingCondition() {
        return new DefaultSourcingCondition(anyEvent(), 1L, 999L);
    }

    private static AppendCondition anAppendCondition() {
        return new DefaultAppendCondition(999L, anyEvent());
    }

    @BeforeEach
    void setUp() {
        processingContext = new StubProcessingContext();
        eventStorageEngine = new AsyncInMemoryEventStorageEngine();
        eventStoreTransaction = new DefaultEventStoreTransaction(eventStorageEngine, processingContext);
    }

    @Test
    void whenSourceThenCreateAppendCondition() {
        // given
        AppendCondition appendCondition = anAppendCondition();

        // when
        eventStoreTransaction.source(aSourcingCondition(), processingContext);

        // then
        assertEquals(appendCondition, processingContext.getResource(APPEND_CONDITION_KEY));
    }

    @Test
    void appendEvent() {
    }

    @Test
    void onAppend() {
    }

    @Test
    void appendPosition() {
    }
}