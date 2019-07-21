package org.axonframework.queryhandling;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.queryhandling.updatestore.model.SubscriptionEntity;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.FluxSink;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @see SimpleQueryUpdateEmitter
 */
@Slf4j
public class DistributedQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final List<EmittedTuple> EMITTED_PAIRS = new CopyOnWriteArrayList<>();

    @Resource
    private QueryBus localSegment;

    @Resource
    private QueryUpdatePollingService queryUpdatePollingService;

    @Resource
    private QueryUpdateStore queryUpdateStore;

    @Resource
    private Serializer messageSerializer;

    @Value("${axon.queryhandling.DistributedQueryUpdateEmitter.emittedExpirationMillies:100}")
    private long emittedExpirationMillis;

    @SuppressWarnings("unchecked")
    private final Thread emitter = new Thread(() -> {
        while (true) {
            queryUpdateStore.getCurrentSubscriptions().forEach(
                    sub -> {
                        if (sub == null)
                            return; // skip

                        SubscriptionQueryMessage msg = sub.asSubscriptionQueryMessage(messageSerializer);
                        List<EmittedTuple> processed = EMITTED_PAIRS.stream()
                                .filter(p -> p.getFilter().test(msg))
                                .peek(p ->
                                        queryUpdateStore.postUpdate(sub, p.getUpdate())
                                ).collect(Collectors.toList());

                        EMITTED_PAIRS.removeAll(processed);

                        // expiration
                        Instant now = Instant.now();
                        Duration maxAge = Duration.ofMillis(emittedExpirationMillis);
                        List<EmittedTuple> expired = EMITTED_PAIRS.stream().filter(p ->
                                Duration.between(now, p.getCreationTime()).compareTo(maxAge) > 0
                        ).collect(Collectors.toList());
                        EMITTED_PAIRS.removeAll(expired);
                    }
            );

            Thread.yield();
        }
    });

    @PostConstruct
    public void startEmitterTimer() {
        emitter.start();
    }

    @SuppressWarnings("deprecation")
    @PreDestroy
    public void stopEmitterTimer() {
        emitter.stop();
    }

    @Override
    public <U> void emit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter, SubscriptionQueryUpdateMessage<U> update) {
        localSegment.queryUpdateEmitter().emit(filter, update);

        EMITTED_PAIRS.add(new EmittedTuple<>(filter, update));
    }

    @Override
    public void complete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        localSegment.queryUpdateEmitter().complete(filter);
    }


    @Override
    public void completeExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        localSegment.queryUpdateEmitter().completeExceptionally(filter, cause);
    }

    @Override
    public boolean queryUpdateHandlerRegistered(SubscriptionQueryMessage<?, ?, ?> query) {
        return queryUpdateStore.subscriptionExists(query);
    }

    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(SubscriptionQueryMessage<?, ?, ?> query, SubscriptionQueryBackpressure backpressure, int updateBufferSize) {
        /*
         * 1. persist subscription
         * 2. initialize flux
         * 3. poll on updates and feed them into the flux
         * 4. subscribe to local updates
         * 5. dispose all on Registration.cancel()
         */

        // Persist subscription
        SubscriptionEntity subscriptionEntity = queryUpdateStore.createSubscription(query);


        // Initialize Flux
        EmitterProcessor<SubscriptionQueryUpdateMessage<U>> processor = EmitterProcessor.create(updateBufferSize);
        FluxSink<SubscriptionQueryUpdateMessage<U>> sink = processor.sink(backpressure.getOverflowStrategy());
        sink.onDispose(() -> queryUpdateStore.removeSubscription(query));
        FluxSinkWrapper<SubscriptionQueryUpdateMessage<U>> fluxSinkWrapper = new FluxSinkWrapper<>(sink);


        // Start polling for updates
        Registration pollingRegistration = queryUpdatePollingService.startPolling(
                subscriptionEntity.getId(),
                fluxSinkWrapper);

        // Subscribe to local updates
        UpdateHandlerRegistration<U> updateHandlerRegistration = localSegment.queryUpdateEmitter().registerUpdateHandler(query, backpressure, updateBufferSize);
        Disposable localSubscription = updateHandlerRegistration.getUpdates()
                .subscribe(fluxSinkWrapper::next);


        // Dispose all on Registration.cancel()
        Registration registration = () -> {
            boolean pollingCanceled =
                    pollingRegistration.cancel();
            fluxSinkWrapper.complete();

            localSubscription.dispose();
            boolean localRegistrationCanceled =
                    updateHandlerRegistration.getRegistration().cancel();

            return pollingCanceled && localRegistrationCanceled;
        };

        return new UpdateHandlerRegistration<>(registration,
                processor.replay(updateBufferSize).autoConnect());
    }

    @Override
    public Registration registerDispatchInterceptor(MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> dispatchInterceptor) {
        return localSegment.queryUpdateEmitter().registerDispatchInterceptor(dispatchInterceptor);
    }

    @lombok.Value
    class EmittedTuple<U> {
        private final Predicate<SubscriptionQueryMessage<?, ?, U>> filter;
        private final SubscriptionQueryUpdateMessage<U> update;
        private Instant creationTime = Instant.now();
    }
}
