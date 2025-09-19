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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.OptionalResponseType;
import org.axonframework.messaging.responsetypes.PublisherResponseType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
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
import java.util.function.Predicate;

/**
 * Implementation of {@link QueryUpdateEmitter} that uses Project Reactor to implement Update Handlers.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @author Steven van Beelen
 * @since 4.0.0
 */
public class SimpleQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueryUpdateEmitter.class);

    private static final String QUERY_UPDATE_TASKS_RESOURCE_KEY = "/update-tasks";

    private final ConcurrentMap<SubscriptionQueryMessage, SinkWrapper<?>> updateHandlers = new ConcurrentHashMap<>();

    @Nonnull
    @Override
    public UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query,
                                   int updateBufferSize) {
        if (hasHandlerFor(query.identifier())) {
            throw new SubscriptionQueryAlreadyRegisteredException(query.identifier());
        }

        Sinks.Many<SubscriptionQueryUpdateMessage> sink = Sinks.many()
                                                               .replay()
                                                               .limit(updateBufferSize);
        SinksManyWrapper<SubscriptionQueryUpdateMessage> sinksManyWrapper = new SinksManyWrapper<>(sink);

        Runnable removeHandler = () -> updateHandlers.remove(query);

        updateHandlers.put(query, sinksManyWrapper);
        Flux<SubscriptionQueryUpdateMessage> updateMessageFlux = sink.asFlux()
                                                                     .doOnCancel(removeHandler)
                                                                     .doOnTerminate(removeHandler);
        return new UpdateHandler(updateMessageFlux, removeHandler, sinksManyWrapper::complete);
    }

    private boolean hasHandlerFor(String queryId) {
        return updateHandlers.keySet().stream().anyMatch(m -> m.identifier().equals(queryId));
    }

    @Override
    public void emit(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                     @Nonnull SubscriptionQueryUpdateMessage update) {
        runOnAfterCommitOrNow(() -> doEmit(filter, update));
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage> filter) {
        runOnAfterCommitOrNow(() -> doComplete(filter));
    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                      @Nonnull Throwable cause) {
        runOnAfterCommitOrNow(() -> doCompleteExceptionally(filter, cause));
    }

    private void doEmit(Predicate<SubscriptionQueryMessage> filter,
                        SubscriptionQueryUpdateMessage update) {
        updateHandlers.keySet()
                      .stream()
                      .filter(payloadMatchesQueryResponseType(update.payloadType()))
                      .filter(filter::test)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(uh -> doEmit(query, uh, update)));
    }

    private Predicate<SubscriptionQueryMessage> payloadMatchesQueryResponseType(Class<?> payloadType) {
        return sqm -> {
            if (sqm.updatesResponseType() instanceof MultipleInstancesResponseType) {
                return payloadType.isArray() || Iterable.class.isAssignableFrom(payloadType);
            }
            if (sqm.updatesResponseType() instanceof OptionalResponseType) {
                return Optional.class.isAssignableFrom(payloadType);
            }
            if (sqm.updatesResponseType() instanceof PublisherResponseType) {
                return Publisher.class.isAssignableFrom(payloadType);
            }
            return sqm.updatesResponseType().getExpectedResponseType().isAssignableFrom(payloadType);
        };
    }

    @SuppressWarnings("unchecked")
    private void doEmit(SubscriptionQueryMessage query, SinkWrapper<?> updateHandler,
                        SubscriptionQueryUpdateMessage update) {
        try {
            ((SinkWrapper<SubscriptionQueryUpdateMessage>) updateHandler).next(update);
        } catch (Exception e) {
            logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                "The subscription will be cancelled. Exception summary: {}",
                        query.type(), e.toString());
            updateHandlers.remove(query);
            emitError(query, e, updateHandler);
        }
    }

    private void doComplete(Predicate<SubscriptionQueryMessage> filter) {
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

    private void emitError(SubscriptionQueryMessage query, Throwable cause,
                           SinkWrapper<?> updateHandler) {
        try {
            updateHandler.error(cause);
        } catch (Exception e) {
            logger.error("An error happened while trying to inform update handler about the error. Query: {}",
                         query);
        }
    }

    private void doCompleteExceptionally(Predicate<SubscriptionQueryMessage> filter, Throwable cause) {
        updateHandlers.keySet()
                      .stream()
                      .filter(filter)
                      .forEach(query -> Optional.ofNullable(updateHandlers.get(query))
                                                .ifPresent(updateHandler -> emitError(query, cause, updateHandler)));
    }

    /**
     * Either runs the provided {@link Runnable} immediately or adds it to a {@link List} as a resource to the current
     * {@link LegacyUnitOfWork} if {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}.
     * This is done to ensure any emitter calls made from a message handling function are executed in the
     * {@link LegacyUnitOfWork.Phase#AFTER_COMMIT} phase.
     * <p>
     * The latter check requires the current UnitOfWork's phase to be {@link LegacyUnitOfWork.Phase#STARTED}. This is
     * done to allow users to circumvent their {@code queryUpdateTask} being handled in the AFTER_COMMIT phase. They can
     * do this by retrieving the current UnitOfWork and performing any of the {@link QueryUpdateEmitter} calls in a
     * different phase.
     *
     * @param queryUpdateTask a {@link Runnable} to be ran immediately or as a resource if
     *                        {@link SimpleQueryUpdateEmitter#inStartedPhaseOfUnitOfWork} returns {@code true}
     */
    private void runOnAfterCommitOrNow(Runnable queryUpdateTask) {
        if (inStartedPhaseOfUnitOfWork()) {
            LegacyUnitOfWork<?> unitOfWork = CurrentUnitOfWork.get();
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
     * {@link LegacyUnitOfWork.Phase#STARTED}, otherwise {@code false}.
     *
     * @return {@code true} if the {@link CurrentUnitOfWork#isStarted()} returns {@code true} and in if the phase is
     * {@link LegacyUnitOfWork.Phase#STARTED}, otherwise {@code false}.
     */
    private boolean inStartedPhaseOfUnitOfWork() {
        return CurrentUnitOfWork.isStarted() && LegacyUnitOfWork.Phase.STARTED.equals(CurrentUnitOfWork.get().phase());
    }

    @Override
    public Set<SubscriptionQueryMessage> activeSubscriptions() {
        return Collections.unmodifiableSet(updateHandlers.keySet());
    }
}
