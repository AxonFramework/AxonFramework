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

package org.axonframework.modelling.command;

import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.SimpleStateManager;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the functionality of the {@link SimpleCommandHandlingComponent} with command handlers that use
 * {@link StateManager}.
 */
class StatefulCommandHandlingComponentTest {

    private final StateManager stateManager = SimpleStateManager
            .named("test")
            .register(String.class, Integer.class,
                      (id, ctx) -> CompletableFuture.completedFuture(Integer.parseInt(id)),
                      (id, entity, context) -> CompletableFuture.completedFuture(null));


    @Test
    void invokedRegisteredHandler() {
        SimpleCommandHandlingComponent testSubject = SimpleCommandHandlingComponent.create("test");

        AtomicBoolean invoked = new AtomicBoolean();
        testSubject.subscribe(new QualifiedName("test-command"), (command, ctx) -> {
            var state = ctx.component(StateManager.class);
            state.loadEntity(Integer.class, "42", ctx).thenAccept(result -> {
                assertEquals(42, result);
            }).join();
            invoked.set(true);
            return MessageStream.empty().cast();
        });

        GenericCommandMessage command = new GenericCommandMessage(new MessageType("test-command"),
                                                                            "my-payload");
        testSubject.handle(command, messageProcessingContext(command)).asCompletableFuture().join();
        assertTrue(invoked.get());
    }

    @Test
    void canRegisterNonStatefulNormalHandler() {
        SimpleCommandHandlingComponent testSubject = SimpleCommandHandlingComponent.create("test");
        AtomicBoolean invoked = new AtomicBoolean();
        testSubject.subscribe(new QualifiedName("test-command"), (command, ctx) -> {
            invoked.set(true);
            return MessageStream.empty().cast();
        });

        GenericCommandMessage command = new GenericCommandMessage(new MessageType("test-command"),
                                                                            "my-payload");
        testSubject.handle(command, messageProcessingContext(command))
                   .asCompletableFuture().join();
        assertTrue(invoked.get());
    }

    @Test
    void registeredHandlersAreListedInSupportedCommands() {
        SimpleCommandHandlingComponent testSubject = SimpleCommandHandlingComponent.create("test");
        testSubject.subscribe(new QualifiedName("test-command"),
                              (command, ctx) -> MessageStream.empty().cast());
        testSubject.subscribe(new QualifiedName("test-command-2"), (command, ctx) -> MessageStream.empty().cast());
        Set<QualifiedName> supportedCommands = testSubject.supportedCommands();
        assertEquals(2, supportedCommands.size());
        assertTrue(supportedCommands.contains(new QualifiedName("test-command")));
        assertTrue(supportedCommands.contains(new QualifiedName("test-command-2")));
    }

    @Test
    void exceptionWhileHandlingCommandResultsInFailedStream() {
        SimpleCommandHandlingComponent testSubject = SimpleCommandHandlingComponent.create("test");
        testSubject.subscribe(new QualifiedName("test-command"), (command, ctx) -> {
            throw new RuntimeException("Faking an exception");
        });

        CompletionException exception = assertThrows(CompletionException.class, () -> {
            GenericCommandMessage command = new GenericCommandMessage(new MessageType("test-command"),
                                                                                "my-payload");
            testSubject.handle(command, StubProcessingContext.forMessage(command))
                       .asCompletableFuture()
                       .join();
        });

        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Faking an exception", exception.getCause().getMessage());
    }

    private ProcessingContext messageProcessingContext(GenericCommandMessage command) {
        return StubProcessingContext.withComponent(StateManager.class, stateManager).withMessage(command);
    }
}