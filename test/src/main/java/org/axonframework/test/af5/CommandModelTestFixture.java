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

package org.axonframework.test.af5;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.eventstore.AsyncEventStore;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.test.aggregate.ResultValidator;
import org.axonframework.test.aggregate.ResultValidatorImpl;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.MatchAllFieldFilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CommandModelTestFixture implements CommandModelTest.Executor {

    private final NewConfiguration configuration;
    private final CommandBus commandBus;
    private final AsyncEventStore eventStore;
    private final AsyncUnitOfWork unitOfWork;
    private final MessageTypeResolver messageTypeResolver;

    private List<FieldFilter> fieldFilters = new ArrayList<>();

    private Deque<EventMessage<?>> givenEvents;

    public CommandModelTestFixture(ApplicationConfigurer<?> configurer) {
        this.configuration = configurer.build();
        this.commandBus = configuration.getComponent(CommandBus.class);
        this.eventStore = configuration.getComponent(AsyncEventStore.class);
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.unitOfWork = new AsyncUnitOfWork();
    }

    CommandModelTest.Executor givenEvent(Object payload) {
        return givenEvent(payload, MetaData.emptyInstance());
    }

    CommandModelTest.Executor givenEvent(Object payload, Map<String, ?> metaData) {
        return givenEvent(payload, MetaData.from(metaData));
    }

    CommandModelTest.Executor givenEvent(Object payload, MetaData metaData) {
        var eventMessage = new GenericEventMessage<>(
                messageTypeResolver.resolve(payload.getClass()),
                payload,
                metaData
        );
        return givenEvents(eventMessage);
    }

    CommandModelTest.Executor givenEvents(EventMessage<?>... events) {
        Collections.addAll(givenEvents, events);
        return this;
    }


    @Override
    public ResultValidator<?> when(Object command, Map<String, ?> metaData) {
        return null;
    }

    private void executeAtSimulatedTime(Runnable runnable) {
        Clock previousClock = GenericEventMessage.clock;
        try {
            GenericEventMessage.clock = Clock.fixed(currentTime(), ZoneOffset.UTC);
            runnable.run();
        } finally {
            GenericEventMessage.clock = previousClock;
        }
    }

    private Instant currentTime() {
        return Instant.now();
    }
}
