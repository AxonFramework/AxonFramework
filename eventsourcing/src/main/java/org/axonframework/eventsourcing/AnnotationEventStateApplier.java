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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import javax.annotation.Nonnull;
import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of {@link EventStateApplier} that applies state changes through {@code @EventSourcingHandler} and
 * {@code @EventHandler} annotated methods.
 *
 * @param <M> The type of model to apply state changes to
 * @author Your Name
 * @since 5.0.0
 */
public class AnnotationEventStateApplier<M> implements EventStateApplier<M> {

    private final AnnotatedHandlerInspector<Object> inspector;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Initialize a new {@link AnnotationEventStateApplier} for the given model type.
     *
     * @param modelType The type of model this instance will handle state changes for
     */
    public AnnotationEventStateApplier(Class<M> modelType) {
        this(modelType,
             ClasspathParameterResolverFactory.forClass(modelType),
             new ClassBasedMessageTypeResolver());
    }

    /**
     * Initialize a new {@link AnnotationEventStateApplier} for the given model type using the provided
     * {@code messageTypeResolver}.
     *
     * @param modelType           The type of model this instance will handle state changes for
     * @param messageTypeResolver The resolver to use for message types
     */
    public AnnotationEventStateApplier(Class<M> modelType, MessageTypeResolver messageTypeResolver) {
        this(modelType,
             ClasspathParameterResolverFactory.forClass(modelType),
             messageTypeResolver);
    }

    /**
     * Initialize a new {@link AnnotationEventStateApplier} with the given {@code modelType},
     * {@code parameterResolverFactory}, and {@code messageTypeResolver}.
     *
     * @param modelType                The type of model this instance will handle state changes for
     * @param parameterResolverFactory The factory for resolving parameters
     * @param messageTypeResolver      The resolver to use for message types
     */
    public AnnotationEventStateApplier(Class<M> modelType,
                                       ParameterResolverFactory parameterResolverFactory,
                                       MessageTypeResolver messageTypeResolver) {
        this(modelType,
             parameterResolverFactory,
             ClasspathHandlerDefinition.forClass(modelType),
             messageTypeResolver);
    }

    /**
     * Initialize a fully configured {@link AnnotationEventStateApplier}.
     *
     * @param modelType                The type of model this instance will handle state changes for
     * @param parameterResolverFactory The factory for resolving parameters
     * @param handlerDefinition        The definition of handlers to use
     * @param messageTypeResolver      The resolver to use for message types
     */
    public AnnotationEventStateApplier(Class<M> modelType,
                                       ParameterResolverFactory parameterResolverFactory,
                                       HandlerDefinition handlerDefinition,
                                       MessageTypeResolver messageTypeResolver) {
        assertNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
        assertNonNull(modelType, "Model Type may not be null");
        this.inspector = AnnotatedHandlerInspector.inspectType(modelType,
                                                               parameterResolverFactory,
                                                               handlerDefinition);
        this.messageTypeResolver = messageTypeResolver;
    }

    @Override
    public M apply(M model, @Nonnull EventMessage<?> event) {
        assertNonNull(model, "Model may not be null");
        assertNonNull(event, "Event Message may not be null");

        Optional<MessageHandlingMember<? super Object>> handler =
                inspector.getHandlers(model.getClass())
                         .filter(h -> h.canHandle(event, null))
                         .findFirst();

        handler.ifPresent(messageHandlingMember -> messageHandlingMember.handle(event, null, model));
        return model;
    }
}