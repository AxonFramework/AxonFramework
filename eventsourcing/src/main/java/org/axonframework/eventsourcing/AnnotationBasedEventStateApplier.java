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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;


/**
 * Implementation of {@link EventStateApplier} that applies state changes through {@code @EventSourcingHandler}
 * annotated methods using an {@link AnnotationEventHandlerAdapter}.
 *
 * @param <M> The type of model to apply state changes to
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AnnotationBasedEventStateApplier<M> implements EventStateApplier<M> {

    private final ParameterResolverFactory parameterResolverFactory;
    private final HandlerDefinition handlerDefinition;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier} for the given model type.
     *
     * @param modelType The type of model this instance will handle state changes for.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType) {
        this(ClasspathParameterResolverFactory.forClass(modelType),
             new ClassBasedMessageTypeResolver());
    }

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier} for the given model type using the provided
     * {@code messageTypeResolver}.
     *
     * @param modelType           The type of model this instance will handle state changes for.
     * @param messageTypeResolver The resolver to use for message types.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType,
                                            @Nonnull MessageTypeResolver messageTypeResolver) {
        this(ClasspathParameterResolverFactory.forClass(modelType),
             messageTypeResolver);
    }

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier} with the given {@code parameterResolverFactory}, and
     * {@code messageTypeResolver}.
     *
     * @param parameterResolverFactory The factory for resolving parameters.
     * @param messageTypeResolver      The resolver to use for message types.
     */
    private AnnotationBasedEventStateApplier(@Nonnull ParameterResolverFactory parameterResolverFactory,
                                             @Nonnull MessageTypeResolver messageTypeResolver) {
        this(parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(Object.class),
             messageTypeResolver);
    }

    /**
     * Initialize a fully configured {@link AnnotationBasedEventStateApplier}.
     *
     * @param parameterResolverFactory The factory for resolving parameters
     * @param handlerDefinition        The definition of handlers to use
     * @param messageTypeResolver      The resolver to use for message types
     */
    public AnnotationBasedEventStateApplier(@Nonnull ParameterResolverFactory parameterResolverFactory,
                                            @Nonnull HandlerDefinition handlerDefinition,
                                            @Nonnull MessageTypeResolver messageTypeResolver) {
        requireNonNull(parameterResolverFactory, "ParameterResolverFactory may not be null");
        requireNonNull(handlerDefinition, "HandlerDefinition may not be null");
        requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
        this.parameterResolverFactory = parameterResolverFactory;
        this.handlerDefinition = handlerDefinition;
        this.messageTypeResolver = messageTypeResolver;
    }

    @Override
    public M apply(M model, @Nonnull EventMessage<?> event) {
        requireNonNull(model, "Model may not be null");
        requireNonNull(event, "Event Message may not be null");

        var handlerAdapter = new AnnotationEventHandlerAdapter(
                model,
                parameterResolverFactory,
                handlerDefinition,
                messageTypeResolver
        );
        try {
            if (handlerAdapter.canHandle(event)) {
                handlerAdapter.handleSync(event);
            }
        } catch (Exception e) {
            // todo: I'm not sure about that, should we add Exception to the method signature and do not handle here?
            throw new StateEvolvingException("Failed to apply event [" + event.type() + "]", e);
        }
        return model;
    }
}