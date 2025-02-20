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
 * Implementation of {@link EventStateApplier} that applies state changes through {@link EventSourcingHandler} annotated
 * methods using an {@link AnnotationEventHandlerAdapter}.
 *
 * @param <M> The type of model to apply state changes to
 * @author Mateusz Nowak
 * @see EventSourcingHandler
 * @see AnnotationEventHandlerAdapter
 * @since 5.0.0
 */
public class AnnotationBasedEventStateApplier<M> implements EventStateApplier<M> {

    private final ParameterResolverFactory parameterResolverFactory;
    private final HandlerDefinition handlerDefinition;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier}.
     *
     * @param modelType The type of model this instance will handle state changes for.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType) {
        this(modelType, ClasspathParameterResolverFactory.forClass(modelType),
             new ClassBasedMessageTypeResolver());
    }

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier} for the given model type using the provided
     * {@code messageTypeResolver}.
     *
     * @param modelType           The type of model this instance will handle state changes for.
     * @param messageTypeResolver The {@link MessageTypeResolver} resolving the
     *                            {@link org.axonframework.messaging.MessageType types} for
     *                            {@link org.axonframework.eventhandling.EventMessage EventMessages}.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType,
                                            @Nonnull MessageTypeResolver messageTypeResolver) {
        this(modelType, ClasspathParameterResolverFactory.forClass(modelType), messageTypeResolver);
    }

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier}.
     *
     * @param modelType           The type of model this instance will handle state changes for.
     * @param parameterResolverFactory The strategy for resolving handler method parameter values.
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the
     *                                 {@link org.axonframework.messaging.MessageType types} for
     *                                 {@link org.axonframework.eventhandling.EventMessage EventMessages}.
     */
    private AnnotationBasedEventStateApplier(
            @Nonnull Class<M> modelType,
            @Nonnull ParameterResolverFactory parameterResolverFactory,
            @Nonnull MessageTypeResolver messageTypeResolver) {
        this(parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(modelType),
             messageTypeResolver);
    }

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier}.
     *
     * @param parameterResolverFactory the strategy for resolving handler method parameter values
     * @param handlerDefinition        the handler definition used to create concrete handlers
     * @param messageTypeResolver      The {@link MessageTypeResolver} resolving the
     *                                 {@link org.axonframework.messaging.MessageType types} for
     *                                 {@link org.axonframework.eventhandling.EventMessage EventMessages}
     */
    public AnnotationBasedEventStateApplier(@Nonnull ParameterResolverFactory parameterResolverFactory,
                                            @Nonnull HandlerDefinition handlerDefinition,
                                            @Nonnull MessageTypeResolver messageTypeResolver) {
        requireNonNull(handlerDefinition, "HandlerDefinition may not be null");
        this.parameterResolverFactory = requireNonNull(parameterResolverFactory,
                                                       "ParameterResolverFactory may not be null");
        this.handlerDefinition = handlerDefinition;
        this.messageTypeResolver = requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
    }

    @Override
    public M apply(@Nonnull M model, @Nonnull EventMessage<?> event) {
        requireNonNull(model, "Model may not be null");
        requireNonNull(event, "Event Message may not be null");

        // todo: should we create a new instance of AnnotationEventHandlerAdapter for each event?
        // any idea how to cache that?
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
            throw new StateEvolvingException(
                    "Failed to apply event [" + event.type() + "] in order to evolve [" + model.getClass() + "] state",
                    e);
        }
        return model;
    }
}