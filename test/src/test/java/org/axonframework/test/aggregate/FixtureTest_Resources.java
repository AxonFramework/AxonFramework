/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class FixtureTest_Resources {

    private FixtureConfiguration<AggregateWithResources> fixture;

    @Before
    public void setUp() {
        fixture = new AggregateTestFixture<>(AggregateWithResources.class);
    }

    @Test
    public void testResourcesAreScopedToSingleTest_ConstructorPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        final Executor resource = mock(Executor.class);
        fixture.registerInjectableResource(resource)
               .given()
               .when(new CreateAggregateCommand("id"));

        verify(resource).execute(isA(Runnable.class));
        verifyNoMoreInteractions(resource);
    }

    @Test
    public void testResourcesAreScopedToSingleTest_ConstructorPartTwo() {
        testResourcesAreScopedToSingleTest_ConstructorPartOne();
    }

    @Test
    public void testResourcesAreScopedToSingleTest_MethodPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        final Executor resource = mock(Executor.class);
        fixture.registerInjectableResource(resource)
               .given(new MyEvent("id", 1))
               .when(new TestCommand("id"))
               .expectResultMessagePayload(fixture.getCommandBus());

        verify(resource).execute(isA(Runnable.class));
        verifyNoMoreInteractions(resource);
    }

    @Test
    public void testResourcesAreScopedToSingleTest_MethodPartTwo() {
        testResourcesAreScopedToSingleTest_MethodPartOne();
    }

    public static class AggregateWithResources {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public AggregateWithResources(CreateAggregateCommand cmd, Executor resource, CommandBus commandBus) {
            apply(new MyEvent(cmd.getAggregateIdentifier(), 1));
            resource.execute(() -> fail("Should not really be executed"));
        }

        @CommandHandler
        public CommandBus handleCommand(TestCommand cmd, Executor resource, CommandBus commandBus) {
            resource.execute(() -> fail("Should not really be executed"));
            return commandBus;
        }

        @EventSourcingHandler
        void handle(MyEvent event, Executor resource) {
            assertNotNull(resource);
            this.id = event.getAggregateIdentifier().toString();
        }

        public AggregateWithResources() {
        }
    }
}
