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

package org.axonframework.messaging.eventsourcing.eventstore;

import org.axonframework.messaging.eventhandling.DomainEventMessage;
import org.axonframework.messaging.eventhandling.DomainEventTestUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the specifics around a {@link BatchingEventStorageEngine}.
 *
 * @author Rene de Waele
 */
@Transactional
public abstract class BatchingEventStorageEngineTest<E extends BatchingEventStorageEngine, EB extends BatchingEventStorageEngine.Builder>
        extends AbstractEventStorageEngineTest<E, EB> {

    private BatchingEventStorageEngine testSubject;

    @Test
    protected void loadLargeAmountOfEventsFromAggregateStream() {
        int eventCount = testSubject.batchSize() + 10;
        testSubject.appendEvents(DomainEventTestUtils.createDomainEvents(eventCount));
        testSubject.appendEvents(new GenericEventMessage(new MessageType("event"), "test"));
        assertEquals(eventCount, testSubject.readEvents(EventTestUtils.AGGREGATE).asStream().count());
        Optional<? extends DomainEventMessage> resultEventMessage =
                testSubject.readEvents(EventTestUtils.AGGREGATE).asStream().reduce((a, b) -> b);
        assertTrue(resultEventMessage.isPresent());
        assertEquals(eventCount - 1, resultEventMessage.get().getSequenceNumber());
    }

    @Test
    void loadLargeAmountFromOpenStream() {
        int eventCount = testSubject.batchSize() + 10;
        testSubject.appendEvents(DomainEventTestUtils.createDomainEvents(eventCount));
        GenericEventMessage last =
                new GenericEventMessage(new MessageType("event"), "test");
        testSubject.appendEvents(last);

        Optional<? extends EventMessage> resultEventMessage =
                testSubject.readEvents(null, false).reduce((a, b) -> b);
        assertEquals(testSubject.batchSize() + 11, testSubject.readEvents(null, false).count());
        assertTrue(resultEventMessage.isPresent());
        assertEquals(last.identifier(), resultEventMessage.get().identifier());
    }

    protected void setTestSubject(BatchingEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }
}
