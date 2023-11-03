/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Tests for ES aggregate polymorphism.
 *
 * @author Milan Savic
 */
public class PolymorphicESAggregateAnnotationCommandHandlerTest
        extends AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    private static final Map<Class<?>, Repository<?>> repositories = new HashMap<>();

    @Override
    public <T> Repository<T> repository(Class<T> aggregateType,
                                        Set<Class<? extends T>> subTypes,
                                        EntityManager entityManager) {
        EventSourcingRepository<T> repository = EventSourcingRepository
                .builder(aggregateType)
                .subtypes(subTypes)
                .eventStore(EmbeddedEventStore.builder()
                                              .storageEngine(new InMemoryEventStorageEngine())
                                              .build())
                .repositoryProvider(new RepositoryProvider() {
                    @Override
                    public <R> Repository<R> repositoryFor(@Nonnull Class<R> aggregateType) {
                        //noinspection unchecked
                        return (Repository<R>) repositories.get(aggregateType);
                    }
                })
                .build();
        repositories.put(aggregateType, repository);
        return repository;
    }
}
