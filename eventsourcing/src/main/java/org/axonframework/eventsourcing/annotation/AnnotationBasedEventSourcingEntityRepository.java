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

package org.axonframework.eventsourcing.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.ConstructorUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventsourcing.AnnotationBasedEventStateApplier;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.axonframework.modelling.repository.ManagedEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.common.ConstructorUtils.factoryForTypeWithOptionalArgumentInstance;

/**
 * Implementation of a {@link AsyncRepository} that configures an entity based on the configuration provided by the
 * {@link EventSourcedEntity} annotation. Please refer to the {@link EventSourcedEntity} annotation for more information
 * on the configuration options.
 *
 * @param <ID> The type of the identifier of the entity.
 * @param <T>  The type of the entity.
 */
public class AnnotationBasedEventSourcingEntityRepository<ID, T> implements AsyncRepository.LifecycleManagement<ID, T> {

    private final Class<ID> idType;
    private final Class<T> entityType;
    private final AsyncEventSourcingRepository<ID, T> repository;
    private final EventSourcedEntityFactory<ID, T> creator;
    private final CriteriaResolver<ID> criteriaResolver;
    private final AnnotationBasedEventStateApplier<T> stateApplier;

    /**
     * Initialize a repository for the given {@code entityType} and {@code idType} using the provided
     * {@code eventStore}.
     *
     * @param eventStore The event store to store and load events from.
     * @param idType     The type of the identifier of the entity.
     * @param entityType The type of the entity.
     */
    @SuppressWarnings("unchecked")
    public AnnotationBasedEventSourcingEntityRepository(
            AsyncEventStore eventStore,
            Class<ID> idType,
            Class<T> entityType
    ) {
        Map<String, Object> annotationAttributes = AnnotationUtils
                .findAnnotationAttributes(entityType, EventSourcedEntity.class)
                .orElseThrow(() -> new IllegalArgumentException("The given class is not an @EventSourcingEntity"));

        this.idType = idType;
        this.entityType = entityType;

        var creatorType = (Class<EventSourcedEntityFactory<ID, T>>) annotationAttributes.get("entityCreator");
        var criteriaResolverType = (Class<CriteriaResolver<ID>>) annotationAttributes.get("criteriaResolver");
        this.creator = ConstructorUtils.factoryForTypeWithOptionalArgumentInstance(creatorType, entityType).get();
        this.criteriaResolver = factoryForTypeWithOptionalArgumentInstance(criteriaResolverType, entityType).get();

        this.stateApplier = new AnnotationBasedEventStateApplier<>(entityType);
        this.repository = new AsyncEventSourcingRepository<>(
                idType,
                entityType,
                eventStore,
                criteriaResolver,
                stateApplier,
                id -> creator.createEntity(entityType, id)
        );
    }


    @Nonnull
    @Override
    public Class<T> entityType() {
        return entityType;
    }

    @Nonnull
    @Override
    public Class<ID> idType() {
        return idType;
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> load(@Nonnull ID identifier,
                                                        @Nonnull ProcessingContext processingContext) {
        return repository.load(identifier, processingContext);
    }

    @Override
    public CompletableFuture<ManagedEntity<ID, T>> loadOrCreate(@Nonnull ID identifier,
                                                                @Nonnull ProcessingContext processingContext) {
        return repository.loadOrCreate(identifier, processingContext);
    }

    @Override
    public ManagedEntity<ID, T> persist(@Nonnull ID identifier, @Nonnull T entity,
                                        @Nonnull ProcessingContext processingContext) {
        return repository.persist(identifier, entity, processingContext);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityType", entityType);
        descriptor.describeProperty("idType", idType);
        descriptor.describeProperty("creator", creator);
        descriptor.describeProperty("criteriaResolver", criteriaResolver);
        descriptor.describeProperty("stateApplier", stateApplier);
        descriptor.describeWrapperOf(repository);
    }

    @Override
    public ManagedEntity<ID, T> attach(@Nonnull ManagedEntity<ID, T> entity,
                                       @Nonnull ProcessingContext processingContext) {
        return repository.attach(entity, processingContext);
    }
}
