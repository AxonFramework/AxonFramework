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

package org.axonframework.integrationtests.polymorphic;

import jakarta.persistence.EntityManager;
import org.axonframework.eventsourcing.LegacyEventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.LegacyEmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.LegacyInMemoryEventStorageEngine;
import org.axonframework.modelling.command.LegacyRepository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Nonnull;

/**
 * Tests for ES aggregate polymorphism.
 *
 * @author Milan Savic
 */
@Disabled("TODO #3061 - Revisit Aggregate Polymorphism")
public class PolymorphicESAggregateAnnotationCommandHandlerTest
        extends AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    private static final Map<Class<?>, LegacyRepository<?>> repositories = new HashMap<>();

    @Override
    public <T> LegacyRepository<T> repository(Class<T> aggregateType,
                                              Set<Class<? extends T>> subTypes,
                                              EntityManager entityManager) {
        LegacyEventSourcingRepository<T> repository = LegacyEventSourcingRepository
                .builder(aggregateType)
                .subtypes(subTypes)
                .eventStore(LegacyEmbeddedEventStore.builder()
                                                    .storageEngine(new LegacyInMemoryEventStorageEngine())
                                                    .build())
                .repositoryProvider(new RepositoryProvider() {
                    @Override
                    public <R> LegacyRepository<R> repositoryFor(@Nonnull Class<R> aggregateType) {
                        //noinspection unchecked
                        return (LegacyRepository<R>) repositories.get(aggregateType);
                    }
                })
                .build();
        repositories.put(aggregateType, repository);
        return repository;
    }
}
