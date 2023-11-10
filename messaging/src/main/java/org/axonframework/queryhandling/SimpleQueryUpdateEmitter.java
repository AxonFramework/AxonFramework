/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.OptionalResponseType;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of {@link QueryUpdateEmitter} that uses Project Reactor to implement Update Handlers.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.0
 */
public class SimpleQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryUpdateEmitter.class);

    private static final String QUERY_UPDATE_TASKS_RESOURCE_KEY = "/update-tasks";

    private final MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor;
    private final QueryUpdateEmitterSpanFactory spanFactory;

    private final ConcurrentMap<SubscriptionQueryMessage<?, ?, ?>, SinkWrapper<?>> updateHandlers =
            new ConcurrentHashMap<>();
    private final List<MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>>> dispatchInterceptors =
            new CopyOnWriteArrayList<>();

    /**
     * Instantiate a {@link SimpleQueryUpdateEmitter} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleQueryUpdateEmitter} instance
     */
    protected SimpleQueryUpdateEmitter(Builder builder) {
        builder.validate();
        this.updateMessageMonitor = builder.updateMessageMonitor;
        this.spanFactory = builder.spanFactory;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleQueryUpdateEmitter}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor} and the {@link QueryBusSpanFactory}
     * defaults to a {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory}.
     *
     * @return a Builder to be able to create a {@link SimpleQueryUpdateEmitter}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean queryUpdateHandlerRegistered(@Nonnull SubscriptionQueryMessage<?, ?, ?> query) {
        return updateHandlers.keySet()
                             .stream()
                             .anyMatch(m -> m.getIdentifier().equals(query.getIdentifier()));
    }

    @Override
    public <U> UpdateHandlerRegistration<U> registerUpdateHandler(@Nonnull SubscriptionQueryMessage<?, ?, ?> query,
                                                                  int updateBufferSize) {
        Sinks.Many<SubscriptionQueryUpdateMessage<U>> sink = Sinks.many().replay().limit(updateBufferSize);
        SinksManyWrapper<SubscriptionQueryUpdateMessage<U>> sinksManyWrapper = new SinksManyWrapper<>(sink);

        Runnable removeHandler = () -> updateHandlers.remove(query);
        Registration registration = () -> {
            removeHandler.run();
            return true;
        };

        updateHandlers.put(query, sinksManyWrapper);
        Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux = sink.asFlux()
                                                                        .doOnCancel(removeHandler)
                                                                        .doOnTerminate(removeHandler);
        return new UpdateHandlerRegistration<>(registration, updateMessageFlux, sinksManyWrapper::complete);
    }

    @Override
    public <U> void emit(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                         @Nonnull SubscriptionQueryUpdateMessage<U> update) {
        SubscriptionQueryUpdateMessage<U> updateMessage = spanFactory.propagateContext(update);
        Span span = spanFactory.createUpdateScheduleEmitSpan(updateMessage);
        span.run(() -> {
            Span doEmitSpan = spanFactory.createUpdateEmitSpan(updateMessage);
            runOnAfterCommitOrNow(doEmitSpan.wrapRunnable(
                    () -> doEmit(filter, intercept(spanFactory.propagateContext(updateMessage)))));
        });
    }

    private <U> SubscriptionQueryUpdateMessage<U> intercept(SubscriptionQueryUpdateMessage<U> message) {
        SubscriptionQueryUpdateMessage<U> intercepted = message;
        for (MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor : dispatchInterceptors) {
            //noinspection unchecked
            intercepted = (SubscriptionQueryUpdateMessage<U>) interceptor.handle(intercepted);
        }
        return intercepted;
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        runOnAfterCommitOrNow(() -> doComplete(filter));
    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage<?, ?, ?>> filter,
                                      @Nonnull Throwable cause) {
        runOnAfterCommitOrNow(() -> doCompleteExceptionally(filter, cause));
    }

    @Override
    public @Nonnull
    Registration registerDispatchInterceptor(
            @Nonnull MessageDispatchInterceptor<? super SubscriptionQueryUpdateMessage<?>> interceptor) {
        dispatchInterceptors.add(interceptor);
        return () -> dispatchInterceptors.remove(interceptor);
    }

    @SuppressWarnings("unchecked")
    private <U> void doEmit(Predicate<SubscriptionQueryMessage<?, ?, U>> filter,
                            SubscriptionQueryUpdateMessage<U> update) {
        updateHandlers.keySet()
                      .stream()
                      .filter(payloadMatchesQueryResponseType(update.getPayloadType()))
                      .filter(sqm -> filter.test((SubscriptionQueryMessage<?, ?, U>) sqm))
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(uh -> doEmit(query, uh, update)));
    }

    private Predicate<SubscriptionQueryMessage<?, ?, ?>> payloadMatchesQueryResponseType(Class<?> payloadType) {
        return sqm -> {
            if (sqm.getUpdateResponseType() instanceof MultipleInstancesResponseType) {
                return payloadType.isArray() || Iterable.class.isAssignableFrom(payloadType);
            }
            if (sqm.getUpdateResponseType() instanceof OptionalResponseType) {
                return Optional.class.isAssignableFrom(payloadType);
            }
            if (sqm.getUpdateResponseType() instanceof PublisherResponseType) {
                return Publisher.class.isAssignableFrom(payloadType);
            }
            return sqm.getUpdateResponseType().getExpectedResponseType().isAssignableFrom(payloadType);
        };
    }

    @SuppressWarnings("unchecked")
    private <U> void doEmit(SubscriptionQueryMessage<?, ?, ?> query, SinkWrapper<?> updateHandler,
                            SubscriptionQueryUpdateMessage<U> update) {
        MessageMonitor.MonitorCallback monitorCallback = updateMessageMonitor.onMessageIngested(update);
        try {
            ((SinkWrapper<SubscriptionQueryUpdateMessage<U>>) updateHandler).next(update);
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                "The subscription will be cancelled. Exception summary: {}",
                        query.getQueryName(), e.toString());
            monitorCallback.reportFailure(e);
            updateHandlers.remove(query);
            emitError(query, e, updateHandler);
        }
    }

    private void doComplete(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter) {
        updateHandlers.keySet()
                      .stream()
                      .filter(filter)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(updateHandler -> {
                                                    try {
                                                        updateHandler.complete();
                                                    } catch (Exception e) {
                                                        emitError(query, e, updateHandler);
                                                    }
                                                }));
    }

    private void emitError(SubscriptionQueryMessage<?, ?, ?> query, Throwable cause,
                           SinkWrapper<?> updateHandler) {
        try {
            updateHandler.error(cause);
        } catch (Exception e) {
            logger.error(format("An error happened while trying to inform update handler about the error. Query: %s",
                                query));
        }
    }

    private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage<?, ?, ?>> filter, Throwable cause) {
        updateHandlers.keySet()
                      .stream()
                      .filter(filter)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(updateHandler -> emitError(query, cause, updateHandler)));
    }

    /**
     * Either runs the provided {@link Runnable} immediately or adds it to a {@link List} as a resource to the current
     * {@link UnitOfWork} if {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}. This is
     * done to ensure any emitter calls made from a message handling function are executed in the
     * {@link UnitOfWork.Phase#AFTER_COMMIT} phase.
     * <p>
     * The latter check requires the current UnitOfWork's phase to be {@link UnitOfWork.Phase#STARTED}. This is done to
     * allow users to circumvent their {@code queryUpdateTask} being handled in the AFTER_COMMIT phase. They can do this
     * by retrieving the current UnitOfWork and performing any of the {@link QueryUpdateEmitter} calls in a different
     * phase.
     *
     * @param queryUpdateTask a {@link Runnable} to be ran immediately or as a resource if
     *                        {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}
     */
    private void runOnAfterCommitOrNow(Runnable queryUpdateTask) {
        if (inStartedPhaseOfUnitOfWork()) {
            UnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
            unitOfWork.getOrComputeResource(
                    this.toString() + QUERY_UPDATE_TASKS_RESOURCE_KEY,
                    resourceKey -> {
                        List<Runnable> queryUpdateTasks = new ArrayList<>();
                        unitOfWork.afterCommit(uow -> queryUpdateTasks.forEach(Runnable::run));
                        return queryUpdateTasks;
                    }
            ).add(queryUpdateTask);
        } else {
            queryUpdateTask.run();
        }
    }

    /**
     * Return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     *
     * @return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link UnitOfWork.Phase#STARTED}, otherwise {@code false}.
     */
    private boolean inStartedPhaseOfUnitOfWork() {
        return CurrentUnitOfWork.isStarted() && UnitOfWork.Phase.STARTED.equals(CurrentUnitOfWork.get().phase());
    }

    @Override
    public Set<SubscriptionQueryMessage<?, ?, ?>> activeSubscriptions() {
        return Collections.unmodifiableSet(updateHandlers.keySet());
    }

    /**
     * Builder class to instantiate a {@link SimpleQueryUpdateEmitter}.
     * <p>
     * The {@link MessageMonitor} is defaulted to a {@link NoOpMessageMonitor} and the {@link QueryBusSpanFactory}
     * defaults to a {@link DefaultQueryBusSpanFactory} backed by a {@link NoOpSpanFactory}.
     */
    public static class Builder {

        private MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor =
                NoOpMessageMonitor.INSTANCE;
        private QueryUpdateEmitterSpanFactory spanFactory = DefaultQueryUpdateEmitterSpanFactory
                .builder()
                .spanFactory(NoOpSpanFactory.INSTANCE)
                .build();

        /**
         * Sets the {@link MessageMonitor} used to monitor {@link SubscriptionQueryUpdateMessage}s being processed.
         * Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param updateMessageMonitor the {@link MessageMonitor} used to monitor
         *                             {@link SubscriptionQueryUpdateMessage}s being processed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder updateMessageMonitor(
                @Nonnull MessageMonitor<? super SubscriptionQueryUpdateMessage<?>> updateMessageMonitor) {
            assertNonNull(updateMessageMonitor, "MessageMonitor may not be null");
            this.updateMessageMonitor = updateMessageMonitor;
            return this;
        }

        /**
         * Sets the {@link QueryUpdateEmitterSpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link DefaultQueryUpdateEmitterSpanFactory} backed by a {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link QueryUpdateEmitterSpanFactory} implementation.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull QueryUpdateEmitterSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Initializes a {@link SimpleQueryUpdateEmitter} as specified through this Builder.
         *
         * @return a {@link SimpleQueryUpdateEmitter} as specified through this Builder
         */
        public SimpleQueryUpdateEmitter build() {
            return new SimpleQueryUpdateEmitter(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            // Method kept for overriding
        }
    }
}
