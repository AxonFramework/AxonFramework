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

package org.axonframework.common.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.function.ThrowingFunction;
import org.axonframework.common.tx.TransactionalExecutor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link TransactionalExecutor} implementation for {@link EntityManager}s.
 *
 * @author John Hendrikx
 * @since 5.1.0
 */
@Internal
public class EntityManagerExecutor implements TransactionalExecutor<EntityManager> {
    private final EntityManagerProvider provider;

    /**
     * Creates a new instance.
     *
     * @param factory An {@link EntityManagerProvider}, cannot be {@code null}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public EntityManagerExecutor(@Nonnull EntityManagerProvider factory) {
        this.provider = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public <R> CompletableFuture<R> apply(ThrowingFunction<EntityManager, R, Exception> function) {
        try {
            return CompletableFuture.completedFuture(function.apply(provider.getEntityManager()));
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
