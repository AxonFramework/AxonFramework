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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

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
        // Note that if the model does not support recursive entities this will throw an StackOverflowError.
        AggregateModel<SomeRecursiveEntity> inspector = ModelInspector.inspectAggregate(SomeRecursiveEntity.class);

        // Create a hierarchy of id's that we will use in this test.
        // The resulting hierarchy will look as follows:
        // root 
        //      child1
        //              child2
        //              child3
        //                      child4
        String rootId = "root";
        String childId1 = "child1";
        String childId2 = "child2";
        String childId3 = "child3";
        String childId4 = "child4";

        SomeRecursiveEntity root = new SomeRecursiveEntity(null, "root");

        // Assert root: it should not have any children (yet)
        assertEquals(0, root.children.size());

        // Publish an event that is picked up by root. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(rootId, childId1)), root);
        assertEquals(1, root.children.size());

        // Assert child1: it should not have any children (yet)
        SomeRecursiveEntity child1 = root.children.get(childId1);
        assertNotNull(child1);
        assertEquals(0, child1.children.size());

        // Publish 2 events that are picked up by child1. It should have 2 children afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId1, childId2)), root);
        inspector.publish(asEventMessage(new CreateChild(childId1, childId3)), root);
        assertEquals(2, child1.children.size());

        // Assert child2: it should not have any children
        SomeRecursiveEntity child2 = child1.children.get(childId2);
        assertNotNull(child2);
        assertEquals(0, child2.children.size());

        // Assert child3: it should not have any children
        SomeRecursiveEntity child3 = child1.children.get(childId3);
        assertNotNull(child3);
        assertEquals(0, child3.children.size());

        // Publish an event that is picked up by child3. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId3, childId4)), root);
        assertEquals(1, child3.children.size());

        // Assert child4: it should not have any children
        SomeRecursiveEntity child4 = child3.children.get(childId4);
        assertNotNull(child4);
        assertEquals(0, child4.children.size());

        // Now move child4 up one level so it is a child of root.
        // The resulting hierarchy will look as follows:
        // root 
        //      child1
        //              child2
        //              child3
        //              child4
        
        // Publish an event that is picked up by child3. 
        // It should have 0 children afterwards and child1 should have 3 children afterwards.
        // Note that if the model does not use copy-iterators this will throw an ConcurrentModificationException.
        inspector.publish(asEventMessage(new MoveChildUp(childId3, childId4)), root);
        assertEquals(0, child3.children.size());
        assertEquals(3, child1.children.size());
        
        // Assert that child4 is no longer part of child3 but of child1 
        assertNull(child3.children.get(childId4));
        assertNotNull(child1.children.get(childId4));
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
    
    private static class CreateChild {
        private final String parentId;
        private final String childId;

        public CreateChild(String parentId, String childId) {
            this.parentId = parentId;
            this.childId = childId;
        }
    }

    private static class MoveChildUp {
        private final String parentId;
        private final String childId;

        private MoveChildUp(String parentId, String childId) {
            this.parentId = parentId;
            this.childId = childId;
        }
    }

    private static class SomeRecursiveEntity {

        private final SomeRecursiveEntity parent;
        private final String entityId;

        @AggregateMember
        private Map<String, SomeRecursiveEntity> children = new ConcurrentHashMap<>();

        public SomeRecursiveEntity(SomeRecursiveEntity parent, String entityId) {
            this.parent = parent;
            this.entityId = entityId;
        }

        @EventHandler
        public void handle(CreateChild event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                children.put(event.childId, new SomeRecursiveEntity(this, event.childId));
            }
        }

        @EventHandler
        public void handle(MoveChildUp event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                SomeRecursiveEntity child = this.children.remove(event.childId);
                parent.children.put(child.entityId, child);
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
