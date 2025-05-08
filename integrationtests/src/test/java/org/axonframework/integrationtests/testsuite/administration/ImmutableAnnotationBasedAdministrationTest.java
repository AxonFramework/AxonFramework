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

package org.axonframework.integrationtests.testsuite.administration;

import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.reflection.ReflectionEventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableCustomer;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableEmployee;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutablePerson;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityModel;

import java.util.List;
import java.util.Set;

public class ImmutableAnnotationBasedAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        EntityModel<ImmutablePerson> personModel = new AnnotatedEntityModel<>(
                ImmutablePerson.class,
                configuration.getComponent(ParameterResolverFactory.class),
                configuration.getComponent(MessageTypeResolver.class),
                Set.of(ImmutableCustomer.class, ImmutableEmployee.class));


        EventSourcingRepository<PersonIdentifier, ImmutablePerson> repository = new EventSourcingRepository<>(
                PersonIdentifier.class,
                ImmutablePerson.class,
                configuration.getComponent(EventStore.class),
                new ReflectionEventSourcedEntityFactory<>(
                        ImmutablePerson.class, PersonIdentifier.class, Set.of(ImmutableCustomer.class, ImmutableEmployee.class), configuration
                ),
                s -> EventCriteria.havingTags("Person", s.key()),
                personModel
        );

        return new EntityCommandHandlingComponent<>(
                repository,
                personModel,
                new AnnotationBasedEntityIdResolver<>()
        );
    }
}
