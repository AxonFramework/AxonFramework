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

package org.axonframework.eventsourcing.annotation;

import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.inmemory.AsyncInMemoryEventStorageEngine;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.TargetEntityId;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBasedEventSourcingEntityRepositoryTest {

    @Test
    void doesNotAcceptNonAnnotatedEntityClass() {
        assertThrows(IllegalArgumentException.class, () -> new AnnotationBasedEventSourcingEntityRepository<>(
                Mockito.mock(),
                String.class,
                String.class
        ));
    }

    @Nested
    class ClassName extends AnnotationBasedEntityMiniTestSuite {

        ClassName() {
            super(SimpleSequenceEntity.class, "SimpleSequenceEntity");
        }
    }

    @Nested
    class TagKey extends AnnotationBasedEntityMiniTestSuite {

        TagKey() {
            super(TagBasedSequenceEntity.class, "mySuperSpecialTag");
        }

        @EventSourcedEntity(tagKey = "mySuperSpecialTag")
        static class TagBasedSequenceEntity extends AbstractSequenceEntity {

        }
    }


    @Nested
    class EventCriteriaBuilderMethod extends AnnotationBasedEntityMiniTestSuite {

        EventCriteriaBuilderMethod() {
            super(EventCriteriaBuilderSequenceEntity.class, "myCriteriaBuilderTag");
        }

        @EventSourcedEntity
        static class EventCriteriaBuilderSequenceEntity extends AbstractSequenceEntity {

            @EventCriteriaBuilder
            public static EventCriteria buildCriteriaFor(String myString) {
                return EventCriteria.match()
                                    .eventsOfAnyType()
                                    .withTags("myCriteriaBuilderTag", myString);
            }
        }
    }

    @Nested
    class MetaAnnotated extends AnnotationBasedEntityMiniTestSuite {

        MetaAnnotated() {
            super(MetaAnnotatedSequenceEntity.class, "metaAnnotated");
        }

        @MetaAnnotatedEventSourcingEntity
        static class MetaAnnotatedSequenceEntity extends AbstractSequenceEntity {

        }
    }

    @Nested
    class CustomEventCriteriaResolver extends AnnotationBasedEntityMiniTestSuite {

        CustomEventCriteriaResolver() {
            super(CustomEventCriteriaResolverSequenceEntity.class, "customCriteriaResolver");
        }

        @EventSourcedEntity(criteriaResolver = CustomCriteriaResolver.class)
        static class CustomEventCriteriaResolverSequenceEntity extends AbstractSequenceEntity {

        }

        static class CustomCriteriaResolver implements CriteriaResolver<String> {

            @Override
            public EventCriteria apply(String id) {
                return EventCriteria.match()
                                    .eventsOfAnyType()
                                    .withTags("customCriteriaResolver", id);
            }
        }
    }

    @Nested
    class CustomCreator extends AnnotationBasedEntityMiniTestSuite {

        CustomCreator() {
            super(CustomCreatorSequenceEntity.class, "CustomCreatorSequenceEntity");
        }

        @EventSourcedEntity(entityCreator = CustomEntityFactory.class)
        static class CustomCreatorSequenceEntity extends AbstractSequenceEntity {

            private CustomCreatorSequenceEntity() {
                throw new UnsupportedOperationException("Should not be called");
            }

            private CustomCreatorSequenceEntity(String one, String two, String three) {
                // No-op
            }
        }

        static class CustomEntityFactory implements EventSourcedEntityFactory<String, CustomCreatorSequenceEntity> {

            @Override
            public CustomCreatorSequenceEntity createEntity(Class<CustomCreatorSequenceEntity> entityType, String id) {
                return new CustomCreatorSequenceEntity("one", "two", "three");
            }
        }
    }

    abstract class AnnotationBasedEntityMiniTestSuite {

        private final SimpleEventStore eventStore;
        private final AnnotationBasedEventSourcingEntityRepository<String, ? extends AbstractSequenceEntity> repository;
        private final StateManager stateManager;

        public AnnotationBasedEntityMiniTestSuite(Class<? extends AbstractSequenceEntity> entityClass,
                                                  String expectedTagKey) {
            this.eventStore = new SimpleEventStore(
                    new AsyncInMemoryEventStorageEngine(),
                    e -> {
                        if (e.getPayload() instanceof MySequenceEvent mySequenceEvent) {
                            return Set.of(Tag.of(expectedTagKey, mySequenceEvent.id()));
                        }
                        throw new IllegalArgumentException("Unsupported event type: " + e.getPayload().getClass());
                    }
            );
            this.repository = new AnnotationBasedEventSourcingEntityRepository<>(
                    eventStore,
                    String.class,
                    entityClass
            );
            this.stateManager = SimpleStateManager.builder("StateManager")
                                                  .register(repository)
                                                  .build();
        }

        @Test
        void verifyEventSourcingWorksProperly() {
            var component = StatefulCommandHandlingComponent
                    .create("msch", stateManager)
                    .subscribe(
                            new MessageType(MyEntitySimpleCommand.class).qualifiedName(),
                            (c, sm, ctx) -> {
                                MyEntitySimpleCommand command = (MyEntitySimpleCommand) c.getPayload();
                                AbstractSequenceEntity entity = sm.loadEntity(repository.entityType(),
                                                                              command.myId(),
                                                                              ctx)
                                                                  .join();
                                int newSequenceNumber = entity.getSequenceNumber() + 1;
                                appendEvent(new MySequenceEvent(command.myId(), newSequenceNumber), ctx);
                                return MessageStream.just(null);
                            });

            sendCommand(component, new MyEntitySimpleCommand("myId"));
            verifySequenceNumber("myId", 0);
            sendCommand(component, new MyEntitySimpleCommand("myId"));
            verifySequenceNumber("myId", 1);

            // Try out another ID that should not match
            sendCommand(component, new MyEntitySimpleCommand("myId2"));
            verifySequenceNumber("myId2", 0);

            // And the other ID should not influence the original
            sendCommand(component, new MyEntitySimpleCommand("myId"));
            verifySequenceNumber("myId", 2);
        }

        private void verifySequenceNumber(String myId, int expected) {
            AbstractSequenceEntity entity = stateManager
                    .loadEntity(repository.entityType(), myId, new StubProcessingContext())
                    .join();
            assertEquals(expected, entity.getSequenceNumber());
        }

        private void appendEvent(Object event, ProcessingContext ctx) {
            eventStore.transaction(ctx).appendEvent(
                    new GenericEventMessage<>(new MessageType(event.getClass()), event)
            );
        }
    }


    protected <T> void sendCommand(
            CommandHandlingComponent component,
            T payload
    ) {
        AsyncUnitOfWork uow = new AsyncUnitOfWork();
        uow.executeWithResult((context) -> {
            GenericCommandMessage<T> command = new GenericCommandMessage<>(
                    new MessageType(payload.getClass()),
                    payload);
            return component.handle(command, context).first().asCompletableFuture();
        }).join();
    }

    record MyEntitySimpleCommand(
            @TargetEntityId
            String myId
    ) {

    }

    @EventSourcedEntity
    static class MyEntity {

        private Integer sequenceNumber = -1;

        @EventSourcingHandler
        void handle(MySequenceEvent event) {
            sequenceNumber = event.sequenceNumber();
        }

        Integer getSequenceNumber() {
            return sequenceNumber;
        }

        @EventCriteriaBuilder
        public static EventCriteria buildCriteriaFor(String myString) {
            return EventCriteria.match()
                                .eventsOfAnyType()
                                .withTags("myTag", myString);
        }
    }

    static class AbstractSequenceEntity {

        private Integer sequenceNumber = -1;

        @EventSourcingHandler
        void handle(MySequenceEvent event) {
            sequenceNumber = event.sequenceNumber();
        }

        Integer getSequenceNumber() {
            return sequenceNumber;
        }
    }

    @EventSourcedEntity
    static class SimpleSequenceEntity extends AbstractSequenceEntity {

    }

    record MySequenceEvent(
            String id,
            Integer sequenceNumber
    ) {

    }
}