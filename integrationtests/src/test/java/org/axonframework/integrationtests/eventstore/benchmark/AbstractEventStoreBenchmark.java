package org.axonframework.integrationtests.eventstore.benchmark;

import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventStoreBenchmark {

    private static final int DEFAULT_THREAD_COUNT = 100, DEFAULT_BATCH_SIZE = 50, DEFAULT_BATCH_COUNT = 50;
    private static final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final EmbeddedEventStore eventStore;
    private final EventProcessor eventProcessor;
    private final EventStorageEngine storageEngine;
    private final int threadCount, batchSize, batchCount;
    private final ExecutorService executorService;
    private final CountDownLatch remainingEvents;
    private final GapDetector gapDetector;

    protected AbstractEventStoreBenchmark(EventStorageEngine storageEngine) {
        this(storageEngine, DEFAULT_THREAD_COUNT, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_COUNT);
    }

    protected AbstractEventStoreBenchmark(EventStorageEngine storageEngine, int threadCount, int batchSize, int batchCount) {
        this.eventStore = new EmbeddedEventStore(this.storageEngine = storageEngine);
        this.threadCount = threadCount;
        this.batchSize = batchSize;
        this.batchCount = batchCount;
        this.remainingEvents = new CountDownLatch(getTotalEventCount());
        this.gapDetector = new GapDetector();
        this.eventProcessor =
                new TrackingEventProcessor("benchmark", new SimpleEventHandlerInvoker(gapDetector), eventStore,
                                           new InMemoryTokenStore());
        this.executorService = Executors.newFixedThreadPool(threadCount);
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
        logger.info(
                "Read {} events in {} seconds. That's about {} events/sec. {} tracking tokens had one or more gaps. " +
                        "Largest gap was {}.", getTotalEventCount(),
                decimalFormat.format(stopWatch.getTotalTimeSeconds()),
                (int) (getTotalEventCount() / stopWatch.getTotalTimeSeconds()), gapDetector.getTokensWithGaps(),
                gapDetector.getLargestGap());
        logger.info("Cleaning up");
        cleanUpAfterBenchmark();
    }

    protected void prepareForBenchmark() {
        eventProcessor.start();
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
            executeStorageJob(aggregateId, i, batchSize);
            return i;
        }).collect(Collectors.toList());
    }

    protected void executeStorageJob(String aggregateId, int batchIndex, int batchSize) {
        UnitOfWork<?> unitOfWork = new DefaultUnitOfWork<>(null);
        unitOfWork.execute(() -> storeEvents(createEvents(aggregateId, batchIndex * batchSize, batchSize)));
    }

    protected EventMessage<?>[] createEvents(String aggregateId, int startSequenceNumber, int count) {
        Stream<EventMessage<?>> stream = IntStream.range(startSequenceNumber, startSequenceNumber + count)
                .mapToObj(sequenceNumber -> createEvent(aggregateId, sequenceNumber));
        return stream.toArray(EventMessage[]::new);
    }

    protected void storeEvents(EventMessage<?>... events) {
        eventStore.publish(events);
    }

    public int getTotalEventCount() {
        return threadCount * batchSize * batchCount;
    }

    protected EventStorageEngine getStorageEngine() {
        return storageEngine;
    }

    private class GapDetector implements EventListener {

        private final NavigableSet<GapAwareTrackingToken> trackingTokensWithGap = new ConcurrentSkipListSet<>();

        @Override
        public void handle(EventMessage event) throws Exception {
            remainingEvents.countDown();
            TrackedEventMessage<?> trackedEvent = (TrackedEventMessage<?>) event;
            if (trackedEvent.trackingToken() instanceof GapAwareTrackingToken) {
                GapAwareTrackingToken trackingToken = (GapAwareTrackingToken) trackedEvent.trackingToken();
                if (trackingToken.hasGaps()) {
                    trackingTokensWithGap.add(trackingToken);
                }
            }
        }

        private int getTokensWithGaps() {
            return trackingTokensWithGap.size();
        }

        private int getLargestGap() {
            return trackingTokensWithGap.stream()
                    .reduce((t1, t2) -> t1.getGaps().size() > t2.getGaps().size() ? t1 : t2)
                    .map(GapAwareTrackingToken::getGaps).map(Collection::size).orElse(0);
        }
    }
}
