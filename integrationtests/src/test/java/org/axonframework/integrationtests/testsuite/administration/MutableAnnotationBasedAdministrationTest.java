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
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.common.PersonType;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableCustomer;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableEmployee;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutablePerson;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.junit.jupiter.api.*;

import java.util.Set;

/**
 * THIS CLASS ONLY EXIST TO VERIFY THE CURRENT FUNCTIONALITY CAN BE APPLIED WITH ANNOTATIONS. THIS CLASS SHOULD NOT BE
 * REVIEWED. It's just proof of concept.
 * <p>
 * All classes and annotations needed are located in this same class, so it can easily be skipped during review.
 */
@Disabled
public class MutableAnnotationBasedAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        EntityModel<MutablePerson> personModel = new AnnotationTestDefinitions.AnnotatedEventSourcedEntityModel<>(
                MutablePerson.class,
                configuration.getComponent(ParameterResolverFactory.class),
                Set.of(MutableCustomer.class, MutableEmployee.class));


        EventSourcingRepository<PersonIdentifier, MutablePerson> repository = new EventSourcingRepository<>(
                PersonIdentifier.class,
                MutablePerson.class,
                configuration.getComponent(EventStore.class),
                (type, id) -> {
                    if (id.type() == PersonType.EMPLOYEE) {
                        return new MutableEmployee();
                    } else if (id.type() == PersonType.CUSTOMER) {
                        return new MutableCustomer();
                    }
                    throw new IllegalArgumentException("Unknown type: " + id.type());
                },
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
