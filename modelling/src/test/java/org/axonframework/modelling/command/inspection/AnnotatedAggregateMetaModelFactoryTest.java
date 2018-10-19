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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.junit.Test;

import javax.persistence.Id;
import java.lang.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

public class AnnotatedAggregateMetaModelFactoryTest {

    @Test
    public void testDetectAllAnnotatedHandlers() throws Exception {
        AggregateModel<SomeAnnotatedHandlers> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeAnnotatedHandlers.class);

        CommandMessage<?> message = asCommandMessage("ok");
        assertEquals(true, getHandler(inspector, message).handle(message, new SomeAnnotatedHandlers()));
        assertEquals(false, getHandler(inspector, message).handle(asCommandMessage("ko"), new SomeAnnotatedHandlers()));
    }

    @Test
    public void testDetectAllAnnotatedHandlersInHierarchy() throws Exception {
        AggregateModel<SomeSubclass> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        SomeSubclass target = new SomeSubclass();
        CommandMessage<?> message = asCommandMessage("sub");
        assertEquals(true, getHandler(inspector, message).handle(message, target));
        assertEquals(false, getHandler(inspector, message).handle(asCommandMessage("ok"), target));
    }

    @Test
    public void testDetectFactoryMethodHandler() {
        AggregateModel<SomeAnnotatedFactoryMethodClass> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeAnnotatedFactoryMethodClass.class);
        CommandMessage<?> message = asCommandMessage("string");
        final MessageHandlingMember<? super SomeAnnotatedFactoryMethodClass> messageHandlingMember = getHandler(inspector, message);
        final Optional<CommandMessageHandlingMember> unwrap = messageHandlingMember.unwrap(CommandMessageHandlingMember.class);
        assertThat(unwrap, notNullValue());
        assertThat(unwrap.isPresent(), is(true));
        final CommandMessageHandlingMember commandMessageHandlingMember = unwrap.get();
        assertThat(commandMessageHandlingMember.isFactoryHandler(), is(true));
    }


    @Test
    public void testEventIsPublishedThroughoutRecursiveHierarchy() {
        // Note that if the inspector does not support recursive entities this will throw an StackOverflowError.
        AggregateModel<SomeRecursiveEntity> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeRecursiveEntity.class);

        // Create a hierarchy that we will use in this test.
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

        SomeRecursiveEntity root = new SomeRecursiveEntity(LinkedList::new, null, rootId);

        // Assert root: it should not have any children (yet)
        assertEquals(0, root.children.size());

        // Publish an event that is picked up by root. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(rootId, childId1)), root);
        assertEquals(1, root.children.size());

        // Assert child1: it should not have any children (yet)
        SomeRecursiveEntity child1 = root.getChild(childId1);
        assertNotNull(child1);
        assertEquals(0, child1.children.size());

        // Publish 2 events that are picked up by child1. It should have 2 children afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId1, childId2)), root);
        inspector.publish(asEventMessage(new CreateChild(childId1, childId3)), root);
        assertEquals(2, child1.children.size());

        // Assert child2: it should not have any children
        SomeRecursiveEntity child2 = child1.getChild(childId2);
        assertNotNull(child2);
        assertEquals(0, child2.children.size());

        // Assert child3: it should not have any children
        SomeRecursiveEntity child3 = child1.getChild(childId3);
        assertNotNull(child3);
        assertEquals(0, child3.children.size());

        // Publish an event that is picked up by child3. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId3, childId4)), root);
        assertEquals(1, child3.children.size());

        // Assert child4: it should not have any children
        SomeRecursiveEntity child4 = child3.getChild(childId4);
        assertNotNull(child4);
        assertEquals(0, child4.children.size());
    }

    @Test
    public void testLinkedListIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(LinkedList::new);
    }

    @Test
    public void testHashSetIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(HashSet::new);
    }

    @Test
    public void testCopyOnWriteArrayListIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(CopyOnWriteArrayList::new);
    }

    @Test
    public void testConcurrentLinkedQueueIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(ConcurrentLinkedQueue::new);
    }

    private void testCollectionIsModifiedDuringIterationInRecursiveHierarchy(Supplier<Collection<SomeRecursiveEntity>> supplier) {
        // Note that if the inspector does not support recursive entities this will throw an StackOverflowError.
        AggregateModel<SomeRecursiveEntity> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeRecursiveEntity.class);

        // Create a hierarchy that we will use in this test.
        // The resulting hierarchy will look as follows:
        // root
        //      child1
        //              child2
        //                      child3
        String rootId = "root";
        String childId1 = "child1";
        String childId2 = "child2";
        String childId3 = "child3";

        SomeRecursiveEntity root = new SomeRecursiveEntity(supplier, null, rootId);
        inspector.publish(asEventMessage(new CreateChild(rootId, childId1)), root);
        inspector.publish(asEventMessage(new CreateChild(childId1, childId2)), root);
        inspector.publish(asEventMessage(new CreateChild(childId2, childId3)), root);
        SomeRecursiveEntity child1 = root.getChild(childId1);

        // Assert child1: it should have 1 child
        assertEquals(1, child1.children.size());

        // Now move child3 up one level so it is a child of child1.
        // The resulting hierarchy will look as follows:
        // root
        //      child1
        //              child2
        //              child3

        // Note that if the inspector does not use copy-iterators this will throw an ConcurrentModificationException.
        inspector.publish(asEventMessage(new MoveChildUp(childId2, childId3)), root);
        assertEquals(2, child1.children.size());
    }

    @Test
    public void testEventIsPublishedThroughoutHierarchy() {
        AggregateModel<SomeSubclass> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        AtomicLong payload = new AtomicLong();

        inspector.publish(new GenericEventMessage<>(payload), new SomeSubclass());

        assertEquals(2L, payload.get());
    }

    @Test
    public void testExpectCommandToBeForwardedToEntity() throws Exception {
        AggregateModel<SomeSubclass> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);
        GenericCommandMessage<?> message = new GenericCommandMessage<>(BigDecimal.ONE);
        SomeSubclass target = new SomeSubclass();
        MessageHandlingMember<? super SomeSubclass> handler = getHandler(inspector, message);
        assertEquals("1", handler.handle(message, target));
    }

    @Test
    public void testFindIdentifier() {
        AggregateModel<SomeAnnotatedHandlers> inspector = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(SomeAnnotatedHandlers.class);

        assertEquals("SomeAnnotatedHandlers", inspector.type());
        assertEquals("id", inspector.getIdentifier(new SomeAnnotatedHandlers()));
        assertEquals("id", inspector.routingKey());
    }

    @Test
    public void testFindJavaxPersistenceIdentifier() {
        AggregateModel<JavaxPersistenceAnnotatedHandlers> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(JavaxPersistenceAnnotatedHandlers.class);

        assertEquals("id", inspector.getIdentifier(new JavaxPersistenceAnnotatedHandlers()));
        assertEquals("id", inspector.routingKey());
    }

    @Test
    public void testFindIdentifierInSuperClass() {
        AggregateModel<SomeSubclass> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        assertEquals("SomeOtherName", inspector.type());
        assertEquals("id", inspector.getIdentifier(new SomeSubclass()));
    }

    @Test(expected = AxonConfigurationException.class)
    public void testIllegalFactoryMethodThrowsExceptionClass() {
        AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeIllegalAnnotatedFactoryMethodClass.class);
    }

    @Test(expected = AxonConfigurationException.class)
    public void typedAggregateIdentifier() {
        AggregateModel<TypedIdentifierAggregate> inspector = AnnotatedAggregateMetaModelFactory.inspectAggregate(TypedIdentifierAggregate.class);

        assertNotNull(inspector.getIdentifier(new TypedIdentifierAggregate()));
    }

    @SuppressWarnings("unchecked")
    private <T> MessageHandlingMember<T> getHandler(AggregateModel<?> members, CommandMessage<?> message) {
        return (MessageHandlingMember<T>) members.commandHandlers().stream().filter(ch -> ch.canHandle(message)).findFirst().orElseThrow(() -> new AssertionError("Expected handler for this message"));
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

    private static class CustomIdentifier {
    }

    @AggregateRoot
    private static class TypedIdentifierAggregate {

        @AggregateIdentifier
        private CustomIdentifier aggregateIdentifier = new CustomIdentifier();

        @CommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @EventHandler
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

        private final Supplier<Collection<SomeRecursiveEntity>> supplier;
        private final SomeRecursiveEntity parent;
        private final String entityId;

        @AggregateMember
        private final SomeIterable<SomeRecursiveEntity> children;

        public SomeRecursiveEntity(Supplier<Collection<SomeRecursiveEntity>> supplier, SomeRecursiveEntity parent, String entityId) {
            this.supplier = supplier;
            this.parent = parent;
            this.entityId = entityId;
            this.children = new SomeIterable<>(supplier.get());
        }

        public SomeRecursiveEntity getChild(String childId) {
            for (SomeRecursiveEntity c : children) {
                if (Objects.equals(c.entityId, childId)) {
                    return c;
                }
            }
            return null;
        }

        @EventHandler
        public void handle(CreateChild event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                children.add(new SomeRecursiveEntity(supplier, this, event.childId));
            }
        }

        @EventHandler
        public void handle(MoveChildUp event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                SomeRecursiveEntity child = getChild(event.childId);
                assertNotNull(child);
                assertTrue(this.children.remove(child));
                assertTrue(parent.children.add(child));
            }
        }
    }

    public static class SomeAnnotatedFactoryMethodClass {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler
        public static SomeAnnotatedFactoryMethodClass factoryMethod(String id) {
            return new SomeAnnotatedFactoryMethodClass(id);
        }

        SomeAnnotatedFactoryMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class SomeIllegalAnnotatedFactoryMethodClass {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler
        public static Object illegalFactoryMethod(String id) {
            return new SomeAnnotatedFactoryMethodClass(id);
        }

        SomeIllegalAnnotatedFactoryMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    /**
     * Wrapper implementation to ensure that the @AggregateMember field is solely triggered by the fact it implements
     * Iterable, and doesn't depend on any other interface being declared. See issue #461.
     *
     * @param <T> The type contained in this iterable
     */
    private static class SomeIterable<T> implements Iterable<T> {

        private final Collection<T> contents;

        public SomeIterable(Collection<T> contents) {
            this.contents = contents;
        }

        @Override
        public Iterator<T> iterator() {
            return contents.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            contents.forEach(action);
        }

        @Override
        public Spliterator<T> spliterator() {
            return contents.spliterator();
        }

        public boolean add(T item) {
            return contents.add(item);
        }

        public boolean remove(T item) {
            return contents.remove(item);
        }

        public int size() {
            return contents.size();
        }
    }
}
