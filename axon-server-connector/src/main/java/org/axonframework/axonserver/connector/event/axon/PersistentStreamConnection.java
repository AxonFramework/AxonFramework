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
package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.connector.impl.StreamClosedException;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventUtils;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
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
 * Receives the events for a persistent stream and passes batches of events to the event consumer.
 */
public class PersistentStreamConnection {

    private static final int MAX_MESSAGES_PER_RUN = 10_000;
    private final Logger logger = LoggerFactory.getLogger(PersistentStreamConnection.class);

    private final String streamId;
    private final Configuration configuration;
    private final PersistentStreamProperties persistentStreamProperties;

    private final AtomicReference<PersistentStream> persistentStreamHolder = new AtomicReference<>();

    private final AtomicReference<Consumer<List<? extends EventMessage<?>>>> consumer = new AtomicReference<>(events -> {
    });
    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final Map<Integer, SegmentConnection> segments = new ConcurrentHashMap<>();
    private final AtomicInteger retrySeconds = new AtomicInteger(1);


    /**
     * Instantiates a connection for a persistent stream.
     * @param streamId the identifier of the persistent stream
     * @param configuration global configuration of Axon components
     * @param persistentStreamProperties properties for the persistent stream
     * @param scheduler scheduler thread pool to schedule tasks
     * @param batchSize the batch size for collecting events
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
    }


    /**
     * Initiates the connection to Axon Server to read events from the persistent stream.
     * @param consumer the consumer of batches of event messages
     */
    public void open(Consumer<List<? extends EventMessage<?>>> consumer) {
        this.consumer.set(consumer);
        start();
    }

    private void start() {
        AxonServerConnectionManager axonServerConnectionManager = configuration.getComponent(AxonServerConnectionManager.class);
        AxonServerConfiguration axonServerConfiguration = configuration.getComponent(AxonServerConfiguration.class);

        PersistentStreamCallbacks callbacks = new PersistentStreamCallbacks(this::segmentOpened,
                                                                            this::segmentClosed,
                                                                            this::messageAvailable,
                                                                            this::streamClosed);
        EventChannel eventChannel = axonServerConnectionManager.getConnection(
                axonServerConfiguration.getContext()).eventChannel();
        PersistentStream persistentStream = eventChannel.openPersistentStream(streamId,
                                                                              axonServerConfiguration.getEventFlowControl()
                                                                                                     .getPermits(),
                                                                              axonServerConfiguration.getEventFlowControl()
                                                                                                     .getNrOfNewPermits(),
                                                                              callbacks,
                                                                              persistentStreamProperties);
        persistentStreamHolder.set(persistentStream);
    }


    private void streamClosed(Throwable throwable) {
        persistentStreamHolder.set(null);
        if (throwable != null) {
            logger.info("{}: Rescheduling persistent stream", streamId, throwable);
            scheduler.schedule(this::start,
                               retrySeconds.getAndUpdate(current -> Math.max(60, current *2)),
                               TimeUnit.SECONDS);
        }
    }

    private void segmentClosed(PersistentStreamSegment persistentStreamSegment) {
        SegmentConnection segmentConnection = segments.remove(persistentStreamSegment.segment());
        if (segmentConnection != null) {
            segmentConnection.close();
        }

        logger.info("{}: Segment closed: {}", streamId, persistentStreamSegment);
    }

    private void segmentOpened(PersistentStreamSegment persistentStreamSegment) {
        logger.info("{}: Segment opened: {}", streamId, persistentStreamSegment);
        retrySeconds.set(1);
        segments.put(persistentStreamSegment.segment(), new SegmentConnection(persistentStreamSegment));
    }

    private void messageAvailable(PersistentStreamSegment persistentStreamSegment) {
        SegmentConnection segmentConnection = segments.get(persistentStreamSegment.segment());
        if (segmentConnection != null) {
            segmentConnection.messageAvailable();
        }
    }

    /**
     * Closes the persistent stream connection to Axon Server.
     */
    public void close() {
        PersistentStream persistentStream = persistentStreamHolder.getAndSet(null);
        if (persistentStream != null) {
            persistentStream.close();
        }
    }

    private class SegmentConnection {
        private final AtomicBoolean processGate = new AtomicBoolean();
        private final AtomicBoolean doneConfirmed = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final PersistentStreamSegment persistentStreamSegment;


        public SegmentConnection(PersistentStreamSegment persistentStreamSegment) {
            this.persistentStreamSegment = persistentStreamSegment;
        }

        public void messageAvailable() {
            if (!processGate.get()) {
                scheduler.submit(() -> readMessagesFromSegment(persistentStreamSegment));
            }
        }

        private void readMessagesFromSegment(PersistentStreamSegment persistentStreamSegment) {
            if (!processGate.compareAndSet(false, true)) {
                return;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("{}[{}] readMessagesFromSegment - closed: {}",
                             streamId, persistentStreamSegment.segment(), persistentStreamSegment.isClosed());
            }

            try {
                int remaining = Math.max(MAX_MESSAGES_PER_RUN, batchSize);
                GrpcMetaDataAwareSerializer serializer = new GrpcMetaDataAwareSerializer(configuration.eventSerializer());
                while (remaining > 0 && !closed.get()) {
                    List<EventWithToken> batch = readBatch(persistentStreamSegment);
                    if (batch.isEmpty()) {
                        break;
                    }

                    List<TrackedEventMessage<?>> eventMessages = upcastAndDeserialize(batch, serializer);
                    if (!closed.get()) {
                        consumer.get().accept(eventMessages);
                        if (logger.isTraceEnabled()) {
                            logger.trace("{}/{} processed {} entries",
                                         streamId,
                                         persistentStreamSegment.segment(),
                                         eventMessages.size());
                        }
                        long token = batch.get(batch.size() - 1).getToken();
                        persistentStreamSegment.acknowledge(token);
                        remaining -= batch.size();
                    }
                }

                acknowledgeDoneWhenClosed(persistentStreamSegment);
            } catch (StreamClosedException e) {
                logger.debug("{}: Stream closed for segment {}", streamId, persistentStreamSegment.segment());
                close();
                // stop loop
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                persistentStreamSegment.error(e.getMessage());
                logger.warn("{}: Exception while processing events for segment {}",
                            streamId, persistentStreamSegment.segment(), e);
                close();
            } finally {
                processGate.set(false);
                if (!closed.get() && persistentStreamSegment.peek() != null) {
                    scheduler.submit(() -> readMessagesFromSegment(persistentStreamSegment));
                }
            }
        }

        private void acknowledgeDoneWhenClosed(PersistentStreamSegment persistentStreamSegment) {
            if (closed.get() &&
                    doneConfirmed.compareAndSet(false, true)) {
                persistentStreamSegment.acknowledge(PENDING_WORK_DONE_MARKER);
            }
        }

        private List<EventWithToken> readBatch(PersistentStreamSegment persistentStreamSegment)
                throws InterruptedException {
            List<EventWithToken> batch = new LinkedList<>();
            // read first one without waiting
            EventWithToken event = persistentStreamSegment.nextIfAvailable();
            if (event == null) {
                return batch;
            }
            batch.add(event);
            // allow next event to arrive within a small amount of time
            while (batch.size() < batchSize && !closed.get() &&
                    (event = persistentStreamSegment.nextIfAvailable(1,TimeUnit.MILLISECONDS)) != null) {
                batch.add(event);
            }
            return batch;
        }

        private List<TrackedEventMessage<?>> upcastAndDeserialize(List<EventWithToken> batch,
                                                                  GrpcMetaDataAwareSerializer serializer) {
            return EventUtils.upcastAndDeserializeTrackedEvents(
                                     batch.stream()
                                          .map(e -> {
                                              TrackingToken trackingToken = new GlobalSequenceTrackingToken(e.getToken());
                                              return new TrackedDomainEventData<>(trackingToken,
                                                                                  new GrpcBackedDomainEventData(e.getEvent()));
                                          }),
                                     serializer,
                                     configuration.upcasterChain())
                             .collect(Collectors.toList());
        }

        public void close() {
            closed.set(true);
        }
    }
}
