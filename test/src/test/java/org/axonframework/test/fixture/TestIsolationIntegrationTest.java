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

package org.axonframework.test.fixture;

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.modelling.annotation.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.test.fixture.sampledomain.ChangeStudentNameCommand;
import org.axonframework.test.fixture.sampledomain.Student;
import org.axonframework.test.fixture.sampledomain.StudentNameChangedEvent;
import org.junit.jupiter.api.*;

import java.util.Objects;

class TestIsolationIntegrationTest {

    @Nested
    class TwoFixturesSharingConfigurer {

        @Test
        void fixturesSeeIsolatedEvents() {
            // given — shared configurer with event sourcing and test isolation
            var configurer = createConfigurerWithIsolation();

            // when — fixture A runs a given-when-then cycle
            var fixtureA = AxonTestFixture.with(configurer);
            fixtureA.given()
                    .noPriorActivity()
                    .when()
                    .command(new ChangeStudentNameCommand("student-1", "Alice"))
                    .then()
                    .events(new StudentNameChangedEvent("student-1", "Alice", 1));

            // then — fixture B (same configurer, same shared event store) sees no prior events
            var fixtureB = AxonTestFixture.with(configurer);
            fixtureB.given()
                    .noPriorActivity()
                    .when()
                    .command(new ChangeStudentNameCommand("student-1", "Bob"))
                    .then()
                    .events(new StudentNameChangedEvent("student-1", "Bob", 1));
        }

        @Test
        void fixturesSeeOwnEventsInAndChaining() {
            // given — shared configurer with event sourcing and test isolation
            var configurer = createConfigurerWithIsolation();

            var fixtureA = AxonTestFixture.with(configurer);

            // when — fixture A sets up state and chains
            fixtureA.given()
                    .noPriorActivity()
                    .when()
                    .command(new ChangeStudentNameCommand("student-1", "Alice"))
                    .then()
                    .events(new StudentNameChangedEvent("student-1", "Alice", 1))
                    .and()
                    .when()
                    .command(new ChangeStudentNameCommand("student-1", "Alice-2"))
                    .then()
                    .events(new StudentNameChangedEvent("student-1", "Alice-2", 2));

            // then — fixture B is isolated: "student-1" starts fresh with changes=1
            var fixtureB = AxonTestFixture.with(configurer);
            fixtureB.given()
                    .noPriorActivity()
                    .when()
                    .command(new ChangeStudentNameCommand("student-1", "Bob"))
                    .then()
                    .events(new StudentNameChangedEvent("student-1", "Bob", 1));
        }
    }

    @Nested
    class SingleFixtureChaining {

        @Test
        void singleFixtureSeesOwnEventsAcrossAndChain() {
            // given — configurer WITHOUT isolation to verify the base case works
            var configurer = createConfigurerWithoutIsolation();

            var fixture = AxonTestFixture.with(configurer);

            // when — first command, then chain
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("student-1", "Alice"))
                   .then()
                   .events(new StudentNameChangedEvent("student-1", "Alice", 1))
                   .and()
                   .when()
                   .command(new ChangeStudentNameCommand("student-1", "Alice-2"))
                   .then()
                   .events(new StudentNameChangedEvent("student-1", "Alice-2", 2));
        }
    }

    @Nested
    class BackwardCompatibility {

        @Test
        void fixtureWithoutIsolationBehavesAsNormal() {
            // given — configurer WITHOUT TestIsolationEnhancer
            var configurer = createConfigurerWithoutIsolation();

            // when — standard fixture usage
            var fixture = AxonTestFixture.with(configurer);
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new ChangeStudentNameCommand("student-1", "Alice"))
                   .then()
                   .events(new StudentNameChangedEvent("student-1", "Alice", 1));
        }
    }

    private static MessagingConfigurer createConfigurerWithIsolation() {
        var configurer = MessagingConfigurer.create();
        configurer.componentRegistry(cr -> cr.registerEnhancer(new TestIsolationEnhancer()));
        registerSampleStatefulCommandHandler(configurer);
        return configurer;
    }

    private static MessagingConfigurer createConfigurerWithoutIsolation() {
        var configurer = MessagingConfigurer.create();
        registerSampleStatefulCommandHandler(configurer);
        return configurer;
    }

    private static void registerSampleStatefulCommandHandler(MessagingConfigurer configurer) {
        configurer.componentRegistry(cr -> cr
                .registerComponent(
                        StateManager.class,
                        c -> {
                            var repository = new EventSourcingRepository<String, Student>(
                                    String.class,
                                    Student.class,
                                    c.getComponent(EventStore.class),
                                    EventSourcedEntityFactory.fromIdentifier(Student::new),
                                    (id, context) -> EventCriteria.havingTags("Student", id),
                                    new AnnotationBasedEntityEvolvingComponent<>(
                                            Student.class,
                                            c.getComponent(EventConverter.class),
                                            c.getComponent(MessageTypeResolver.class)
                                    ),
                                    null
                            );
                            return SimpleStateManager.named("testfixture")
                                                     .register(repository);
                        })
                .registerComponent(TagResolver.class,
                                   c -> new AnnotationBasedTagResolver())
                .registerComponent(EventStore.class,
                                   c -> new StorageEngineBackedEventStore(new InMemoryEventStorageEngine(),
                                                             new SimpleEventBus(),
                                                             c.getComponent(TagResolver.class)))
                .registerComponent(EventSink.class,
                                   c -> c.getComponent(EventStore.class))
                .registerDecorator(CommandBus.class, 50, (c, name, delegate) -> {
                    var statefulCommandHandler = SimpleCommandHandlingComponent
                            .create("mystatefulCH")
                            .subscribe(
                                    new QualifiedName(ChangeStudentNameCommand.class),
                                    (cmd, ctx) -> {
                                        var sm = ctx.component(StateManager.class);
                                        ChangeStudentNameCommand payload = (ChangeStudentNameCommand) cmd.payload();
                                        var student = sm.loadEntity(Student.class, payload.id(), ctx).join();
                                        if (!Objects.equals(student.getName(), payload.name())) {
                                            var eventSink = c.getComponent(EventSink.class);
                                            eventSink.publish(
                                                    ctx,
                                                    studentNameChangedEventMessage(payload.id(),
                                                                                   payload.name(),
                                                                                   student.getChanges() + 1)
                                            );
                                        }
                                        return MessageStream.empty().cast();
                                    });
                    delegate.subscribe(statefulCommandHandler);

                    return delegate;
                })
        );
    }

    private static GenericEventMessage studentNameChangedEventMessage(
            String id,
            String name,
            int change
    ) {
        return new GenericEventMessage(new MessageType(StudentNameChangedEvent.class),
                                         new StudentNameChangedEvent(id, name, change));
    }
}
