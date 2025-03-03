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

package org.axonframework.modelling;

import org.axonframework.messaging.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleModelRegistryTest {

    @Test
    void registerModel() {
        // Given
        ModelRegistry testSubject = SimpleModelRegistry.create("test");
        testSubject.registerModel(
                String.class,
                Integer.class,
                (id, ctx) -> CompletableFuture.completedFuture(Integer.valueOf(id))
        );

        // When
        ModelContainer container = testSubject.modelContainer(new StubProcessingContext());

        // Then
        assertEquals(42, container.getModel(Integer.class, "42").join());
    }


    @Test
    void cachesAlreadyCreatedModel() {
        // Given
        AtomicInteger creationCount = new AtomicInteger();
        ModelRegistry testSubject = SimpleModelRegistry.create("test");
        testSubject.registerModel(
                String.class,
                Integer.class,
                (id, ctx) -> {
                    creationCount.incrementAndGet();
                    return CompletableFuture.completedFuture(Integer.valueOf(id));
                }
        );

        // When
        ModelContainer container = testSubject.modelContainer(new StubProcessingContext());

        // Then
        assertEquals(42, container.getModel(Integer.class, "42").join());
        assertEquals(42, container.getModel(Integer.class, "42").join());
        assertEquals(42, container.getModel(Integer.class, "42").join());
        assertEquals(1, creationCount.get());
    }

    @Test
    void throwsExceptionOnMissingModelDefinition() {
        // Given
        ModelRegistry testSubject = SimpleModelRegistry.create("test");
        ModelContainer container = testSubject.modelContainer(new StubProcessingContext());

        // When & Then
        var exception = assertThrows(CompletionException.class, () -> container.getModel(Integer.class, "42").join());
        assertInstanceOf(MissingModelDefinitionException.class, exception.getCause());
    }

    @Test
    void canRegisterEachModelClassOnlyOnce() {
        // Given
        ModelRegistry testSubject = SimpleModelRegistry.create("test");
        testSubject.registerModel(
                String.class,
                Integer.class,
                (id, ctx) -> CompletableFuture.completedFuture(Integer.valueOf(id))
        );

        // When & Then
        assertThrows(ModelAlreadyRegisteredException.class, () -> testSubject.registerModel(
                String.class,
                Integer.class,
                (id, ctx) -> CompletableFuture.completedFuture(Integer.valueOf(id))
        ));
    }
}