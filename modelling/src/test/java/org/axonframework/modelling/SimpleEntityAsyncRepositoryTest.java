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

import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SimpleEntityAsyncRepositoryTest {

    private final AtomicInteger loadedCounter = new AtomicInteger();
    private final AtomicInteger persistCounter = new AtomicInteger();
    private final AtomicReference<Integer> savedEntity = new AtomicReference<>();
    private final SimpleEntityAsyncRepository<String, Integer> testSubject = new SimpleEntityAsyncRepository<>(
            String.class,
            Integer.class,
            (id, context) -> {
                loadedCounter.incrementAndGet();
                return CompletableFuture.completedFuture(Integer.parseInt(id));
            },
            (id, entity, context) -> {
                persistCounter.incrementAndGet();
                savedEntity.set(entity);
                return CompletableFuture.completedFuture(null);
            }
    );

    @Test
    void loadedEntityPersistsOnCommit() throws ExecutionException, InterruptedException, TimeoutException {
        new AsyncUnitOfWork().executeWithResult(ctx -> {
            ManagedEntity<String, Integer> entity = testSubject.load("42", ctx).join();
            assertEquals(42, entity.entity());

            // We change the state a few times
            entity.applyStateChange(i -> i + 1);
            entity.applyStateChange(i -> i + 1);
            entity.applyStateChange(i -> i + 1);

            // But, it's not saved yet
            assertNull(savedEntity.get());
            assertEquals(0, persistCounter.get());

            // Now, after the Unit of Work is committed, the entity should be saved
            ctx.onCommit(ctx2 -> {
                assertEquals(42 + 3, savedEntity.get());
                assertEquals(1, persistCounter.get());
                return CompletableFuture.completedFuture(null);
            });

            return CompletableFuture.completedFuture(null);
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void canAttachAnotherEntityToContext() throws ExecutionException, InterruptedException, TimeoutException {
        ManagedEntity<String, Integer> managedEntity = Mockito.mock(ManagedEntity.class);
        Mockito.when(managedEntity.identifier()).thenReturn("42");
        Mockito.when(managedEntity.entity()).thenReturn(42);

        new AsyncUnitOfWork().executeWithResult(ctx -> {
            ManagedEntity<String, Integer> entity = testSubject.attach(managedEntity, ctx);
            assertEquals(42, entity.entity());
            assertEquals("42", entity.identifier());

            // Now, after the Unit of Work is committed, the entity should be saved
            ctx.onCommit(ctx2 -> {
                assertEquals(42, savedEntity.get());
                assertEquals(1, persistCounter.get());
                return CompletableFuture.completedFuture(null);
            });

            return CompletableFuture.completedFuture(null);
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void queuesPersistForCommitOnPersistNonLoadedEntity()
            throws ExecutionException, InterruptedException, TimeoutException {
        new AsyncUnitOfWork().executeWithResult(ctx -> {
            ManagedEntity<String, Integer> entity = testSubject.persist("42", 42, ctx);
            assertEquals(42, entity.entity());

            // But, it's not saved yet
            assertNull(savedEntity.get());
            assertEquals(0, persistCounter.get());

            // Now, after the Unit of Work is committed, the entity should be saved
            ctx.onCommit(ctx2 -> {
                assertEquals(42, savedEntity.get());
                assertEquals(1, persistCounter.get());
                return CompletableFuture.completedFuture(null);
            });

            return CompletableFuture.completedFuture(null);
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void updatesKnownEntityWhenLoadedAndPersistedWithOtherValue()
            throws ExecutionException, InterruptedException, TimeoutException {
        new AsyncUnitOfWork().executeWithResult(ctx -> {
            ManagedEntity<String, Integer> entity = testSubject.load("42", ctx).join();
            assertEquals(42, entity.entity());

            // Now we persist another value
            ManagedEntity<String, Integer> updatedEntity = testSubject.persist("42", 43, ctx);
            assertEquals(43, updatedEntity.entity());

            // But, it's not saved yet
            assertNull(savedEntity.get());
            assertEquals(0, persistCounter.get());

            // Now, after the Unit of Work is committed, the entity should be saved
            ctx.onCommit(ctx2 -> {
                assertEquals(43, savedEntity.get());
                assertEquals(1, persistCounter.get());
                return CompletableFuture.completedFuture(null);
            });

            return CompletableFuture.completedFuture(null);
        }).get(5, TimeUnit.SECONDS);
    }
}