package org.axonframework.axonserver.connector.event.axon;

import io.axoniq.axonserver.connector.ResultStreamPublisher;
import io.axoniq.axonserver.connector.control.ProcessorInstructionHandler;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.PersistentStream;
import io.axoniq.axonserver.connector.event.PersistentStreamCallbacks;
import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import io.axoniq.axonserver.connector.event.PersistentStreamSegment;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PersistentStreamConnection {
    private final Logger logger = LoggerFactory.getLogger(PersistentStreamConnection.class);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(4);

    private final String name;
    private final Configuration configuration;
    private final PersistentStreamProperties persistentStreamProperties;

    private final Map<Integer, Scheduler> executorMap = new HashMap<>();
    private final Map<Integer, Sinks.Many<Long>> progressMap = new ConcurrentHashMap<>();
    private final AtomicReference<PersistentStream> persistentStreamHolder = new AtomicReference<>();

    private volatile Consumer<List<? extends EventMessage<?>>> consumer;
    private final int batchSize;

    public PersistentStreamConnection(String name, Configuration configuration, PersistentStreamProperties persistentStreamProperties,
                                      int batchSize) {
        this.name = name;
        this.configuration = configuration;
        this.persistentStreamProperties = persistentStreamProperties;
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
                                                                            null,
                                                                            this::streamClosed);
        EventChannel eventChannel = axonServerConnectionManager.getConnection(
                axonServerConfiguration.getContext()).eventChannel();
        PersistentStream persistentStream = eventChannel.openPersistentStream(name,
                                                                              100,
                                                                              50,
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
                                                                                   .setActiveThreads(
                                                                                           progressMap.size())
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
            logger.info("Rescheduling persistent stream: {}", name, throwable);
            scheduledExecutorService.schedule(this::start, 1, TimeUnit.SECONDS);
        }
    }

    private void segmentClosed(int i) {
        logger.info("Segment closed: {}", i);
        Scheduler scheduler = executorMap.remove(i);
        if (scheduler != null) {
            scheduler.dispose();
        }

        Sinks.Many<Long> sink = progressMap.remove(i);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private void segmentOpened(PersistentStreamSegment persistentStreamSegment) {
        logger.info("Segment opened: {}", persistentStreamSegment.segment());
        Sinks.Many<Long> ackEmitter = Sinks.many()
                                           .unicast()
                                           .onBackpressureBuffer();

        ackEmitter.asFlux()
//                  .bufferTimeout(10, Duration.ofSeconds(1))
                  .subscribe(ack -> persistentStreamSegment.acknowledge(ack),
                             Throwable::printStackTrace,
                             () -> {
                             });

        Scheduler publisherScheduler = executorMap.computeIfAbsent(persistentStreamSegment.segment(),
                                                                   s -> Schedulers.newSingle("publisher", true));
        progressMap.put(persistentStreamSegment.segment(), ackEmitter);
        GrpcMetaDataAwareSerializer serializer = new GrpcMetaDataAwareSerializer(configuration.eventSerializer());
        Flux.from(new ResultStreamPublisher<>(() -> persistentStreamSegment))
            .subscribeOn(Schedulers.fromExecutorService(scheduledExecutorService))
            .publishOn(publisherScheduler)
            .bufferTimeout(batchSize, Duration.ofMillis(1))
            .concatMap(events -> {
                List<TrackedEventMessage<?>> eventMessages = EventUtils.upcastAndDeserializeTrackedEvents(
                        events.stream().map(e -> {
                            TrackingToken trackingToken = new GlobalSequenceTrackingToken(e.getToken());
                            return new TrackedDomainEventData<>(trackingToken,
                                                                new GrpcBackedDomainEventData(e.getEvent()));
                        }),
                        serializer,
                        configuration.upcasterChain()).collect(Collectors.toList());

                consumer.accept(eventMessages);
                return Mono.just(events.get(events.size() - 1).getToken());
            }, batchSize)
            .subscribe(ackEmitter::tryEmitNext);
    }

    public void close() {
        PersistentStream persistentStream = persistentStreamHolder.getAndSet(null);
        if (persistentStream != null) {
            persistentStream.close();
        }
    }
}
