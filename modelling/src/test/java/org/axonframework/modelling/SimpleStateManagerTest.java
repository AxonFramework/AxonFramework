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
        assertThrows(MissingRepositoryException.class,
                     () -> testSubject.loadEntity(Integer.class, "42", ProcessingContext.NONE).join());
    }

    @Test
    void canRegisterEachModelClassOnlyOnce() {
        // given
        SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                               .register(repository);

        // when & then
        assertThrows(ConflictingRepositoryAlreadyRegisteredException.class, () -> builder.register(repository));
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
    void returnsNullIfTryingToGetRepositoryForUnregisteredType() {
        // given
        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .build();

        // when & then
        assertNull(stateManager.repository(String.class, Integer.class));
    }


    @Test
    void returnsAllRegisteredIdentifiersForAnEntity() {
        // given
        SimpleRepository<String, MyFirstImplementingClass> repository = new SimpleRepository<>(
                String.class,
                MyFirstImplementingClass.class,
                (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                (id, entity, context) -> CompletableFuture.completedFuture(null)
        );

        SimpleRepository<Integer, MyFirstImplementingClass> repository2 = new SimpleRepository<>(
                Integer.class,
                MyFirstImplementingClass.class,
                (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                (id, entity, context) -> CompletableFuture.completedFuture(null)
        );

        SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                            .register(repository)
                                                            .register(repository2)
                                                            .build();

        // when
        var result = stateManager.registeredIdsFor(MyFirstImplementingClass.class);

        // then
        assertEquals(2, result.size());
        assertTrue(result.contains(String.class));
        assertTrue(result.contains(Integer.class));
    }

    @Nested
    class PolymorphicRepositories {

        @Test
        void canRequestMoreSpecificEntityThanDefinedInRepositoryIfTypeMatches() {
            // given
            SimpleRepository<String, MySuperClass> repository = new SimpleRepository<>(
                    String.class,
                    MySuperClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );
            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .build();

            // when
            var result = stateManager.loadManagedEntity(MyFirstImplementingClass.class,
                                                        "42",
                                                        new StubProcessingContext()).join();

            // then
            assertNotNull(result.entity());
        }

        @Test
        void canNotRequestLessSpecificEntityThanDefinedInRepository() {
            // given
            SimpleRepository<String, MyFirstImplementingClass> repository = new SimpleRepository<>(
                    String.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );
            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .build();

            // when & then
            assertThrows(MissingRepositoryException.class,
                         () -> stateManager.loadManagedEntity(MySuperClass.class, "42", new StubProcessingContext())
                                           .join());
        }

        @Test
        void canRequestAnEntityWithAMoreSpecificIdentifierThanDefinedByRepository() {
            // given
            SimpleRepository<MySuperClass, String> repository = new SimpleRepository<>(
                    MySuperClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("42"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );
            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .build();

            // when
            var result = stateManager.loadManagedEntity(String.class,
                                                        new MyFirstImplementingClass(),
                                                        new StubProcessingContext()).join();

            // then
            assertNotNull(result.entity());
        }

        @Test
        void canNotRequestAnEntityWithALessSpecificIdentifierThanDefinedByRepository() {
            // given
            SimpleRepository<MySecondImplementingClass, String> repository = new SimpleRepository<>(
                    MySecondImplementingClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("something"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null));
            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .build();

            // when & then
            assertThrows(MissingRepositoryException.class,
                         () -> stateManager.loadManagedEntity(String.class,
                                                              new MySuperClass(),
                                                              new StubProcessingContext()).join());
        }

        @Test
        void canRegisterTwoRepositoriesWithSameSuperClass() {
            // given
            SimpleRepository<String, MyFirstImplementingClass> repository = new SimpleRepository<>(
                    String.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleRepository<String, MySecondImplementingClass> repository2 = new SimpleRepository<>(
                    String.class,
                    MySecondImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MySecondImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .register(repository2)
                                                                .build();

            // when
            var result = stateManager.loadManagedEntity(MyFirstImplementingClass.class,
                                                        "42",
                                                        new StubProcessingContext()).join();
            var result2 = stateManager.loadManagedEntity(MySecondImplementingClass.class,
                                                         "42",
                                                         new StubProcessingContext()).join();

            // then
            assertNotNull(result.entity());
            assertNotNull(result2.entity());
        }

        @Test
        void requestingASpecificClassWhileLoaderReturnsSuperclassShouldFail() {
            // given
            SimpleRepository<String, MySuperClass> repository = new SimpleRepository<>(
                    String.class,
                    MySuperClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MySuperClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );
            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .build();

            // when & then
            var exception = assertThrows(CompletionException.class, () -> stateManager.loadManagedEntity(
                    MyFirstImplementingClass.class,
                    "42",
                    new StubProcessingContext()).join());
            assertInstanceOf(LoadedEntityNotOfExpectedTypeException.class, exception.getCause());
        }

        @Test
        void canNotRegisterSubclassOfAlreadyRegisteredEntity() {
            // given
            SimpleRepository<String, MySuperClass> repository = new SimpleRepository<>(
                    String.class,
                    MySuperClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MySuperClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleRepository<String, MyFirstImplementingClass> repository2 = new SimpleRepository<>(
                    String.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                                   .register(repository);

            // when & then
            assertThrows(ConflictingRepositoryAlreadyRegisteredException.class, () -> builder.register(repository2));
        }


        @Test
        void canNotRegisterSuperclassOfAlreadyRegisteredEntity() {
            // given
            SimpleRepository<String, MyFirstImplementingClass> repository = new SimpleRepository<>(
                    String.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );
            SimpleRepository<String, MySuperClass> repository2 = new SimpleRepository<>(
                    String.class,
                    MySuperClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MySuperClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );


            SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                                   .register(repository);

            // when & then
            assertThrows(ConflictingRepositoryAlreadyRegisteredException.class, () -> builder.register(repository2));
        }

        @Test
        void canRegisterAndLoadSameEntitywithDifferentIdentifierClasses() {
            // given
            SimpleRepository<String, MyFirstImplementingClass> repository = new SimpleRepository<>(
                    String.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleRepository<Integer, MyFirstImplementingClass> repository2 = new SimpleRepository<>(
                    Integer.class,
                    MyFirstImplementingClass.class,
                    (id, context) -> CompletableFuture.completedFuture(new MyFirstImplementingClass()),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleStateManager stateManager = SimpleStateManager.builder("test")
                                                                .register(repository)
                                                                .register(repository2)
                                                                .build();

            // when
            var result = stateManager.loadManagedEntity(MyFirstImplementingClass.class,
                                                        "42",
                                                        new StubProcessingContext()).join();
            var result2 = stateManager.loadManagedEntity(MyFirstImplementingClass.class,
                                                         42,
                                                         new StubProcessingContext()).join();

            // then
            assertNotNull(result.entity());
            assertNotNull(result2.entity());
        }

        @Test
        void canNotRegisterSecondIdClassForSameEntityTypeWhichIsASuperClassOfFirstIdClass() {
            // given
            SimpleRepository<MyFirstImplementingClass, String> repository = new SimpleRepository<>(
                    MyFirstImplementingClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("42"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleRepository<MySuperClass, String> repository2 = new SimpleRepository<>(
                    MySuperClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("42"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                                   .register(repository);

            // when & then
            assertThrows(ConflictingRepositoryAlreadyRegisteredException.class, () -> builder.register(repository2));
        }


        @Test
        void canNotRegisterSecondIdClassForSameEntityTypeWhichIsASubclassOfFirstIdClass() {
            // given
            SimpleRepository<MySuperClass, String> repository = new SimpleRepository<>(
                    MySuperClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("42"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleRepository<MyFirstImplementingClass, String> repository2 = new SimpleRepository<>(
                    MyFirstImplementingClass.class,
                    String.class,
                    (id, context) -> CompletableFuture.completedFuture("42"),
                    (id, entity, context) -> CompletableFuture.completedFuture(null)
            );

            SimpleStateManager.Builder builder = SimpleStateManager.builder("test")
                                                                   .register(repository);

            // when & then
            assertThrows(ConflictingRepositoryAlreadyRegisteredException.class, () -> builder.register(repository2));
        }
    }


    class MySuperClass {

    }

    class MyFirstImplementingClass extends MySuperClass {

    }

    class MySecondImplementingClass extends MySuperClass {

    }
}