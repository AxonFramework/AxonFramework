/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.eventhandling.EventHandler;
import org.junit.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.model.inspection.ModelInspector.inspectAggregate;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

public class EntityEventRoutingModelInspectorTest {

    private static final String AGGREGATE_ID = "aggregateId";
    private static final String ENTITY_ID = "entityId";

    @Test
    public void testExpectEventsToBeRoutedToNoEntityForForwardModeSetToNone() throws Exception {
        AggregateModel<SomeNoneEventRoutingEntityAggregate> inspector =
                inspectAggregate(SomeNoneEventRoutingEntityAggregate.class);

        SomeNoneEventRoutingEntityAggregate target = new SomeNoneEventRoutingEntityAggregate();

        // Both called once, as the entity does not receive any events
        AtomicLong aggregatePayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(AGGREGATE_ID, aggregatePayload)), target);
        AtomicLong entityPayload = new AtomicLong();
        inspector.publish(asEventMessage(new EntityRoutedEvent(ENTITY_ID, entityPayload)), target);

        assertEquals(1L, aggregatePayload.get());
        assertEquals(1L, entityPayload.get());
    }

    @Test
    public void testExpectEventsToBeRoutedToRightEntityOnly() throws Exception {
        AggregateModel<SomeEventRoutingEntityAggregate> inspector = inspectAggregate(SomeEventRoutingEntityAggregate.class);

        SomeEventRoutingEntityAggregate target = new SomeEventRoutingEntityAggregate();

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
    public void testExpectEventsToBeRoutedToRightEntityOnlyWithSpecificRoutingKey() throws Exception {
        AggregateModel<SomeEventRoutingEntityAggregateWithSpecificEventRoutingKey> inspector =
                inspectAggregate(SomeEventRoutingEntityAggregateWithSpecificEventRoutingKey.class);

        SomeEventRoutingEntityAggregateWithSpecificEventRoutingKey target =
                new SomeEventRoutingEntityAggregateWithSpecificEventRoutingKey();

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
    public void testExpectEventsToBeRoutedToRightEntityOnlyForEntityCollection() throws Exception {
        AggregateModel<SomeEventRoutingEntityCollectionAggregate> inspector =
                inspectAggregate(SomeEventRoutingEntityCollectionAggregate.class);

        SomeEventRoutingEntityCollectionAggregate target = new SomeEventRoutingEntityCollectionAggregate();

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
    public void testExpectEventsToBeRoutedToRightEntityOnlyForEntityMap() throws Exception {
        AggregateModel<SomeEventRoutingEntityMapAggregate> inspector =
                inspectAggregate(SomeEventRoutingEntityMapAggregate.class);

        SomeEventRoutingEntityMapAggregate target = new SomeEventRoutingEntityMapAggregate();

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

    private static class SomeNoneEventRoutingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventRoutingMode = ForwardingMode.NONE)
        private SomeEventRoutingEntity entity = new SomeEventRoutingEntity(ENTITY_ID);

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventRoutingEntityAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventRoutingMode = ForwardingMode.ROUTING_KEY)
        private SomeEventRoutingEntity entity = new SomeEventRoutingEntity(ENTITY_ID);

        @EventHandler
        public void handle(EntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventRoutingEntityAggregateWithSpecificEventRoutingKey {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventRoutingMode = ForwardingMode.ROUTING_KEY, eventRoutingKey = "someIdentifier")
        private SomeEventRoutingEntity entity = new SomeEventRoutingEntity(ENTITY_ID);

        @EventHandler
        public void handle(SomeOtherEntityRoutedEvent event) {
            event.getValue().incrementAndGet();
        }
    }

    private static class SomeEventRoutingEntityCollectionAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventRoutingMode = ForwardingMode.ROUTING_KEY)
        private List<SomeEventRoutingEntity> entities;

        SomeEventRoutingEntityCollectionAggregate() {
            this.entities = new ArrayList<>();
            entities.add(new SomeEventRoutingEntity("entityId1"));
            entities.add(new SomeEventRoutingEntity("entityId2"));
            entities.add(new SomeEventRoutingEntity("entityId3"));
        }
    }

    private static class SomeEventRoutingEntityMapAggregate {

        @AggregateIdentifier
        private String id = AGGREGATE_ID;

        @AggregateMember(eventRoutingMode = ForwardingMode.ROUTING_KEY)
        private Map<String, SomeEventRoutingEntity> entities;

        SomeEventRoutingEntityMapAggregate() {
            this.entities = new HashMap<>();
            entities.put("entityId1", new SomeEventRoutingEntity("entityId1"));
            entities.put("entityId2", new SomeEventRoutingEntity("entityId2"));
            entities.put("entityId3", new SomeEventRoutingEntity("entityId3"));
        }
    }

    private static class SomeEventRoutingEntity {

        @EntityId
        private final String entityId;

        SomeEventRoutingEntity(String entityId) {
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
