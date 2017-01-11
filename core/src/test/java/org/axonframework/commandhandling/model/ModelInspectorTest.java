/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.junit.Test;

import javax.persistence.Id;
import java.lang.annotation.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModelInspectorTest {

    @Test
    public void testDetectAllAnnotatedHandlers() throws Exception {
        AggregateModel<SomeAnnotatedHandlers> inspector = ModelInspector.inspectAggregate(SomeAnnotatedHandlers.class);

        CommandMessage<?> message = asCommandMessage("ok");
        assertEquals(true, inspector.commandHandler(message.getCommandName()).handle(message, new SomeAnnotatedHandlers()));
        assertEquals(false, inspector.commandHandler(message.getCommandName()).handle(asCommandMessage("ko"), new SomeAnnotatedHandlers()));
    }

    @Test
    public void testDetectAllAnnotatedHandlersInHierarchy() throws Exception {
        AggregateModel<SomeSubclass> inspector = ModelInspector.inspectAggregate(SomeSubclass.class);

        SomeSubclass target = new SomeSubclass();
        CommandMessage<?> message = asCommandMessage("sub");
        assertEquals(true, inspector.commandHandler(message.getCommandName()).handle(message, target));
        assertEquals(false, inspector.commandHandler(message.getCommandName()).handle(asCommandMessage("ok"), target));
    }

    @Test
    public void testDetectAllAnnotatedHandlersInRecursiveHierarchy() throws Exception {
        AggregateModel<SomeRecursiveRoot> inspector = ModelInspector.inspectAggregate(SomeRecursiveRoot.class);

        SomeRecursiveRoot target = new SomeRecursiveRoot();

        // Create a hierarchy of id's that we will use in this test.
        // The resulting hierarchy will look as follows:
        // root 
        //      child0
        //              child1
        //                      child2
        //                      child3
        //              child4
        String childId0 = SomeRecursiveEntity.childId(target.rootId, 1);
        String childId1 = SomeRecursiveEntity.childId(childId0, 1);
        String childId2 = SomeRecursiveEntity.childId(childId1, 1);
        String childId3 = SomeRecursiveEntity.childId(childId1, 2);
        String childId4 = SomeRecursiveEntity.childId(childId0, 2);

        // Assert child0: the id should match and it should not have any children (yet)
        SomeRecursiveEntity child0 = target.entity;
        assertEquals(childId0, child0.entityId);
        assertEquals(0, child0.children.size());

        // Publish an event that is picked up by child0. It should have 1 child afterwards.
        inspector.publish(asEventMessage(childId0), target);
        assertEquals(1, child0.children.size());

        // Assert child1: the id should match and it should not have any children (yet)
        SomeRecursiveEntity child1 = child0.children.get(0);
        assertNotNull(child1);
        assertEquals(childId1, child1.entityId);
        assertEquals(0, child1.children.size());

        // Publish 2 events that are picked up by child1. It should have 2 children afterwards.
        inspector.publish(asEventMessage(childId1), target);
        inspector.publish(asEventMessage(childId1), target);
        assertEquals(2, child1.children.size());

        // Assert child2: the id should match and it should not have any children
        SomeRecursiveEntity child2 = child1.children.get(0);
        assertNotNull(child2);
        assertEquals(childId2, child2.entityId);
        assertEquals(0, child2.children.size());

        // Assert child3: the id should match and it should not have any children
        SomeRecursiveEntity child3 = child1.children.get(1);
        assertNotNull(child3);
        assertEquals(childId3, child3.entityId);
        assertEquals(0, child3.children.size());

        // Publish an event that is picked up by child0. It should have 2 children afterwards.
        inspector.publish(asEventMessage(childId0), target);
        assertEquals(2, child0.children.size());

        // Assert child4: the id should match and it should not have any children
        SomeRecursiveEntity child4 = child0.children.get(1);
        assertNotNull(child4);
        assertEquals(childId4, child4.entityId);
        assertEquals(0, child4.children.size());
    }

    @Test
    public void testEventIsPublishedThroughoutHierarchy() throws Exception {
        AggregateModel<SomeSubclass> inspector = ModelInspector.inspectAggregate(SomeSubclass.class);

        CommandMessage<?> message = asCommandMessage("sub");
        AtomicLong payload = new AtomicLong();

        inspector.publish(new GenericEventMessage<>(payload), new SomeSubclass());

        assertEquals(2L, payload.get());
    }

    @Test
    public void testExpectCommandToBeForwardedToEntity() throws Exception {
        AggregateModel<SomeSubclass> inspector = ModelInspector.inspectAggregate(SomeSubclass.class);
        GenericCommandMessage<?> message = new GenericCommandMessage<>(BigDecimal.ONE);
        SomeSubclass target = new SomeSubclass();
        MessageHandlingMember<? super SomeSubclass> handler = inspector.commandHandler(message.getCommandName());
        assertEquals("1", handler.handle(message, target));

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1_000_000; i++) {
            inspector.commandHandler(message.getCommandName()).handle(message, target);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time taken: " + (endTime - startTime) + "ms");
    }

    @Test
    public void testFindIdentifier() throws Exception {
        AggregateModel<SomeAnnotatedHandlers> inspector = ModelInspector.inspectAggregate(SomeAnnotatedHandlers.class);

        assertEquals("SomeAnnotatedHandlers", inspector.type());
        assertEquals("id", inspector.getIdentifier(new SomeAnnotatedHandlers()));
        assertEquals("id", inspector.routingKey());
    }

    @Test
    public void testFindJavaxPersistenceIdentifier() throws Exception {
        AggregateModel<JavaxPersistenceAnnotatedHandlers> inspector = ModelInspector.inspectAggregate(JavaxPersistenceAnnotatedHandlers.class);

        assertEquals("id", inspector.getIdentifier(new JavaxPersistenceAnnotatedHandlers()));
        assertEquals("id", inspector.routingKey());
    }

    @Test
    public void testFindIdentifierInSuperClass() throws Exception {
        AggregateModel<SomeSubclass> inspector = ModelInspector.inspectAggregate(SomeSubclass.class);

        assertEquals("SomeOtherName", inspector.type());
        assertEquals("id", inspector.getIdentifier(new SomeSubclass()));
    }

    private static class JavaxPersistenceAnnotatedHandlers {

        @Id
        private String id = "id";

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    private static class SomeAnnotatedHandlers {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @AggregateRoot(type = "SomeOtherName")
    private static class SomeSubclass extends SomeAnnotatedHandlers {

        @AggregateMember
        private SomeOtherEntity entity = new SomeOtherEntity();

        @MyCustomCommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @MyCustomEventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }

    }

    private static class SomeOtherEntity {

        @CommandHandler
        public String handle(BigDecimal cmd) {
            return cmd.toPlainString();
        }

        @EventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    private static class SomeRecursiveRoot {

        private String rootId = "root";

        @AggregateMember
        private SomeRecursiveEntity entity = new SomeRecursiveEntity(SomeRecursiveEntity.childId(rootId, 1));
    }

    private static class SomeRecursiveEntity {

        private final String entityId;

        @AggregateMember
        private List<SomeRecursiveEntity> children = new ArrayList<>();

        public SomeRecursiveEntity(String entityId) {
            this.entityId = entityId;
        }

        public static String childId(String parent, int number) {
            return String.format("child #%d of %s", number, parent);
        }

        @EventHandler
        public void handle(String targetEntityId) {
            if (Objects.equals(entityId, targetEntityId)) {
                children.add(new SomeRecursiveEntity(childId(this.entityId, children.size() + 1)));
            }
        }
    }

    @Documented
    @EventHandler
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomEventHandler {

    }

    @Documented
    @CommandHandler
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomCommandHandler {

    }
}
