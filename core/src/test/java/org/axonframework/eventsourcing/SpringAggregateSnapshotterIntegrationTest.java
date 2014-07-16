/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.SimpleDomainEventStream;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventstore.EventStore;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/contexts/axon-namespace-support-context.xml")
public class SpringAggregateSnapshotterIntegrationTest {

    @Autowired
    @Qualifier("inThreadsnapshotter")
    private SpringAggregateSnapshotter snapshotter;

    @Autowired
    @Qualifier("eventStore")
    private EventStore eventStore;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @SuppressWarnings({"unchecked"})
    @Test
    public void testSnapshotterKnowsAllFactories() throws NoSuchFieldException {
        Map<String, AggregateFactory<?>> snapshotters = (Map<String, AggregateFactory<?>>) ReflectionUtils
                .getFieldValue(AggregateSnapshotter.class.getDeclaredField("aggregateFactories"), snapshotter);

        assertFalse("No snapshotters found", snapshotters.isEmpty());
    }

    @Test
    public void testDuplicateSnapshotIsIgnored() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        snapshotter.setAggregateFactories(Arrays.<AggregateFactory<?>>asList(new GenericAggregateFactory<StubAggregate>(StubAggregate.class)));
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                eventStore.appendEvents("StubAggregate", new SimpleDomainEventStream(new GenericDomainEventMessage("id1", 0, "Payload1"),
                                                                                     new GenericDomainEventMessage("id1", 1, "Payload2")));
            }
        });
        try {
            snapshotter.setExecutor(executor);
            snapshotter.scheduleSnapshot("StubAggregate", "id1");
            snapshotter.scheduleSnapshot("StubAggregate", "id1");
        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
