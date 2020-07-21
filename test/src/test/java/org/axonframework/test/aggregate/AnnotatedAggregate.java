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

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.modelling.command.AggregateLifecycle.markDeleted;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stub aggregate used to test various scenarios of the {@link FixtureConfiguration}.
 *
 * @author Allard Buijze
 */
@SuppressWarnings("unused")
class AnnotatedAggregate implements AnnotatedAggregateInterface {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @SuppressWarnings("UnusedDeclaration")
    private transient int counter;
    private Integer lastNumber;
    @SuppressWarnings("FieldCanBeLocal")
    @AggregateIdentifier
    private String identifier;
    private MyEntity entity;

    public AnnotatedAggregate() {
    }

    @CommandHandler
    public AnnotatedAggregate(CreateAggregateCommand command, EventBus eventBus, HardToCreateResource resource) {
        assertNotNull(resource, "resource should not be null");
        assertNotNull(eventBus, "Expected EventBus to be injected as resource");

        apply(new MyEvent(
                command.getAggregateIdentifier() == null ? UUID.randomUUID() : command.getAggregateIdentifier(), 0
        ));
    }

    @CommandHandler
    public void delete(DeleteCommand command) {
        apply(new MyAggregateDeletedEvent(command.isAsIllegalChange()));
        if (command.isAsIllegalChange()) {
            markDeleted();
        }
    }

    @CommandHandler
    public void doSomethingIllegal(IllegalStateChangeCommand command) {
        apply(new MyEvent(command.getAggregateIdentifier(), lastNumber + 1));
        lastNumber = command.getNewIllegalValue();
    }

    @CommandHandler
    public void handle(ResolveParameterCommand command, AtomicBoolean assertion) {
        assertFalse(assertion.get());
        assertion.set(true);
        apply(new ParameterResolvedEvent(identifier, assertion));
    }

    @EventSourcingHandler
    public void handleMyEvent(MyEvent event) {
        identifier = event.getAggregateIdentifier() == null ? null : event.getAggregateIdentifier().toString();
        lastNumber = event.getSomeValue();
        if (entity == null) {
            entity = new MyEntity();
        }
    }

    @EventSourcingHandler
    public void deleted(MyAggregateDeletedEvent event) {
        if (!event.isWithIllegalStateChange()) {
            markDeleted();
        }
    }

    @EventSourcingHandler
    public void handleAll(DomainEventMessage<?> event) {
        // We don't care about events
        logger.debug("Invoked with payload: " + event.getPayloadType().getName());
    }

    @Override
    public void doSomething(TestCommand command) {
        // This state change should be accepted, since it happens on a transient value
        counter++;
        apply(new MyEvent(command.getAggregateIdentifier(), lastNumber + 1));
    }
}
