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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.eventhandling.EventHandler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory.inspectAggregate;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;

public class EntityEventForwardingModelInspectorTest {

    private static final String AGGREGATE_ID = "aggregateId";
    private static final String ENTITY_ID = "entityId";

    @Test
    public void testExpectEventsToBeRoutedToNoEntityForForwardModeSetToNone() {
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
    public void testExpectEventsToBeRoutedToRightEntityOnly() {
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
    public void testExpectEventsToBeRoutedToRightEntityOnlyWithSpecificRoutingKey() {
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
    public void testExpectEventsToBeRoutedToRightEntityOnlyForEntityCollection() {
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
    public void testExpectEventsToBeRoutedToRightEntityOnlyForEntityMap() {
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
