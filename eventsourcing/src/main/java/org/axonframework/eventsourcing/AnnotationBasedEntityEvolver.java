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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import static java.util.Objects.requireNonNull;


/**
 * Implementation of the {@link EntityEvolver} that applies state changes through {@link EventSourcingHandler} annotated
 * methods using an {@link AnnotatedHandlerInspector}.
 *
 * @param <E> The entity type to evolve.
 * @author Mateusz Nowak
 * @see EventSourcingHandler
 * @see AnnotatedHandlerInspector
 * @since 5.0.0
 */
public class AnnotationBasedEntityEvolver<E> implements EntityEvolver<E> {

    private final AnnotatedHandlerInspector<E> inspector;

    /**
     * Initialize a new annotation-based {@link EntityEvolver}.
     *
     * @param entityType The type of entity this instance will handle state changes for.
     */
    public AnnotationBasedEntityEvolver(@Nonnull Class<E> entityType) {
        this(entityType,
             AnnotatedHandlerInspector.inspectType(entityType,
                                                   ClasspathParameterResolverFactory.forClass(entityType),
                                                   ClasspathHandlerDefinition.forClass(entityType)));
    }

    /**
     * Initialize a new annotation-based {@link EntityEvolver}.
     *
     * @param entityType The type of entity this instance will handle state changes for.
     * @param inspector  The inspector to use to find the annotated handlers on the entity.
     */
    public AnnotationBasedEntityEvolver(@Nonnull Class<E> entityType,
                                        @Nonnull AnnotatedHandlerInspector<E> inspector
    ) {
        requireNonNull(entityType, "The entity type must not be null.");
        this.inspector = requireNonNull(inspector, "The Annotated Handler Inspector must not be null.");
    }

    @Override
    public E evolve(@Nonnull E entity,
                    @Nonnull EventMessage<?> event,
                    @Nonnull ProcessingContext context) {
        requireNonNull(entity, "The entity must not be null.");
        requireNonNull(event, "The event message must not be null.");

        try {
            var eventHandler = new AnnotatedEventHandlingComponent<>(entity, inspector);
            var eventHandlerResult = eventHandler.handle(event, context)
                                                 .asCompletableFuture()
                                                 .join();
            return entityFromStreamResultOrUpdatedExisting(eventHandlerResult, entity);
        } catch (Exception e) {
            throw new StateEvolvingException(
                    "Failed to apply event [" + event.type() + "] in order to evolve [" + entity.getClass() + "] state",
                    e
            );
        }
    }

    private E entityFromStreamResultOrUpdatedExisting(MessageStream.Entry<?> potentialEntityFromStream, E existing) {
        if (potentialEntityFromStream != null) {
            var resultPayload = potentialEntityFromStream.message().getPayload();
            if (resultPayload != null && existing.getClass().isAssignableFrom(resultPayload.getClass())) {
                //noinspection unchecked
                return (E) existing.getClass().cast(resultPayload);
            }
        }
        return existing;
    }
}