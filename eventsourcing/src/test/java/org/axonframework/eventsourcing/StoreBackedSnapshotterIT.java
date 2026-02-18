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

package org.axonframework.eventsourcing;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.snapshot.StoreBackedSnapshotter;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.api.SnapshotContext;
import org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy;
import org.axonframework.eventsourcing.snapshot.api.SnapshotStore;
import org.axonframework.eventsourcing.snapshot.api.Snapshotter;
import org.axonframework.eventsourcing.snapshot.inmemory.InMemorySnapshotStore;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link StoreBackedSnapshotter} with an {@link InMemorySnapshotStore}.
 *
 * @author John Hendrikx
 */
public class StoreBackedSnapshotterIT {
    static final String ACCOUNT_ID = "1";
    static final String FQN = Account.class.getName() + "#" + String.class.getName();

    static volatile int evolveCalls;

    StateManager stateManager;
    EventStore eventStore;
    UnitOfWorkFactory unitOfWorkFactory;

    @BeforeEach
    void beforeEach() {
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        SnapshotPolicy snapshotPolicy = SnapshotPolicy.afterEvents(3);
        StoreBackedSnapshotter<String, Account> snapshotter = new StoreBackedSnapshotter<>(
            snapshotStore,
            MessageType.fromString("my-snapshot#1"),
            snapshotPolicy
        );

        AxonConfiguration configuration = EventSourcingConfigurer.create()
            .registerEntity(
                EventSourcedEntityModule.declarative(String.class, Account.class)
                    .messagingModel((c, model) -> model.entityEvolver(new SealedEntityEvolver<>(AccountEvents.class, Account::onEvent)).build())
                    .entityFactory(c -> (id, msg, ctx) -> {
                        AccountCreated ac = msg.payloadAs(AccountCreated.class);

                        return new Account(ac.id, ac.name, 0);
                    })
                    .criteriaResolver(c -> (id, ctx) -> EventCriteria.havingTags(Tag.of("account", id)))
                    .snapshotter(c -> snapshotter)
                    .build()
            )
            .build();

        configuration.start();

        this.stateManager = configuration.getComponent(StateManager.class);
        this.eventStore = configuration.getComponent(EventStore.class);
        this.unitOfWorkFactory = configuration.getComponent(UnitOfWorkFactory.class);
    }

    @Test
    void testSnapshotAfterThreeEvents() {
        publish(
            new AccountCreated(ACCOUNT_ID, "My Account"),
            new FundsDeposited(ACCOUNT_ID, 10050)
        );

        {
            evolveCalls = 0;

            Account account = loadAccount(ACCOUNT_ID);

            assertThat(evolveCalls).isEqualTo(2);
            assertThat(account.balance).isEqualTo(10050);
        }

        publish(
            new FundsDeposited(ACCOUNT_ID, 1000),
            new FundsWithdrawn(ACCOUNT_ID, 1),
            new FundsDeposited(ACCOUNT_ID, 2000),
            new FundsWithdrawn(ACCOUNT_ID, 2),
            new FundsWithdrawn(ACCOUNT_ID, 3),
            new FundsDeposited(ACCOUNT_ID, 3000)
        );

        {   // Expect a full evolution of the entity at first:
            evolveCalls = 0;

            Account account = loadAccount(ACCOUNT_ID);

            assertThat(evolveCalls).isEqualTo(8);
            assertThat(account.balance).isEqualTo(16044);
        }

        {
            evolveCalls = 0;

            Account account = loadAccount(ACCOUNT_ID);

            assertThat(evolveCalls).isZero();  // No evolution needed as a snapshot should have been created
            assertThat(account.balance).isEqualTo(16044);
        }

        publish(
            new FundsDeposited(ACCOUNT_ID, 1)
        );

        {
            evolveCalls = 0;

            Account account = loadAccount(ACCOUNT_ID);

            assertThat(evolveCalls).isEqualTo(1);
            assertThat(account.balance).isEqualTo(16045);
        }
    }


    @SuppressWarnings("unchecked")
    void publish(Object... events) {
        eventStore.publish(null, (List<EventMessage>)(List<?>)Arrays.stream(events).map(e -> new GenericEventMessage(new MessageType(e.getClass()), e)).toList());
    }

    Account loadAccount(String identifier) {
        Repository<String, Account> repository = stateManager.repository(Account.class, String.class);
        UnitOfWork unitOfWork = unitOfWorkFactory.create();

        return unitOfWork.executeWithResult(pc -> repository.load(ACCOUNT_ID, pc)).join().entity();
    }

    sealed interface AccountEvents {}
    record AccountCreated(@EventTag(key = "account") String id, String name) implements AccountEvents {}
    record FundsWithdrawn(@EventTag(key = "account") String id, long amount) implements AccountEvents {}
    record FundsDeposited(@EventTag(key = "account") String id, long amount) implements AccountEvents {}

    record Account(String id, String name, long balance) {
        Account withBalance(long balance) {
            return new Account(id, name, balance);
        }

        static Account onEvent(Account input, AccountEvents events) {
            evolveCalls++;

            return switch(events) {
                case AccountCreated ac -> new Account(ac.id, ac.name, 0);
                case FundsWithdrawn fw -> input.withBalance(input.balance - fw.amount);
                case FundsDeposited fd -> input.withBalance(input.balance + fd.amount);
            };
        }
    }

    static class SealedEntityEvolver<T, E> implements EntityEvolver<E> {
        private final Class<T> cls;
        private final BiFunction<E, T, E> evolver;

        public SealedEntityEvolver(Class<T> cls, BiFunction<E, T, E> evolver) {
            this.cls = cls;
            this.evolver = evolver;
        }

        @Override
        public E evolve(E entity, EventMessage event, ProcessingContext context) {
            EventConverter converter = context.component(EventConverter.class);

            // note: as everything is in memory, this is just a cast check, which is why this works...
            return evolver.apply(entity, event.payloadAs(cls, converter));
        }
    }
}
