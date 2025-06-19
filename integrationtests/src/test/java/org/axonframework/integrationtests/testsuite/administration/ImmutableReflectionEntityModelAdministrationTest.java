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

import org.axonframework.configuration.Module;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutablePerson;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.modelling.entity.EntityMetamodel;

/**
 * Runs the administration test suite using as many reflection components of the {@link EntityMetamodel} and
 * related classes as possible. As reflection-based components are added, this test may change to use more of them.
 */
public class ImmutableReflectionEntityModelAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    Module getModule() {
        return StatefulCommandHandlingModule.named("ImmutableReflectionEntityModelAdministrationTest")
                                            .entities()
                                            .entity(EventSourcedEntityModule
                                                            .annotated(PersonIdentifier.class, ImmutablePerson.class))
                                            .build();
    }
}
