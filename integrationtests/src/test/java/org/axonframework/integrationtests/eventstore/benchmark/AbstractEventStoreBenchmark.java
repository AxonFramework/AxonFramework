package org.axonframework.integrationtests.eventstore.benchmark;

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.axonframework.serialization.MessageSerializer.serializeMetaData;
import static org.axonframework.serialization.MessageSerializer.serializePayload;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStoreBenchmark {

    private static final int DEFAULT_THREAD_COUNT = 10, DEFAULT_BATCH_SIZE = 5, DEFAULT_BATCH_COUNT = 5000;
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EmbeddedEventStore eventStore;
    private final EventProcessor eventProcessor;
    private final EventStorageEngine storageEngine;
    private final int threadCount, batchSize, batchCount;
    private final ExecutorService executorService;
    private final CountDownLatch remainingEvents;
    private final Set<String> readEvents = new HashSet<>();

    protected AbstractEventStoreBenchmark(EventStorageEngine storageEngine) {
        this(storageEngine, DEFAULT_THREAD_COUNT, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_COUNT);
    }

    protected AbstractEventStoreBenchmark(EventStorageEngine storageEngine, int threadCount, int batchSize,
                                          int batchCount) {
        this.eventStore = new EmbeddedEventStore(this.storageEngine = storageEngine);
        this.threadCount = threadCount;
        this.batchSize = batchSize;
        this.batchCount = batchCount;
        this.remainingEvents = new CountDownLatch(getTotalEventCount());
        this.eventProcessor = new TrackingEventProcessor("benchmark", new SimpleEventHandlerInvoker(
                (EventListener) (e) -> {
                    if (readEvents.add(e.getIdentifier())) {
                        remainingEvents.countDown();
                    } else {
                        throw new IllegalStateException("Double event!");
                    }
                }), eventStore, new InMemoryTokenStore(),
                                                         NoTransactionManager.INSTANCE, batchSize);
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
    }

    protected List<Callable<Object>> createStorageJobs(int threadCount, int batchSize, int batchCount) {
        return IntStream.range(0, threadCount)
                .mapToObj(i -> createStorageJobs(String.valueOf(i), batchSize, batchCount)).flatMap(Collection::stream)
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
        Stream<EventMessage<?>> stream = IntStream.range(startSequenceNumber, startSequenceNumber + count)
                .mapToObj(sequenceNumber -> createEvent(aggregateId, sequenceNumber)).map(event -> {
                    serializer().ifPresent(serializer -> {
                        serializePayload(event, serializer, byte[].class);
                        serializeMetaData(event, serializer, byte[].class);
                    });
                    return event;
                });
        return stream.toArray(EventMessage[]::new);
    }

    protected void executeStorageJob(EventMessage<?>... events) {
        UnitOfWork<?> unitOfWork = new DefaultUnitOfWork<>(null);
        unitOfWork.execute(() -> storeEvents(events));
    }

    protected void storeEvents(EventMessage<?>... events) {
        eventStore.publish(events);
    }

    protected Optional<Serializer> serializer() {
        return storageEngine instanceof AbstractEventStorageEngine ?
                Optional.of(((AbstractEventStorageEngine) storageEngine).getSerializer()) : Optional.empty();
    }

    public int getTotalEventCount() {
        return threadCount * batchSize * batchCount;
    }

    protected EventStorageEngine getStorageEngine() {
        return storageEngine;
    }

}
