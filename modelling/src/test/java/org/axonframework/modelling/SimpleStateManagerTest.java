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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class SimpleStateManagerTest {

    private final AsyncRepository<String, Integer> repository = new SimpleRepository<>(
            String.class,
            Integer.class,
            (id, context) -> CompletableFuture.completedFuture(Integer.parseInt(id)),
            (id, entity, context) -> CompletableFuture.completedFuture(null)
    );

    @Test
    void registerAllowsEntitiesToBeLoadedFromTheRepository() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when
        var state = stateManager.loadEntity(Integer.class, "42", new StubProcessingContext()).join();

        // then
        assertEquals(42, state);
    }

    @Test
    void throwsExceptionOnMissingModelDefinition() {
        // given
        StateManager testSubject = SimpleStateManager.builder("test").build();

        // when & then
        var exception = assertThrows(CompletionException.class,
                                     () -> testSubject.loadEntity(Integer.class, "42", ProcessingContext.NONE).join());
        assertInstanceOf(MissingRepositoryException.class, exception.getCause());
    }

    @Test
    void throwsExceptionOnMismatchingIdType() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when & then
        var exception = assertThrows(CompletionException.class,
                                     () -> stateManager.loadEntity(Integer.class, 0.f, ProcessingContext.NONE).join());
        assertInstanceOf(IdTypeMismatchException.class, exception.getCause());
    }

    @Test
    void canRegisterEachModelClassOnlyOnce() {
        // given
        SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                               .register(repository);

        // when & then
        assertThrows(RepositoryAlreadyRegisteredException.class, () -> builder.register(repository));
    }

    @Test
    void canRetrieveRegisteredEntities() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when
        var registeredTypes = stateManager.registeredEntities();

        // then
        assertEquals(1, registeredTypes.size());
        assertTrue(registeredTypes.contains(Integer.class));
    }

    @Test
    void canGetRepositoryForRegisteredType() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when
        var result = stateManager.repository(Integer.class, String.class);

        // then
        assertEquals(repository, result);
    }

    @Test
    void throwsExceptionIfTryingToGetRepositoryForUnregisteredType() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when & then
        assertThrows(MissingRepositoryException.class, () -> stateManager.repository(Integer.class, String.class));
    }
}