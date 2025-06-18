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
import org.axonframework.eventsourcing.annotation.reflection.AnnotationBasedEventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableCustomer;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableEmployee;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutablePerson;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.annotation.AnnotatedEntityMetamodel;

import java.util.Set;

/**
 * Runs the administration test suite using as many reflection components of the {@link EntityMetamodel} and
 * related classes as possible. As reflection-based components are added, this test may change to use more of them.
 */
public class ImmutableReflectionEntityModelAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        EntityMetamodel<ImmutablePerson> personModel = AnnotatedEntityMetamodel.forPolymorphicType(
                ImmutablePerson.class,
                Set.of(ImmutableEmployee.class, ImmutableCustomer.class),
                configuration.getComponent(ParameterResolverFactory.class),
                configuration.getComponent(MessageTypeResolver.class)
        );

        EventSourcingRepository<PersonIdentifier, ImmutablePerson> repository = new EventSourcingRepository<>(
                PersonIdentifier.class,
                ImmutablePerson.class,
                configuration.getComponent(EventStore.class),
                new AnnotationBasedEventSourcedEntityFactory<>(ImmutablePerson.class,
                                                               PersonIdentifier.class,
                                                               Set.of(ImmutableEmployee.class, ImmutableCustomer.class),
                                                               configuration.getComponent(ParameterResolverFactory.class),
                                                               configuration.getComponent(MessageTypeResolver.class)),
                (s, ctx) -> EventCriteria.havingTags("Person", s.key()),
                personModel
        );

        return new EntityCommandHandlingComponent<>(
                repository,
                personModel,
                new AnnotationBasedEntityIdResolver<>()
        );
    }
}
