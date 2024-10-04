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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interface describing the option to publish {@link EventMessage events}.
 * <p>
 * When a {@link ProcessingContext} is provided, the publication is typically staged in the
 * {@link ProcessingContext#onPostInvocation(Function) post invocation} phase of the {@code ProcessingContext}. As a
 * consequence, the result of publication will be made apparent in the {@code ProcessingContext}. When using
 * {@link #publish(String, EventMessage[])} instead, the result of publication is carried in the resulting
 * {@link CompletableFuture}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventSink {

    /**
     * Publishes the given {@code events} as part of a phase on the given {@code processingContext} in this event sink.
     * <p>
     * Typically, the {@link ProcessingContext#onPostInvocation(Function) post invocation} phase is used for this
     * purpose. As a consequence, the result of publication will be made apparent in the {@code ProcessingContext}.
     *
     * @param processingContext The {@link ProcessingContext} to attach the publication step into.
     * @param context           The (bounded) context within which to publish the given {@code events}.
     * @param events            The {@link EventMessage events} to publish in this sink.
     */
    default void publish(@Nonnull ProcessingContext processingContext,
                         @Nonnull String context,
                         EventMessage<?>... events) {
        publish(processingContext, context, Arrays.asList(events));
    }

    /**
     * Publishes the given {@code events} as part of a phase on the given {@code processingContext} in this event sink.
     * <p>
     * Typically, the {@link ProcessingContext#onPostInvocation(Function) post invocation} phase is used for this
     * purpose. As a consequence, the result of publication will be made apparent in the {@code ProcessingContext}.
     *
     * @param processingContext The {@link ProcessingContext} to attach the publication step into.
     * @param context           The (bounded) context within which to publish the given {@code events}.
     * @param events            The {@link EventMessage events} to publish in this sink.
     */
    default void publish(@Nonnull ProcessingContext processingContext,
                         @Nonnull String context,
                         @Nonnull List<EventMessage<?>> events) {
        processingContext.onPostInvocation(c -> publish(context, events));
    }

    /**
     * Publishes the given {@code events} in this event sink.
     *
     * @param context The (bounded) context within which to publish the given {@code events}.
     * @param events  The {@link EventMessage events} to publish in this sink.
     * @return A {@link CompletableFuture} of {@link Void}. Publication succeeded when the returned
     * {@code CompletableFuture} completes successfully.
     */
    default CompletableFuture<Void> publish(@Nonnull String context,
                                            EventMessage<?>... events) {
        return publish(context, Arrays.asList(events));
    }

    /**
     * Publishes the given {@code events} in this event sink.
     *
     * @param context The (bounded) context within which to publish the given {@code events}.
     * @param events  The {@link EventMessage events} to publish in this sink.
     * @return A {@link CompletableFuture} of {@link Void}. Publication succeeded when the returned
     * {@code CompletableFuture} completes successfully.
     */
    CompletableFuture<Void> publish(@Nonnull String context,
                                    @Nonnull List<EventMessage<?>> events);
}
