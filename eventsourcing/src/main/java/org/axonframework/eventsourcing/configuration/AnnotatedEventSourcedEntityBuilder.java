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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ConstructorUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.eventsourcing.AnnotationBasedEntityEvolver;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.annotation.CriteriaResolverDefinition;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.repository.AsyncRepository;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.axonframework.common.ConstructorUtils.getConstructorFunctionWithZeroArguments;

/**
 * Annotation-based implementation of the {@link EventSourcedEntityBuilder}.
 * <p>
 * Expects the {@link EventSourcedEntity} annotation on the given {@code entityType}, throwing an
 * {@link IllegalArgumentException} when not present.
 *
 * @param <I> The type of identifier used to identify the event sourced entity that's being built.
 * @param <E> The type of the event sourced entity being built.
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
class AnnotatedEventSourcedEntityBuilder<I, E> implements EventSourcedEntityBuilder<I, E> {

    private final Class<I> idType;
    private final Class<E> entityType;
    private final EventSourcedEntityFactoryDefinition<E, I> entityFactoryDefinition;
    private final CriteriaResolverDefinition criteriaResolverDefinition;
    private final AnnotationBasedEntityEvolver<E> entityEvolver;

    AnnotatedEventSourcedEntityBuilder(@Nonnull Class<I> idType,
                                       @Nonnull Class<E> entityType) {
        this.idType = requireNonNull(idType, "The id type cannot be null.");
        this.entityType = requireNonNull(entityType, "The entity type cannot be null.");
        Map<String, Object> annotationAttributes = AnnotationUtils
                .findAnnotationAttributes(entityType, EventSourcedEntity.class)
                .orElseThrow(() -> new IllegalArgumentException("The given class is not an @EventSourcingEntity."));
        //noinspection unchecked
        Class<EventSourcedEntityFactoryDefinition<E, I>> entityFactoryType =
                (Class<EventSourcedEntityFactoryDefinition<E, I>>) annotationAttributes.get("entityFactoryDefinition");
        this.entityFactoryDefinition = getConstructorFunctionWithZeroArguments(entityFactoryType).get();
        //noinspection unchecked
        var criteriaResolverType = (Class<CriteriaResolverDefinition>) annotationAttributes.get("criteriaResolverDefinition");
        this.criteriaResolverDefinition = ConstructorUtils.getConstructorFunctionWithZeroArguments(criteriaResolverType).get();
        this.entityEvolver = new AnnotationBasedEntityEvolver<>(entityType);
    }

    @Override
    public String entityName() {
        return entityType.getSimpleName() + "#" + idType.getSimpleName();
    }

    @Override
    public ComponentFactory<AsyncRepository<I, E>> repository() {
        return c -> {
            CriteriaResolver<I> criteriaResolver = criteriaResolverDefinition
                    .createEventCriteriaResolver(entityType, idType, c);
            EventSourcedEntityFactory<I, E> entityFactory = entityFactoryDefinition
                    .createFactory(entityType, idType, c);
            return new AsyncEventSourcingRepository<>(
                    idType,
                    entityType,
                    c.getComponent(EventStore.class),
                    entityFactory,
                    criteriaResolver,
                    entityEvolver
            );
        };
    }
}
