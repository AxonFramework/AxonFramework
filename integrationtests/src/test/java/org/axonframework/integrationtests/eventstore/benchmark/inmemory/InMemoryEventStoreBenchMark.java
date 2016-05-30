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

package org.axonframework.integrationtests.eventstore.benchmark.inmemory;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;

/**
 * @author Rene de Waele
 */
public class InMemoryEventStoreBenchMark extends AbstractEventStoreBenchmark {

    private static final IdentifierFactory IDENTIFIER_FACTORY = IdentifierFactory.getInstance();
    private EventStore eventStore;

    public static void main(String[] args) throws Exception {
        new InMemoryEventStoreBenchMark().startBenchMark();
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new MongoBenchmark();
    }

    @Override
    protected void prepareEventStore() {
        eventStore = new EmbeddedEventStore(new InMemoryEventStorageEngine());
    }

    private class MongoBenchmark implements Runnable {

        @Override
        public void run() {
            final String aggregateId = IDENTIFIER_FACTORY.generateIdentifier();
            int eventSequence = 0;
            for (int t = 0; t < getTransactionCount(); t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, eventStore, eventSequence);
            }
        }
    }
}
