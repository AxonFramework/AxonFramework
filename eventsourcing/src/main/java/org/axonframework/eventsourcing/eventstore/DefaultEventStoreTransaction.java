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

package org.axonframework.eventsourcing.eventstore;

import jakarta.validation.constraints.NotNull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The default {@link EventStoreTransaction}.
 * <p>
 * While {@link #source(SourcingCondition, ProcessingContext) sourcing} it will map the {@link SourcingCondition} into
 * an {@link AppendCondition} for {@link #appendEvent(EventMessage) appending}, taking into account several sourcing
 * invocation might have occurred in the same {@link ProcessingContext}. During
 * {@link #appendEvent(EventMessage) appending} it will pass along a collection of {@link EventMessage events} to an
 * {@link AsyncEventStorageEngine} is part of the prepare commit phase of the {@link ProcessingContext}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class DefaultEventStoreTransaction implements EventStoreTransaction {

    private static final ProcessingContext.ResourceKey<AppendCondition> appendConditionKey =
            ProcessingContext.ResourceKey.create("appendCondition");
    private static final ProcessingContext.ResourceKey<List<EventMessage<?>>> eventQueueKey =
            ProcessingContext.ResourceKey.create("eventQueue");

    private final AsyncEventStorageEngine eventStorageEngine;
    private final ProcessingContext processingContext;
    private final List<Consumer<EventMessage<?>>> callbacks = new CopyOnWriteArrayList<>();

    /**
     * Constructs a {@link DefaultEventStoreTransaction} using the given {@code eventStorageEngine} to
     * {@link #appendEvent(EventMessage) append events} originating from the given {@code context}.
     *
     * @param eventStorageEngine The {@link AsyncEventStorageEngine} used to
     *                           {@link #appendEvent(EventMessage) append events} with.
     * @param processingContext  The {@link ProcessingContext} from which to
     *                           {@link #appendEvent(EventMessage) append events} and attach resources like the
     *                           {@link EventStoreTransaction#APPEND_POSITION_KEY} to.
     */
    public DefaultEventStoreTransaction(@NotNull AsyncEventStorageEngine eventStorageEngine,
                                        @NotNull ProcessingContext processingContext) {
        this.eventStorageEngine = eventStorageEngine;
        this.processingContext = processingContext;
    }

    @Override
    public MessageStream<EventMessage<?>> source(@NotNull SourcingCondition condition,
                                                 @NotNull ProcessingContext context) {
        context.updateResource(
                appendConditionKey,
                appendCondition -> appendCondition == null
                        ? AppendCondition.from(condition)
                        : appendCondition.with(condition)
        );
        return eventStorageEngine.source(condition);
    }

    @Override
    public void appendEvent(@NotNull EventMessage<?> eventMessage) {
        List<EventMessage<?>> eventQueue = processingContext.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    attachAppendEventsStep();
                    return new CopyOnWriteArrayList<>();
                }
        );
        eventQueue.add(eventMessage);

        callbacks.forEach(callback -> callback.accept(eventMessage));
    }

    private void attachAppendEventsStep() {
        processingContext.onPrepareCommit(
                commitContext -> {
                    AppendCondition appendCondition =
                            commitContext.computeResourceIfAbsent(appendConditionKey, AppendCondition::none);
                    List<EventMessage<?>> eventQueue = commitContext.getResource(eventQueueKey);
                    return eventStorageEngine.appendEvents(appendCondition, eventQueue)
                                             .whenComplete((position, exception) -> commitContext.putResource(
                                                     APPEND_POSITION_KEY, position
                                             ));
                }
        );
    }

    @Override
    public void onAppend(@NotNull Consumer<EventMessage<?>> callback) {
        callbacks.add(callback);
    }
}
