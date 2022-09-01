/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.spring.eventsourcing;

import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.spring.domain.SpringWiredAggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {"/META-INF/spring/spring-prototype-aggregate-factory.xml"})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SpringPrototypeAggregateFactoryTest {

    @Autowired
    private SpringPrototypeAggregateFactory<SpringWiredAggregate> testSubject;

    @Test
    void contextStarts() {
        assertNotNull(testSubject);
    }

    @Test
    void createNewAggregateInstance() {
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2", new GenericDomainEventMessage<>("SpringWiredAggregate", "id2", 0,
                                                                                                                "FirstEvent"));
        assertTrue(aggregate.isInitialized(), "Aggregate's init method not invoked");
        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
        assertEquals("it's here", aggregate.getSpringConfiguredName());
    }

    @Test
    void processSnapshotAggregateInstance() {
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2",
                                                                     new GenericDomainEventMessage<>("SpringWiredAggregate", "id2", 5,
                                                                                                     new SpringWiredAggregate()));
        assertTrue(aggregate.isInitialized(), "Aggregate's init method not invoked");
        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
        assertEquals("it's here", aggregate.getSpringConfiguredName());
    }
}
