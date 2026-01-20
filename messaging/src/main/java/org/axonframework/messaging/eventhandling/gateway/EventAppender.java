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
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.annotation.EventAppenderParameterResolverFactory;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.annotation.MessageHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Component that appends events to an {@link EventSink} in the context of a {@link ProcessingContext}. This makes the
 * {@code EventAppender} the <b>preferred</b> way to append events from within another message handling method.
 * <p>
 * The events will be appended in the context this appender was created for. You can construct one through the
 * {@link #forContext(ProcessingContext)}.
 * <p>
 * When using annotation-based {@link MessageHandler @MessageHandler-methods} and
 * you have declared an argument of type {@link EventAppender}, the appender will automatically be injected by the
 * {@link EventAppenderParameterResolverFactory}.
 * <p>
 * As this component is {@link ProcessingContext}-scoped, it is not retrievable from the {@link Configuration}.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface EventAppender extends DescribableComponent {

    /**
     * The {@link Context.ResourceKey} used to store the {@link EventAppender} in the {@link ProcessingContext}.
     */
    Context.ResourceKey<ProcessingContextEventAppender> RESOURCE_KEY = Context.ResourceKey.withLabel("EventAppender");

    /**
     * Creates an appender for the given {@link ProcessingContext}.
     * <p>
     * You can use this appender only for the context it was created for. There is no harm in using this method more
     * than once, as the same appender will be returned.
     *
     * @param context The {@link ProcessingContext} to create the appender for.
     * @return The created appender.
     */
    static EventAppender forContext(@Nonnull ProcessingContext context) {
        return forContext(context, context.component(EventSink.class), context.component(MessageTypeResolver.class));
    }

    /**
     * Creates an appender for the given {@link ProcessingContext} and {@link EventSink}. You can use this appender only
     * for the context it was created for. There is no harm in using this method more than once, as the same appender
     * will be returned.
     *
     * @param context             The {@link ProcessingContext} to create the appender for.
     * @param eventSink           The {@link EventSink} to use for the appender.
     * @param messageTypeResolver The {@link MessageTypeResolver} to use for the appender.
     * @return The created appender.
     */
    static EventAppender forContext(
            @Nonnull ProcessingContext context,
            @Nonnull EventSink eventSink,
            @Nonnull MessageTypeResolver messageTypeResolver
    ) {
        Objects.requireNonNull(context, "ProcessingContext may not be null");
        return context.computeResourceIfAbsent(
                RESOURCE_KEY,
                () -> new ProcessingContextEventAppender(context, eventSink, messageTypeResolver)
        );
    }

    /**
     * Append a collection of events to the event store in the current {@link ProcessingContext}. The events will be
     * published when the context commits.
     *
     * @param events The collection of events to publish.
     */
    default void append(Object... events) {
        append(Arrays.asList(events));
    }

    /**
     * Append a collection of events to the event store in the current {@link ProcessingContext}. The events will be
     * published when the context commits.
     *
     * @param events The collection of events to publish.
     */
    void append(@Nonnull List<?> events);
}
