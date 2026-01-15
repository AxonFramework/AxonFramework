/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface towards the Event Handling components of an application.
 * <p>
 * This interface provides a friendlier API toward the {@link EventSink} and allows for
 * components to easily publish events.
 *
 * @author Bert Laverman
 * @see DefaultEventGateway
 * @since 4.1.0
 */
public interface EventGateway {

    /**
     * Publishes the given {@code events} within the given {@code context}. When present, the {@code events} should be
     * published as part of the {@code context's} lifecycle.
     * <p>
     * The {@code events} are mapped to {@link EventMessage EventMessages} before they
     * are given to an {@link EventSink}.
     *
     * @param context The processing context, if any, to publish the given {@code events} in.
     * @param events  The collection of events to publish.
     * @return A {@link CompletableFuture} of {@link Void}. Completion of the future depends on the
     * {@link EventSink} used by this gateway.
     */
    default CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                            Object... events) {
        return publish(context, Arrays.asList(events));
    }

    /**
     * Publishes the given {@code events} within the given {@code context}. When present, the {@code events} should be
     * published as part of the {@code context's} lifecycle.
     * <p>
     * The {@code events} are mapped to {@link EventMessage EventMessages} before they
     * are given to an {@link EventSink}.
     *
     * @param context The processing context, if any, to publish the given {@code events} in.
     * @param events  The collection of events to publish.
     * @return A {@link CompletableFuture} of {@link Void}. Completion of the future depends on the
     * {@link EventSink} used by this gateway.
     */
    CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                    @Nonnull List<?> events);

    /**
     * Publishes the given {@code events} within the given {@code context}. When present, the {@code events} should be
     * published as part of the {@code context's} lifecycle.
     * <p>
     * The {@code events} are mapped to {@link EventMessage EventMessages} before they
     * are given to an {@link EventSink}.
     *
     * @param events  The collection of events to publish.
     * @return A {@link CompletableFuture} of {@link Void}. Completion of the future depends on the
     * {@link EventSink} used by this gateway.
     * @see #publish(ProcessingContext, List)
     */
    default CompletableFuture<Void> publish(@Nonnull List<?> events) {
        return publish(null, events);
    }
}
