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
package org.axonframework.axonserver.connector.event.axon;

import com.google.common.base.Strings;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.connector.impl.StreamClosedException;
import io.axoniq.axonserver.grpc.streams.PersistentStreamEvent;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.axoniq.axonserver.connector.event.PersistentStreamSegment.PENDING_WORK_DONE_MARKER;

/**
 * A connection instance receiving the events for a persistent stream to pass on in batches of events to an event
 * consumer.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
public class PersistentStreamConnection {

    private static final int MAX_RETRY_INTERVAL_SECONDS = 60;
    private static final int MIN_RETRY_INTERVAL_SECONDS = 1;
    private final Logger logger = LoggerFactory.getLogger(PersistentStreamConnection.class);

    private final String streamId;
    private final Configuration configuration;
    private final PersistentStreamProperties persistentStreamProperties;

    private final AtomicReference<PersistentStream> persistentStreamHolder = new AtomicReference<>();

    private static final Consumer<List<? extends EventMessage<?>>> NO_OP_CONSUMER = events -> {
    };
    private final AtomicReference<Consumer<List<? extends EventMessage<?>>>> consumer =
            new AtomicReference<>(NO_OP_CONSUMER);
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final Map<Integer, SegmentConnection> segments = new ConcurrentHashMap<>();
    private final AtomicInteger retrySeconds = new AtomicInteger(MIN_RETRY_INTERVAL_SECONDS);

    private final String defaultContext;

    /**
     * Instantiates a connection for a persistent stream.
     *
     * @param streamId                   The unique identifier of the persistent stream.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     */
    public PersistentStreamConnection(String streamId,
                                      Configuration configuration,
                                      PersistentStreamProperties persistentStreamProperties,
                                      ScheduledExecutorService scheduler,
                                      int batchSize) {
        this.streamId = streamId;
        this.configuration = configuration;
        this.persistentStreamProperties = persistentStreamProperties;
        this.scheduler = scheduler;
        this.batchSize = batchSize;
        this.defaultContext = null;
    }

    /**
     * Instantiates a connection for a persistent stream.
     *
     * @param streamId                   The unique identifier of the persistent stream.
     * @param configuration              Global configuration of Axon components.
     * @param persistentStreamProperties Properties for the persistent stream.
     * @param scheduler                  Scheduler thread pool to schedule tasks.
     * @param batchSize                  The batch size for collecting events.
     * @param defaultContext             The default context to use for the connection.
     */
    public PersistentStreamConnection(String streamId,
                                      Configuration configuration,
                                      PersistentStreamProperties persistentStreamProperties,
                                      ScheduledExecutorService scheduler,
                                      int batchSize,
                                      String defaultContext) {
        this.streamId = streamId;
        this.configuration = configuration;
        this.persistentStreamProperties = persistentStreamProperties;
        this.scheduler = scheduler;
        this.batchSize = batchSize;
        this.defaultContext = defaultContext;
    }


    /**
     * Initiates the connection to Axon Server to read events from the persistent stream. The stream can be opened just
     * once with a single consumer. The connection is exclusive to that consumer. If you try to open it again, an
     * {@link IllegalStateException} is thrown.
     *
     * @param consumer The consumer of batches of event messages.
     * @throws IllegalStateException if the stream was already opened.
     */
    public void open(Consumer<List<? extends EventMessage<?>>> consumer) {
        if (!this.consumer.compareAndSet(NO_OP_CONSUMER, consumer)) {
            throw new IllegalStateException(
                    String.format("%s: Persistent Stream has already been opened.", streamId));
        }
        start();
    }

    private void start() {
        AxonServerConnectionManager axonServerConnectionManager =
                configuration.getComponent(AxonServerConnectionManager.class);
        AxonServerConfiguration axonServerConfiguration = configuration.getComponent(AxonServerConfiguration.class);
        String context = Strings.isNullOrEmpty(defaultContext) ? axonServerConfiguration.getContext() : defaultContext;
        PersistentStreamCallbacks callbacks = new PersistentStreamCallbacks(this::segmentOpened,
                                                                            this::segmentClosed,
                                                                            this::messageAvailable,
                                                                            this::streamClosed);

        EventChannel eventChannel = axonServerConnectionManager.getConnection(context).eventChannel();
        PersistentStream persistentStream = eventChannel.openPersistentStream(
                streamId,
                axonServerConfiguration.getEventFlowControl().getPermits(),
                axonServerConfiguration.getEventFlowControl().getNrOfNewPermits(),
                callbacks,
                persistentStreamProperties
        );
        persistentStreamHolder.set(persistentStream);
    }


    private void segmentOpened(PersistentStreamSegment persistentStreamSegment) {
        logger.info("Segment opened: {}", persistentStreamSegment);
        retrySeconds.set(1);
        segments.put(persistentStreamSegment.segment(), new SegmentConnection(persistentStreamSegment));
    }

    private void segmentClosed(PersistentStreamSegment persistentStreamSegment) {
        segments.remove(persistentStreamSegment.segment());
        logger.info("Segment closed: {}", persistentStreamSegment);
    }

    private void messageAvailable(PersistentStreamSegment persistentStreamSegment) {
        SegmentConnection segmentConnection = segments.get(persistentStreamSegment.segment());
        if (segmentConnection != null) {
            segmentConnection.messageAvailable();
        }
    }

    private void streamClosed(Throwable throwable) {
        persistentStreamHolder.set(null);
        if (throwable != null) {
            logger.info("{}: Rescheduling persistent stream", streamId, throwable);
            scheduler.schedule(this::start,
                               retrySeconds.getAndUpdate(current -> Math.min(MAX_RETRY_INTERVAL_SECONDS, current * 2)),
                               TimeUnit.SECONDS);
        }
    }

    /**
     * Closes the persistent stream connection to Axon Server.
     */
    public void close() {
        PersistentStream persistentStream = persistentStreamHolder.getAndSet(null);
        if (persistentStream != null) {
            persistentStream.close();
            this.consumer.set(NO_OP_CONSUMER);
        }
    }

    private interface SegmentState {

        void readMessages();
    }

    private class SegmentConnection {

        private final AtomicBoolean processGate = new AtomicBoolean();
        private final AtomicBoolean doneConfirmed = new AtomicBoolean();
        private final PersistentStreamSegment persistentStreamSegment;
        private final GrpcMetaDataAwareSerializer serializer;
        private final AtomicReference<SegmentState> currentState = new AtomicReference<>(new ProcessingState());

        public SegmentConnection(PersistentStreamSegment persistentStreamSegment) {
            this.persistentStreamSegment = persistentStreamSegment;
            serializer = new GrpcMetaDataAwareSerializer(configuration.getComponent(Serializer.class));
        }

        private class RetryState implements SegmentState {

            private final List<PersistentStreamEvent> batch;
            private final AtomicInteger retryInterval = new AtomicInteger(MIN_RETRY_INTERVAL_SECONDS);

            public RetryState(List<PersistentStreamEvent> batch) {
                this.batch = batch;
                scheduler.schedule(this::retry, retryInterval.get(), TimeUnit.SECONDS);
            }

            private void retry() {
                try {
                    processBatch(batch);
                    currentState.set(new ProcessingState());
                    scheduler.submit(SegmentConnection.this::readMessagesFromSegment);
                } catch (Exception ex) {
                    int interval = retryInterval.updateAndGet(old -> Math.min(old * 2, MAX_RETRY_INTERVAL_SECONDS));
                    logger.warn("{}: Exception while retrying events for segment {}, retrying after {} seconds",
                                streamId, persistentStreamSegment.segment(), interval, ex);

                    scheduler.schedule(this::retry, interval, TimeUnit.SECONDS);
                }
            }

            @Override
            public void readMessages() {
                // no-op, first need to retry the cached events
            }
        }

        private class ProcessingState implements SegmentState {

            @Override
            public void readMessages() {
                if (!processGate.compareAndSet(false, true)) {
                    return;
                }

                if (logger.isTraceEnabled()) {
                    logger.trace("{}[{}] readMessagesFromSegment - closed: {}",
                                 streamId, persistentStreamSegment.segment(), persistentStreamSegment.isClosed());
                }

                try {
                    if (!persistentStreamSegment.isClosed()) {
                        List<PersistentStreamEvent> batch = readBatch(persistentStreamSegment);
                        if (!batch.isEmpty()) {
                            try {
                                processBatch(batch);
                            } catch (Exception ex) {
                                logger.warn(
                                        "{}: Exception while processing events for segment {}, retrying after {} second",
                                        streamId,
                                        persistentStreamSegment.segment(),
                                        MIN_RETRY_INTERVAL_SECONDS,
                                        ex);

                                currentState.set(new RetryState(batch));
                            }
                        }
                    }

                    acknowledgeDoneWhenClosed(persistentStreamSegment);
                } catch (StreamClosedException e) {
                    logger.debug("{}: Stream closed for segment {}", streamId, persistentStreamSegment.segment());
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    persistentStreamSegment.error(e.getMessage());
                    logger.warn("{}: Exception while processing events for segment {}",
                                streamId, persistentStreamSegment.segment(), e);
                } finally {
                    processGate.set(false);
                    if (!persistentStreamSegment.isClosed() && persistentStreamSegment.peek() != null) {
                        scheduler.submit(SegmentConnection.this::readMessagesFromSegment);
                    }
                }
            }

            private List<PersistentStreamEvent> readBatch(
                    PersistentStreamSegment persistentStreamSegment
            ) throws InterruptedException {
                List<PersistentStreamEvent> batch = new LinkedList<>();
                // read first one without waiting
                PersistentStreamEvent event = persistentStreamSegment.nextIfAvailable();
                if (event == null) {
                    return batch;
                }
                batch.add(event);
                // allow next event to arrive within a small amount of time
                while (batch.size() < batchSize && !persistentStreamSegment.isClosed()
                        && (event = persistentStreamSegment.nextIfAvailable(1, TimeUnit.MILLISECONDS)) != null) {
                    batch.add(event);
                }
                return batch;
            }

            private void acknowledgeDoneWhenClosed(PersistentStreamSegment persistentStreamSegment) {
                if (persistentStreamSegment.isClosed() && doneConfirmed.compareAndSet(false, true)) {
                    persistentStreamSegment.acknowledge(PENDING_WORK_DONE_MARKER);
                }
            }
        }

        private void processBatch(List<PersistentStreamEvent> batch) {
            List<TrackedEventMessage<?>> eventMessages = upcastAndDeserialize(batch);
            if (!persistentStreamSegment.isClosed()) {
                long token = batch.get(batch.size() - 1).getEvent().getToken();
                consumer.get().accept(eventMessages);
                if (logger.isTraceEnabled()) {
                    logger.trace("{}/{} processed {} entries",
                                 streamId,
                                 persistentStreamSegment.segment(),
                                 eventMessages.size());
                }
                persistentStreamSegment.acknowledge(token);
            }
        }

        public void messageAvailable() {
            if (!processGate.get()) {
                scheduler.submit(this::readMessagesFromSegment);
            }
        }

        private void readMessagesFromSegment() {
            currentState.get().readMessages();
        }

        private List<TrackedEventMessage<?>> upcastAndDeserialize(List<PersistentStreamEvent> batch) {
            return EventUtils.upcastAndDeserializeTrackedEvents(
                                     batch.stream()
                                          .map(e -> {
                                              TrackingToken trackingToken = createToken(e);
                                              return new TrackedDomainEventData<>(
                                                      trackingToken,
                                                      new GrpcBackedDomainEventData(e.getEvent().getEvent())
                                              );
                                          }),
                                     serializer,
                                     configuration.getComponent(EventUpcasterChain.class))
                             .collect(Collectors.toList());
        }


        private TrackingToken createToken(PersistentStreamEvent event) {
            if (!event.getReplay()) {
                return new GlobalSequenceTrackingToken(event.getEvent().getToken());
            }
            return ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(event.getEvent().getToken() + 1),
                                                 new GlobalSequenceTrackingToken(event.getEvent().getToken()));
        }
    }
}
