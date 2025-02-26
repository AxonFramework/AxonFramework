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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.utils.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StatefulCommandHandlingComponentTest {

    ModelRegistry modelRegistry = SimpleModelRegistry.create("test");

    @Test
    void invokesRegisteredHandlerWithModelContainer() {
        StatefulCommandHandlingComponent testSubject = StatefulCommandHandlingComponent.create("test", modelRegistry);
        ProcessingContext processingContext = new StubProcessingContext();
        modelRegistry.registerModel(
                String.class,
                Integer.class,
                (id, ctx) -> CompletableFuture.completedFuture(42)
        );

        AtomicBoolean invoked = new AtomicBoolean();
        testSubject.subscribe(new QualifiedName("test-command"), (command, models, ctx) -> {
            models.getModel(Integer.class, "42").thenAccept(result -> {
                assertEquals(42, result);
            }).join();
            invoked.set(true);
            return MessageStream.empty().cast();
        });

        testSubject.handle(new GenericCommandMessage<>(new MessageType(new QualifiedName("test-command")),
                                                       "my-payload"), processingContext)
                   .asCompletableFuture().join();
        assertTrue(invoked.get());
    }

    @Test
    void canRegisterNonStatefulNormalHandler() {
        StatefulCommandHandlingComponent testSubject = StatefulCommandHandlingComponent.create("test", modelRegistry);
        ProcessingContext processingContext = new StubProcessingContext();
        AtomicBoolean invoked = new AtomicBoolean();
        testSubject.subscribe(new QualifiedName("test-command"), (command, ctx) -> {
            invoked.set(true);
            return MessageStream.empty().cast();
        });

        testSubject.handle(new GenericCommandMessage<>(new MessageType(new QualifiedName("test-command")),
                                                       "my-payload"), processingContext)
                   .asCompletableFuture().join();
        assertTrue(invoked.get());
    }

    @Test
    void reigsteredHandlersAreListedInSupportedCommands() {
        StatefulCommandHandlingComponent testSubject = StatefulCommandHandlingComponent.create("test", modelRegistry);
        testSubject.subscribe(new QualifiedName("test-command"),
                              (command, models, ctx) -> MessageStream.empty().cast());
        testSubject.subscribe(new QualifiedName("test-command-2"), (command, ctx) -> MessageStream.empty().cast());
        Set<QualifiedName> supportedCommands = testSubject.supportedCommands();
        assertEquals(2, supportedCommands.size());
        assertTrue(supportedCommands.contains(new QualifiedName("test-command")));
        assertTrue(supportedCommands.contains(new QualifiedName("test-command-2")));
    }

    @Test
    void exceptionWhileHandlingCommandResultsInFailedStream() {
        StatefulCommandHandlingComponent testSubject = StatefulCommandHandlingComponent.create("test", modelRegistry);
        ProcessingContext processingContext = new StubProcessingContext();
        testSubject.subscribe(new QualifiedName("test-command"), (command, models, ctx) -> {
            throw new RuntimeException("Faking an exception");
        });

        CompletionException exception = assertThrows(CompletionException.class, () -> {
            testSubject.handle(new GenericCommandMessage<>(new MessageType(new QualifiedName("test-command")),
                                                           "my-payload"), processingContext)
                       .asCompletableFuture()
                       .join();
        });

        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Faking an exception", exception.getCause().getMessage());
    }
}