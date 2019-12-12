/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.config;

import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.axonframework.disruptor.commandhandling.DisruptorCommandBus;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.NoSnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.Repository;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class to validate the {@link AggregateConfigurer}'s inner workings.
 *
 * @author Steven van Beelen
 */
public class AggregateConfigurerTest {

    private Configuration mockConfiguration;

    private EventStore testEventStore;
    private ParameterResolverFactory testParameterResolverFactory;

    private AggregateConfigurer<TestAggregate> testSubject;

    @Before
    public void setUp() {
        mockConfiguration = mock(Configuration.class);

        testEventStore = mock(EventStore.class);
        when(mockConfiguration.eventBus()).thenReturn(testEventStore);
        when(mockConfiguration.eventStore()).thenReturn(testEventStore);

        testParameterResolverFactory = mock(ParameterResolverFactory.class);
        when(mockConfiguration.parameterResolverFactory()).thenReturn(testParameterResolverFactory);

        testSubject = new AggregateConfigurer<>(TestAggregate.class);
    }

    @Test
    public void testConfiguredDisruptorCommandBusCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        when(mockConfiguration.commandBus()).thenReturn(disruptorCommandBus);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    @Test
    public void testConfiguredDisruptorCommandBusAsLocalSegmentCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        DistributedCommandBus distributedCommandBusImplementation = mock(DistributedCommandBus.class);
        when(distributedCommandBusImplementation.localSegment()).thenReturn(disruptorCommandBus);
        when(mockConfiguration.commandBus()).thenReturn(distributedCommandBusImplementation);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    private static class TestAggregate {

        TestAggregate() {
            // No-op constructor
        }
    }
}