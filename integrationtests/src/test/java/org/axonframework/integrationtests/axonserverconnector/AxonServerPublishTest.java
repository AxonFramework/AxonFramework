/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.integrationtests.axonserverconnector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.BiFunction;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.repository.Repository;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
public class AxonServerPublishTest {

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer CONTAINER = new AxonServerContainer()
        .withDevMode(true)
        .withDcbContext(true);

    private static volatile int evolveCalls;

    private EventStore eventStore;
    private UnitOfWorkFactory unitOfWorkFactory;
    private StateManager stateManager;

    @BeforeEach
    protected void beforeEach() {
        ObjectMapper objectMapper = JsonMapper.builder()
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

        DelegatingEventConverter eventConverter = new DelegatingEventConverter(new JacksonConverter(objectMapper));

        AxonConfiguration configuration = EventSourcingConfigurer.create()
            .componentRegistry(cr -> cr.registerComponent(Converter.class, c -> eventConverter))
            .componentRegistry(cr -> cr.registerComponent(AxonServerConfiguration.class, c -> AxonServerConfiguration.builder()
                .componentName("AxonServerPublishTest")
                .servers(CONTAINER.getAxonServerAddress())
                .build()
            ))
            .registerEntity(
                EventSourcedEntityModule.declarative(String.class, Account.class)
                    .messagingModel((c, model) -> model.entityEvolver(new SealedEntityEvolver<>(AccountEvents.class, Account::onEvent)).build())
                    .entityFactory(c -> (id, msg, ctx) -> {
                        AccountCreated ac = msg.payloadAs(AccountCreated.class, eventConverter);

                        return new Account(ac.id, ac.name, 0);
                    })
                    .criteriaResolver(c -> (id, ctx) -> EventCriteria.havingTags(Tag.of("account", id)))
                    .build()
            )
            .build();

        configuration.start();

        this.eventStore = configuration.getComponent(EventStore.class);
        this.stateManager = configuration.getComponent(StateManager.class);
        this.unitOfWorkFactory = configuration.getComponent(UnitOfWorkFactory.class);
    }

    @Test
    protected void test() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            String ACCOUNT_ID = UUID.randomUUID().toString();
            int finalI = i;

            publish(
                new AccountCreated(ACCOUNT_ID, "My Account"),
                new FundsDeposited(ACCOUNT_ID, 10050)
            );

            //Thread.sleep(50);

            {
                evolveCalls = 0;

                Account account = loadAccount(ACCOUNT_ID);

                assertThat(account).withFailMessage(() -> "Fail on loop " + finalI).isNotNull();
                assertThat(evolveCalls).withFailMessage(() -> "Fail on loop " + finalI).isEqualTo(2);
                assertThat(account.balance()).isEqualTo(10050);
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    protected sealed interface AccountEvents {}
    public record AccountCreated(@EventTag(key = "account") String id, String name) implements AccountEvents {}
    public record FundsWithdrawn(@EventTag(key = "account") String id, long amountWithdrawn) implements AccountEvents {}
    public record FundsDeposited(@EventTag(key = "account") String id, long amountDeposited) implements AccountEvents {}
    public record AccountClosed(@EventTag(key = "account") String id, String reason) implements AccountEvents {}

    protected record Account(String id, String name, long balance) {
        Account withBalance(long balance) {
            return new Account(id, name, balance);
        }

        static Account onEvent(Account input, AccountEvents events) {
            evolveCalls++;

            return switch(events) {
                case AccountCreated ac -> new Account(ac.id, ac.name, 0);
                case FundsWithdrawn fw -> input.withBalance(input.balance - fw.amountWithdrawn);
                case FundsDeposited fd -> input.withBalance(input.balance + fd.amountDeposited);
                case AccountClosed ac -> input;
            };
        }
    }

    /**
     * Publish the given events.
     *
     * @param events the events to publish, cannot be {@code null}
     */
    protected void publish2(Object... events) {
        try {
            UnitOfWork unitOfWork = unitOfWorkFactory.create();

            unitOfWork.executeWithResult(pc -> eventStore.publish(pc, Arrays.stream(events).map(e -> new GenericEventMessage(new MessageType(e.getClass()), e)).toList())).join();
        }
        catch (Exception e) {
            throw new AssertionError("No exception expected", e);  // this gives us a proper location in the test that was running
        }
    }

    protected void publish(Object... events) {
        try {
            eventStore.publish(null, Arrays.stream(events).map(e -> new GenericEventMessage(new MessageType(e.getClass()), e)).toList()).join();
        }
        catch (Exception e) {
            throw new AssertionError("No exception expected", e);  // this gives us a proper location in the test that was running
        }
    }

    /**
     * Load the account with the given identifier.
     *
     * @param identifier the identifier, cannot be {@code null}
     * @return the account
     */
    protected Account loadAccount(String identifier) {
        try {
            Repository<String, Account> repository = stateManager.repository(Account.class, String.class);
            UnitOfWork unitOfWork = unitOfWorkFactory.create();

            return unitOfWork.executeWithResult(pc -> repository.load(identifier, pc)).join().entity();
        }
        catch (Exception e) {
            throw new AssertionError("No exception expected", e);  // this gives us a proper location in the test that was running
        }
    }

    private static class SealedEntityEvolver<T, E> implements EntityEvolver<E> {
        private final Class<T> cls;
        private final BiFunction<E, T, E> evolver;

        public SealedEntityEvolver(Class<T> cls, BiFunction<E, T, E> evolver) {
            this.cls = cls;
            this.evolver = evolver;
        }

        @Override
        public E evolve(E entity, EventMessage event, ProcessingContext context) {
            EventConverter converter = context.component(EventConverter.class);

            return evolver.apply(entity, event.payloadAs(cls, converter));
        }
    }
}
