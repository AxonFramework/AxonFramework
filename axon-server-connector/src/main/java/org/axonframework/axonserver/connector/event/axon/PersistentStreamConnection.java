package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.control.ProcessorInstructionHandler;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.connector.impl.StreamClosedException;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PersistentStreamConnection {


    private final Logger logger = LoggerFactory.getLogger(PersistentStreamConnection.class);

    private final String name;
    private final Configuration configuration;
    private final PersistentStreamProperties persistentStreamProperties;

    private final AtomicReference<PersistentStream> persistentStreamHolder = new AtomicReference<>();

    private volatile Consumer<List<? extends EventMessage<?>>> consumer;
    private final ScheduledExecutorService scheduler;
    private final int batchSize;

    private final Map<PersistentStreamSegment, AtomicBoolean> processGate = new ConcurrentHashMap<>();

    public PersistentStreamConnection(String name,
                                      Configuration configuration,
                                      PersistentStreamProperties persistentStreamProperties,
                                      ScheduledExecutorService scheduler,
                                      int batchSize) {
        this.name = name;
        this.configuration = configuration;
        this.persistentStreamProperties = persistentStreamProperties;
        this.scheduler = scheduler;
        this.batchSize = batchSize;
    }


    public void open(Consumer<List<? extends EventMessage<?>>> consumer) {
        this.consumer = consumer;
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
        PersistentStream persistentStream = eventChannel.openPersistentStream(name,
                                                                              axonServerConfiguration.getEventFlowControl()
                                                                                                     .getPermits(),
                                                                              axonServerConfiguration.getEventFlowControl()
                                                                                                     .getNrOfNewPermits(),
                                                                              callbacks,
                                                                              persistentStreamProperties);


        registerEventProcessor(axonServerConnectionManager, axonServerConfiguration.getContext());
        persistentStreamHolder.set(persistentStream);
    }


    private void registerEventProcessor(AxonServerConnectionManager axonServerConnectionManager, String context) {
        axonServerConnectionManager.getConnection(context)
                                   .controlChannel()
                                   .registerEventProcessor(name,
                                                           () -> EventProcessorInfo.newBuilder()
                                                                                   .setProcessorName(name)
                                                                                   .setMode("PersistentStream")
                                                                                   .setTokenStoreIdentifier("AxonServer")
                                                                                   .setActiveThreads(0)
                                                                                   .build(),
                                                           new ProcessorInstructionHandler() {
                                                               @Override
                                                               public CompletableFuture<Boolean> releaseSegment(
                                                                       int segmentId) {
                                                                   CompletableFuture<Boolean> failed = new CompletableFuture<>();
                                                                   failed.completeExceptionally(new RuntimeException(
                                                                           "Release not supported"));
                                                                   return failed;
                                                               }

                                                               @Override
                                                               public CompletableFuture<Boolean> splitSegment(
                                                                       int segmentId) {
                                                                   CompletableFuture<Boolean> failed = new CompletableFuture<>();
                                                                   failed.completeExceptionally(new RuntimeException(
                                                                           "Split not supported"));
                                                                   return failed;
                                                               }

                                                               @Override
                                                               public CompletableFuture<Boolean> mergeSegment(
                                                                       int segmentId) {
                                                                   CompletableFuture<Boolean> failed = new CompletableFuture<>();
                                                                   failed.completeExceptionally(new RuntimeException(
                                                                           "Merge not supported"));
                                                                   return failed;
                                                               }

                                                               @Override
                                                               public CompletableFuture<Void> pauseProcessor() {
                                                                   CompletableFuture<Void> failed = new CompletableFuture<>();
                                                                   failed.completeExceptionally(new RuntimeException(
                                                                           "Pause not supported"));
                                                                   return failed;
                                                               }

                                                               @Override
                                                               public CompletableFuture<Void> startProcessor() {
                                                                   CompletableFuture<Void> failed = new CompletableFuture<>();
                                                                   failed.completeExceptionally(new RuntimeException(
                                                                           "Start not supported"));
                                                                   return failed;
                                                               }
                                                           });
    }

    private void streamClosed(Throwable throwable) {
        persistentStreamHolder.set(null);
        if (throwable != null) {
            logger.info("{}: Rescheduling persistent stream", name, throwable);
            scheduler.schedule(this::start, 1, TimeUnit.SECONDS);
        }
    }

    private void segmentClosed(PersistentStreamSegment persistentStreamSegment) {
        processGate.remove(persistentStreamSegment);
        logger.info("{}: Segment closed: {}", name, persistentStreamSegment);
    }

    private void segmentOpened(PersistentStreamSegment persistentStreamSegment) {
        logger.info("{}: Segment opened: {}", name, persistentStreamSegment);
        processGate.put(persistentStreamSegment, new AtomicBoolean());
    }

    private void messageAvailable(PersistentStreamSegment persistentStreamSegment) {
        scheduler.submit(() -> readMessagesFromSegment(persistentStreamSegment));
    }

    private void readMessagesFromSegment(PersistentStreamSegment persistentStreamSegment) {
        AtomicBoolean gate = processGate.get(persistentStreamSegment);
        if (gate == null || !gate.compareAndSet(false, true)) {
            return;
        }

        boolean reschedule = true;
        try {
            int remaining = 10_000;
            GrpcMetaDataAwareSerializer serializer = new GrpcMetaDataAwareSerializer(configuration.eventSerializer());
            EventWithToken event = persistentStreamSegment.next();
            while (remaining > 0 && !persistentStreamSegment.isClosed() && event != null) {
                List<EventWithToken> batch = new LinkedList<>();
                batch.add(event);

                while (batch.size() < batchSize && (event = persistentStreamSegment.nextIfAvailable()) != null) {
                    batch.add(event);
                }
                List<TrackedEventMessage<?>> eventMessages = EventUtils.upcastAndDeserializeTrackedEvents(
                        batch.stream().map(e -> {
                            TrackingToken trackingToken = new GlobalSequenceTrackingToken(e.getToken());
                            return new TrackedDomainEventData<>(trackingToken,
                                                                new GrpcBackedDomainEventData(e.getEvent()));
                        }),
                        serializer,
                        configuration.upcasterChain()).collect(Collectors.toList());

                if (!persistentStreamSegment.isClosed()) {
                    consumer.accept(eventMessages);
                    long token = batch.get(batch.size() - 1).getToken();
                    if (!persistentStreamSegment.isClosed()) {
                        logger.debug("{}: segment {} - processed up to {}", name, persistentStreamSegment, token);
                    }
                    persistentStreamSegment.acknowledge(token);
                    event = persistentStreamSegment.next();
                    remaining -= batch.size();
                }
            }
        } catch (StreamClosedException e) {
            logger.debug("{}: Stream closed for segment {}", name, persistentStreamSegment);
            processGate.remove(persistentStreamSegment);
            reschedule = false;
            // stop loop
        } catch (InterruptedException e) {
            logger.debug("{}: Segment {} interrupted", name, persistentStreamSegment);
            processGate.remove(persistentStreamSegment);
            Thread.currentThread().interrupt();
            reschedule = false;
            // stop loop
        } catch (Exception e) {
            persistentStreamSegment.error(e.getMessage());
            logger.warn("{}: Exception while processing events for segment {}", name, persistentStreamSegment, e);
            processGate.remove(persistentStreamSegment);
            reschedule = false;
        } finally {
            gate.set(false);
            if (reschedule && persistentStreamSegment.peek() != null) {
                scheduler.submit(() -> readMessagesFromSegment(persistentStreamSegment));
            }
        }
    }

    public void close() {
        PersistentStream persistentStream = persistentStreamHolder.getAndSet(null);
        if (persistentStream != null) {
            persistentStream.close();
        }
    }
}
