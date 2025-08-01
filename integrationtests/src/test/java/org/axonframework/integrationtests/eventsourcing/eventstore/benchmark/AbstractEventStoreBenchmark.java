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

package org.axonframework.integrationtests.eventsourcing.eventstore.benchmark;

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.AbstractLegacyEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.LegacyEventStorageEngine;
import org.axonframework.eventstreaming.LegacyStreamableEventSource;
import org.axonframework.messaging.unitofwork.LegacyDefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.axonframework.eventhandling.DomainEventTestUtils.createDomainEvent;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStoreBenchmark {

    private static final int DEFAULT_THREAD_COUNT = 10, DEFAULT_BATCH_SIZE = 5, DEFAULT_BATCH_COUNT = 5000;
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LegacyEmbeddedEventStore eventStore;
    private final EventProcessor eventProcessor;
    private final LegacyEventStorageEngine storageEngine;
    private final int threadCount, batchSize, batchCount;
    private final ExecutorService executorService;
    private final ScheduledExecutorService coordinatorExecutor;
    private final ScheduledExecutorService workerExecutor;
    private final CountDownLatch remainingEvents;
    private final Set<String> readEvents = new HashSet<>();

    protected AbstractEventStoreBenchmark(LegacyEventStorageEngine storageEngine) {
        this(storageEngine, DEFAULT_THREAD_COUNT, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_COUNT);
    }

    protected AbstractEventStoreBenchmark(LegacyEventStorageEngine storageEngine, int threadCount, int batchSize,
                                          int batchCount) {
        this.eventStore = LegacyEmbeddedEventStore.builder()
                                                  .storageEngine(this.storageEngine = storageEngine)
                                                  .build();
        this.threadCount = threadCount;
        this.batchSize = batchSize;
        this.batchCount = batchCount;
        this.remainingEvents = new CountDownLatch(getTotalEventCount());

        // Create executors that we can manage lifecycle for
        this.coordinatorExecutor = Executors.newSingleThreadScheduledExecutor(
                new AxonThreadFactory("benchmark-coordinator"));
        this.workerExecutor = Executors.newScheduledThreadPool(2,
                new AxonThreadFactory("benchmark-worker"));

        SimpleEventHandlerInvoker eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(
                                                 (EventMessageHandler) (eventHandler, ctx) -> {
                                                     if (readEvents.add(eventHandler.identifier())) {
                                                         remainingEvents.countDown();
                                                     } else {
                                                         throw new IllegalStateException("Double event!");
                                                     }
                                                     // We aren't interested in the event handling result
                                                     return null;
                                                 }

                                         ).build();
        this.eventProcessor = PooledStreamingEventProcessor.builder()
                                                    .name("benchmark")
                                                    .eventHandlerInvoker(eventHandlerInvoker)
                                                    .eventSource(new LegacyStreamableEventSource<>(eventStore))
                                                    .tokenStore(new InMemoryTokenStore())
                                                    .transactionManager(NoTransactionManager.INSTANCE)
                                                    .coordinatorExecutor(coordinatorExecutor)
                                                    .workerExecutor(workerExecutor)
                                                    .build();
        this.executorService = Executors.newFixedThreadPool(threadCount, new AxonThreadFactory("storageJobs"));
    }

    public void start() {
        logger.info("Preparing for benchmark", getTotalEventCount());
        List<Callable<Object>> storageJobs = createStorageJobs(threadCount, batchSize, batchCount);
        Collections.shuffle(storageJobs);
        logger.info("Created {} event storage jobs", storageJobs.size());
        prepareForBenchmark();
        logger.info("Started benchmark. Storing {} events", getTotalEventCount());
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("Storing events");
        try {
            executorService.invokeAll(storageJobs);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Benchmark was interrupted", e);
        }
        stopWatch.stop();
        logger.info("Stored {} events in {} seconds. That's about {} events/sec", getTotalEventCount(),
                    decimalFormat.format(stopWatch.getTotalTimeSeconds()),
                    (int) (getTotalEventCount() / stopWatch.getTotalTimeSeconds()));

        stopWatch.start("Waiting for event processor to catch up");
        try {
            remainingEvents.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Benchmark was interrupted", e);
        }
        stopWatch.stop();
        logger.info("Read {} events in {} seconds. That's about {} events/sec.", getTotalEventCount(),
                    decimalFormat.format(stopWatch.getTotalTimeSeconds()),
                    (int) (getTotalEventCount() / stopWatch.getTotalTimeSeconds()));
        logger.info("Cleaning up");
        cleanUpAfterBenchmark();
    }

    protected void prepareForBenchmark() {
        eventProcessor.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Benchmark was interrupted", e);
        }
    }

    protected void cleanUpAfterBenchmark() {
        executorService.shutdown();
        eventProcessor.shutDown();
        eventStore.shutDown();
        coordinatorExecutor.shutdown();
        workerExecutor.shutdown();
    }

    protected List<Callable<Object>> createStorageJobs(int threadCount, int batchSize, int batchCount) {
        return IntStream.range(0, threadCount)
                        .mapToObj(i -> createStorageJobs(String.valueOf(i), batchSize, batchCount))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
    }

    protected List<Callable<Object>> createStorageJobs(String aggregateId, int batchSize, int batchCount) {
        return IntStream.range(0, batchCount).mapToObj(i -> (Callable<Object>) () -> {
            EventMessage<?>[] events = createEvents(aggregateId, i * batchSize, batchSize);
            executeStorageJob(events);
            return i;
        }).collect(Collectors.toList());
    }

    protected EventMessage<?>[] createEvents(String aggregateId, int startSequenceNumber, int count) {
        return IntStream.range(startSequenceNumber, startSequenceNumber + count)
                        .mapToObj(sequenceNumber -> createDomainEvent(aggregateId, sequenceNumber))
                        .peek(event -> serializer().ifPresent(serializer -> {
                            event.serializePayload(serializer, byte[].class);
                            event.serializeMetaData(serializer, byte[].class);
                        })).toArray(EventMessage[]::new);
    }

    protected void executeStorageJob(EventMessage<?>... events) {
        LegacyUnitOfWork<?> unitOfWork = new LegacyDefaultUnitOfWork<>(null);
        unitOfWork.execute((ctx) -> storeEvents(events));
    }

    protected void storeEvents(EventMessage<?>... events) {
        eventStore.publish(events);
    }

    protected Optional<Serializer> serializer() {
        return storageEngine instanceof AbstractLegacyEventStorageEngine ?
                Optional.of(((AbstractLegacyEventStorageEngine) storageEngine).getSnapshotSerializer()) : Optional.empty();
    }

    public int getTotalEventCount() {
        return threadCount * batchSize * batchCount;
    }

    protected LegacyEventStorageEngine getStorageEngine() {
        return storageEngine;
    }
}
