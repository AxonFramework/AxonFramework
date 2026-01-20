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

package org.axonframework.modelling;

import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

class HierarchicalStateManagerTest {

    @Test
    void resolvesEntityFromChildIfExistsInBoth() {
        StateManager parent = createStringSimpleStateManager("parent");
        StateManager child = createStringSimpleStateManager("child");

        HierarchicalStateManager stateManager = HierarchicalStateManager.create(parent, child);

        verifyHasAsResult(stateManager, "child");
    }

    @Test
    void resolvesEntityFromParentIfDoesNotExistInChild() {
        StateManager parent = createStringSimpleStateManager("parent");
        StateManager child = SimpleStateManager.named("child");

        HierarchicalStateManager stateManager = HierarchicalStateManager.create(parent, child);

        verifyHasAsResult(stateManager, "parent");
    }

    @Test
    void resolvesEntityFromChildIfDoesNotExistInParent() {
        StateManager parent = SimpleStateManager.named("parent");
        StateManager child = createStringSimpleStateManager("child");

        HierarchicalStateManager stateManager = HierarchicalStateManager.create(parent, child);

        verifyHasAsResult(stateManager, "child");
    }

    @Test
    void throwsExceptionIfExistsInNeither() {
        StateManager parent = SimpleStateManager.named("parent");
        StateManager child = SimpleStateManager.named("child");

        HierarchicalStateManager stateManager = HierarchicalStateManager.create(parent, child);

        Assertions.assertThrows(MissingRepositoryException.class, () -> {
            stateManager.loadEntity(String.class, "id", new StubProcessingContext())
                        .join();
        });
    }

    @Test
    void combinesTypesOfBothChildAndParentInRepositoriesMethods() {
        StateManager parent = SimpleStateManager.named("parent")
                                                .register(createMockForTypes(String.class, Integer.class))
                                                .register(createMockForTypes(Integer.class, Integer.class));
        StateManager child = SimpleStateManager.named("child")
                                               .register(createMockForTypes(Boolean.class, Integer.class))
                                               .register(createMockForTypes(String.class, Boolean.class));

        HierarchicalStateManager stateManager = HierarchicalStateManager.create(parent, child);

        Set<Class<?>> classes = stateManager.registeredEntities();
        Assertions.assertEquals(3, classes.size());
        Assertions.assertTrue(classes.contains(String.class));
        Assertions.assertTrue(classes.contains(Integer.class));
        Assertions.assertTrue(classes.contains(Boolean.class));

        Set<Class<?>> stringIds = stateManager.registeredIdsFor(String.class);
        Assertions.assertEquals(2, stringIds.size());
        Assertions.assertTrue(stringIds.contains(Integer.class));
        Assertions.assertTrue(stringIds.contains(Boolean.class));

        Set<Class<?>> integerIds = stateManager.registeredIdsFor(Integer.class);
        Assertions.assertEquals(1, integerIds.size());
        Assertions.assertTrue(integerIds.contains(Integer.class));

        Set<Class<?>> booleanIds = stateManager.registeredIdsFor(Boolean.class);
        Assertions.assertEquals(1, booleanIds.size());
        Assertions.assertTrue(booleanIds.contains(Integer.class));
    }

    private Repository<?, ?> createMockForTypes(Class<?> entityType, Class<?> idType) {
        Repository mock = Mockito.mock(Repository.LifecycleManagement.class);
        Mockito.when(mock.idType()).thenReturn(idType);
        Mockito.when(mock.entityType()).thenReturn(entityType);
        return mock;
    }

    private static void verifyHasAsResult(HierarchicalStateManager stateManager, String child) {
        stateManager.loadEntity(String.class, "id", new StubProcessingContext())
                    .thenAccept(entity -> {
                        Assertions.assertEquals(child, entity);
                    })
                    .join();
    }

    private static StateManager createStringSimpleStateManager(String value) {
        return SimpleStateManager.named(value)
                                 .register(String.class,
                                           String.class,
                                           (id, ctx) -> CompletableFuture.completedFuture(value),
                                           (id, state, ctx) -> {
                                               return FutureUtils.emptyCompletedFuture();
                                           });
    }
}