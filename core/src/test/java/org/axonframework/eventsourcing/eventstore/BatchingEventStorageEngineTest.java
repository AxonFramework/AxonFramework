/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static junit.framework.TestCase.assertEquals;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.AGGREGATE;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvents;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asStream;

/**
 * @author Rene de Waele
 */
@Transactional
public abstract class BatchingEventStorageEngineTest extends AbstractEventStorageEngineTest {

    private static final int BATCH_SIZE = 100;

    private BatchingEventStorageEngine testSubject;

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testLoad_LargeAmountOfEvents() {
        int eventCount = BATCH_SIZE + 10;
        testSubject.appendEvents(createEvents(eventCount));
        assertEquals(eventCount, asStream(testSubject.readEvents(AGGREGATE)).count());
        assertEquals(eventCount - 1, asStream(testSubject.readEvents(AGGREGATE)).reduce((a, b) -> b).get().getSequenceNumber());
    }

    @Test
    @DirtiesContext
    public void testLoad_LargeAmountOfEventsInSmallBatches() {
        testSubject.setBatchSize(9);
        testLoad_LargeAmountOfEvents();
    }

    protected void setTestSubject(BatchingEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
        testSubject.setBatchSize(BATCH_SIZE);
    }

}
