/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.auditing.AuditingInterceptor;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventstore.EventStore;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.hamcrest.Description;
import org.junit.*;
import org.junit.internal.matchers.*;

import java.util.Arrays;
import java.util.Collections;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AuditingInterceptorIntegrationTest {

    private SimpleCommandBus commandBus;
    private EventBus eventBus;
    private EventSourcingRepository<StubAggregate> repository;
    private EventStore eventStore;

    @Before
    public void setUp() {
        this.commandBus = new SimpleCommandBus();
        this.eventBus = mock(EventBus.class);
        eventStore = mock(EventStore.class);
        this.repository = new EventSourcingRepository<>(StubAggregate.class, eventStore);
        repository.setEventBus(eventBus);
        StubAggregateCommandHandler target = new StubAggregateCommandHandler();
        target.setRepository(repository);
        target.setEventBus(eventBus);
        AnnotationCommandHandlerAdapter.subscribe(target, commandBus);
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    /**
     * Tests whether issue #AXON-38 is solved
     */
    @Test
    public void testAuditingInterceptorAlsoAddsInformationToEventsOfNewlyCreatedAggregate() {
        commandBus.subscribe(String.class.getName(), (commandMessage, unitOfWork) -> {
            StubAggregate aggregate = new StubAggregate("aggregateId");
            aggregate.makeAChange();
            repository.add(aggregate);
            return null;
        });

        final AuditingInterceptor auditingInterceptor = new AuditingInterceptor();
        auditingInterceptor.setAuditDataProvider(command -> Collections.singletonMap("audit", (Object)"data"));
        commandBus.setHandlerInterceptors(Arrays.asList(auditingInterceptor));

        commandBus.dispatch(asCommandMessage("command"));

        verify(eventStore).appendEvents(eq("StubAggregate"), argThat(new TypeSafeMatcher<DomainEventStream>() {
            @Override
            public boolean matchesSafely(DomainEventStream item) {
                DomainEventMessage first = item.peek();
                return "data".equals(first.getMetaData().get("audit"));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("An event with audit data");
            }
        }));

        verify(eventBus).publish(argThat(new TypeSafeMatcher<EventMessage>() {

            @Override
            public boolean matchesSafely(EventMessage item) {
                return "data".equals(item.getMetaData().get("audit"));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("An event with audit data");
            }
        }), isA(EventMessage.class));
    }
}
