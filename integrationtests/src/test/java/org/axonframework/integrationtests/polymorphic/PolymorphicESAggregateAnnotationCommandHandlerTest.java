/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.polymorphic;

import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AggregateModel;

import javax.persistence.EntityManager;

/**
 * Tests for ES aggregate polymorphism.
 *
 * @author Milan Savic
 */
public class PolymorphicESAggregateAnnotationCommandHandlerTest
        extends AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    @Override
    public Repository<ParentAggregate> repository(AggregateModel<ParentAggregate> model,
                                                  EntityManager entityManager) {
        return EventSourcingRepository.builder(ParentAggregate.class)
                                      .eventStore(EmbeddedEventStore.builder()
                                                                    .storageEngine(new InMemoryEventStorageEngine())
                                                                    .build())
                                      .aggregateModel(model)
                                      .build();
    }
}
