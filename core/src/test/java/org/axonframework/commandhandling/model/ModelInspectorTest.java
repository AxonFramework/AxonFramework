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
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.junit.Test;

import javax.persistence.Id;
import java.lang.annotation.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertEquals;

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
        MessageHandler<? super SomeSubclass> handler = inspector.commandHandler(message.getCommandName());
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
