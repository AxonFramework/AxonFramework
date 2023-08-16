/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.eventhandling.EventHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory.inspectAggregate;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityEventForwardingModelInspectorTest {

    private static final String AGGREGATE_ID = "aggregateId";
    private static final String ENTITY_ID = "entityId";
    private static final String ANOTHER_ENTITY_ID = "anotherEntityId";

    @Test
    void expectEventsToBeRoutedToNoEntityForForwardModeSetToNone() {
        AggregateModel<SomeNoneEventForwardingEntityAggregate> inspector =
                inspectAggregate(SomeNoneEventForwardingEntityAggregate.class);

        SomeNoneEventForwardingEntityAggregate target = new SomeNoneEventForwardingEntityAggregate();

        // Both called once, as the entity does not receive any events
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(1L, entityPayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnly() {
        AggregateModel<SomeEventForwardingEntityAggregate> inspector =
                inspectAggregate(SomeEventForwardingEntityAggregate.class);

        SomeEventForwardingEntityAggregate target = new SomeEventForwardingEntityAggregate();

        // Called once
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        // Called twice - by aggregate and entity
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(2L, entityPayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyViaMethod() {
        AggregateModel<SomeGetterEventForwardingEntityAggregate> inspector =
                inspectAggregate(SomeGetterEventForwardingEntityAggregate.class);

        SomeGetterEventForwardingEntityAggregate target = new SomeGetterEventForwardingEntityAggregate();

        // Called once
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        // Called twice - by aggregate and entity
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(2L, entityPayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyWithSpecificRoutingKey() {
        AggregateModel<SomeEventForwardingEntityAggregateWithSpecificEventRoutingKey> inspector =
                inspectAggregate(SomeEventForwardingEntityAggregateWithSpecificEventRoutingKey.class);

        SomeEventForwardingEntityAggregateWithSpecificEventRoutingKey target =
                new SomeEventForwardingEntityAggregateWithSpecificEventRoutingKey();

        // Called once
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new SomeOtherEntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        // Called twice - by aggregate and entity
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new SomeOtherEntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(2L, entityPayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyWithSpecificRoutingKeyViaMethod() {
        AggregateModel<SomeGetterEventForwardingEntityAggregateWithSpecificEventRoutingKey> inspector =
                inspectAggregate(SomeGetterEventForwardingEntityAggregateWithSpecificEventRoutingKey.class);

        SomeGetterEventForwardingEntityAggregateWithSpecificEventRoutingKey target =
                new SomeGetterEventForwardingEntityAggregateWithSpecificEventRoutingKey();

        // Called once
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new SomeOtherEntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        // Called twice - by aggregate and entity
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new SomeOtherEntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(2L, entityPayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyForEntityCollection() {
        AggregateModel<SomeEventForwardingEntityCollectionAggregate> inspector =
                inspectAggregate(SomeEventForwardingEntityCollectionAggregate.class);

        SomeEventForwardingEntityCollectionAggregate target = new SomeEventForwardingEntityCollectionAggregate();

        // All called once, as there is an event per entity only
        AtomicLong entityOnePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId1", entityOnePayload)), target);
        AtomicLong entityTwoPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId2", entityTwoPayload)), target);
        AtomicLong entityThreePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId3", entityThreePayload)), target);

        assertEquals(1L, entityOnePayload.get());
        assertEquals(1L, entityTwoPayload.get());
        assertEquals(1L, entityThreePayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyForEntityCollectionViaMethod() {
        AggregateModel<SomeGetterEventForwardingEntityCollectionAggregate> inspector =
                inspectAggregate(SomeGetterEventForwardingEntityCollectionAggregate.class);

        SomeGetterEventForwardingEntityCollectionAggregate target = new SomeGetterEventForwardingEntityCollectionAggregate();

        // All called once, as there is an event per entity only
        AtomicLong entityOnePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId1", entityOnePayload)), target);
        AtomicLong entityTwoPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId2", entityTwoPayload)), target);
        AtomicLong entityThreePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId3", entityThreePayload)), target);

        assertEquals(1L, entityOnePayload.get());
        assertEquals(1L, entityTwoPayload.get());
        assertEquals(1L, entityThreePayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyForEntityMap() {
        AggregateModel<SomeEventForwardingEntityMapAggregate> inspector =
                inspectAggregate(SomeEventForwardingEntityMapAggregate.class);

        SomeEventForwardingEntityMapAggregate target = new SomeEventForwardingEntityMapAggregate();

        // All called once, as there is an event per entity only
        AtomicLong entityOnePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId1", entityOnePayload)), target);
        AtomicLong entityTwoPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId2", entityTwoPayload)), target);
        AtomicLong entityThreePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId3", entityThreePayload)), target);

        assertEquals(1L, entityOnePayload.get());
        assertEquals(1L, entityTwoPayload.get());
        assertEquals(1L, entityThreePayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyForEntityMapViaMethod() {
        AggregateModel<SomeGetterEventForwardingEntityMapAggregate> inspector =
                inspectAggregate(SomeGetterEventForwardingEntityMapAggregate.class);

        SomeGetterEventForwardingEntityMapAggregate target = new SomeGetterEventForwardingEntityMapAggregate();

        // All called once, as there is an event per entity only
        AtomicLong entityOnePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId1", entityOnePayload)), target);
        AtomicLong entityTwoPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId2", entityTwoPayload)), target);
        AtomicLong entityThreePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent("entityId3", entityThreePayload)), target);

        assertEquals(1L, entityOnePayload.get());
        assertEquals(1L, entityTwoPayload.get());
        assertEquals(1L, entityThreePayload.get());
    }

    @Test
    void expectEventsToBeRoutedToRightEntityOnlyWithMultipleEntities() {
        AggregateModel<SomeMixedEventForwardingEntityAggregate> inspector =
                inspectAggregate(SomeMixedEventForwardingEntityAggregate.class);

        SomeMixedEventForwardingEntityAggregate target = new SomeMixedEventForwardingEntityAggregate();

        // Called once
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        // Called twice - by aggregate and entity via annotated but random method name. Not a standard named getter.
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ENTITY_ID, entityPayload)), target);
        // Called twice - by aggregate and entity. Another entity, via annotated field.
        AtomicLong anotherEntityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ANOTHER_ENTITY_ID, anotherEntityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(2L, entityPayload.get());
        assertEquals(2L, anotherEntityPayload.get());
    }

    private static class SomeNoneEventForwardingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventForwardingMode = ForwardNone.class)
        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventForwardingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeGetterEventForwardingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        public SomeEventForwardedEntity getEntity() {
            return entity;
        }

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventForwardingEntityAggregateWithSpecificEventRoutingKey {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class, routingKey = "someIdentifier")
        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @EventHandler
        public void handle(SomeOtherEntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeGetterEventForwardingEntityAggregateWithSpecificEventRoutingKey {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class, routingKey = "someIdentifier")
        public SomeEventForwardedEntity getEntity() {
            return entity;
        }

        @EventHandler
        public void handle(SomeOtherEntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventForwardingEntityCollectionAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        private List<SomeEventForwardedEntity> entities;

        SomeEventForwardingEntityCollectionAggregate() {
            this.entities = new ArrayList<>();
            entities.add(new SomeEventForwardedEntity("entityId1"));
            entities.add(new SomeEventForwardedEntity("entityId2"));
            entities.add(new SomeEventForwardedEntity("entityId3"));
        }
    }

    private static class SomeGetterEventForwardingEntityCollectionAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        private List<SomeEventForwardedEntity> entities;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        public List<SomeEventForwardedEntity> getEntities() {
            return entities;
        }

        SomeGetterEventForwardingEntityCollectionAggregate() {
            this.entities = new ArrayList<>();
            entities.add(new SomeEventForwardedEntity("entityId1"));
            entities.add(new SomeEventForwardedEntity("entityId2"));
            entities.add(new SomeEventForwardedEntity("entityId3"));
        }
    }

    private static class SomeEventForwardingEntityMapAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        private Map<String, SomeEventForwardedEntity> entities;

        SomeEventForwardingEntityMapAggregate() {
            this.entities = new HashMap<>();
            entities.put("entityId1", new SomeEventForwardedEntity("entityId1"));
            entities.put("entityId2", new SomeEventForwardedEntity("entityId2"));
            entities.put("entityId3", new SomeEventForwardedEntity("entityId3"));
        }
    }

    private static class SomeGetterEventForwardingEntityMapAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        private Map<String, SomeEventForwardedEntity> entities;

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        public Map<String, SomeEventForwardedEntity> getEntities() {
            return entities;
        }

        SomeGetterEventForwardingEntityMapAggregate() {
            this.entities = new HashMap<>();
            entities.put("entityId1", new SomeEventForwardedEntity("entityId1"));
            entities.put("entityId2", new SomeEventForwardedEntity("entityId2"));
            entities.put("entityId3", new SomeEventForwardedEntity("entityId3"));
        }
    }

    private static class SomeMixedEventForwardingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        private SomeEventForwardedEntity entity = new SomeEventForwardedEntity(ENTITY_ID);

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        private SomeEventForwardedEntity anotherEntity = new SomeEventForwardedEntity(ANOTHER_ENTITY_ID);

        @AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)
        public SomeEventForwardedEntity entityMethod() {
            return entity;
        }

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventForwardedEntity {

        @EntityId
        private final String entityId;

        SomeEventForwardedEntity(String entityId) {
            this.entityId = entityId;
        }

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class EntityRoutedEvent {

        private final String entityId;
        private final AtomicLong value;

        private EntityRoutedEvent(String entityId, AtomicLong value) {
            this.entityId = entityId;
            this.value = value;
        }

        public String getEntityId() {
            return entityId;
        }

        public AtomicLong getValue() {
            return value;
        }
    }

    private static class SomeOtherEntityRoutedEvent extends EntityRoutedEvent {

        private final String someIdentifier;

        private SomeOtherEntityRoutedEvent(String someIdentifier, AtomicLong value) {
            super(someIdentifier, value);
            this.someIdentifier = someIdentifier;
        }

        public String getSomeIdentifier() {
            return someIdentifier;
        }
    }
}
