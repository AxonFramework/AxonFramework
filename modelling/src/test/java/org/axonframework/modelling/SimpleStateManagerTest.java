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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.repository.AsyncRepository;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class SimpleStateManagerTest {

    private final Map<String, Integer> persistedValues = new HashMap<>();
    private final AsyncRepository<String, Integer> repository = new SimpleEntityAsyncRepository<>(
            String.class,
            Integer.class,
            (id, context) -> CompletableFuture.completedFuture(Integer.parseInt(id)),
            (id, entity, context) -> {
                persistedValues.put(id, entity);
                return CompletableFuture.completedFuture(null);
            }
    );

    @Test
    void registerAllowsEntitiesToBeLoadedFromTheRepository() {
        // Given
        StateManager testSubject = SimpleStateManager.create("test");
        testSubject.register(
                String.class,
                Integer.class,
                repository
        );

        // When
        var state = testSubject.loadEntity(Integer.class, "42", new StubProcessingContext()).join();

        // Then
        assertEquals(42, state);
    }

    @Test
    void throwsExceptionOnMissingModelDefinition() {
        // Given
        StateManager testSubject = SimpleStateManager.create("test");

        // When & Then
        var exception = assertThrows(CompletionException.class,
                                     () -> testSubject.loadEntity(Integer.class, "42", ProcessingContext.NONE).join());
        assertInstanceOf(MissingRepositoryException.class, exception.getCause());
    }

    @Test
    void canRegisterEachModelClassOnlyOnce() {
        // Given
        StateManager testSubject = SimpleStateManager.create("test");
        testSubject.register(
                String.class,
                Integer.class,
                repository
        );

        // When & Then
        assertThrows(StateTypeAlreadyRegisteredException.class, () -> testSubject.register(
                String.class,
                Integer.class,
                repository
        ));
    }
}