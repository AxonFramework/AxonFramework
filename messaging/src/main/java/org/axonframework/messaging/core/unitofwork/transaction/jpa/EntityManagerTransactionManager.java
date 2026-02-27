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

package org.axonframework.messaging.core.unitofwork.transaction.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityTransaction;
import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A {@link TransactionManager} implementation that manages JPA {@link EntityTransaction EntityTransactions}
 * directly via an {@link EntityManagerProvider}.
 * <p>
 * This implementation registers an {@link EntityManagerExecutor} supplier into the {@link
 * org.axonframework.messaging.core.unitofwork.ProcessingContext} using {@link #SUPPLIER_KEY}, making the
 * entity manager available to components that consume it (such as {@link JpaTransactionalExecutorProvider}).
 * <p>
 * This is the canonical owner of {@link #SUPPLIER_KEY} for JPA-based resources in a processing context,
 * allowing both pure-JPA and Spring-managed transaction scenarios to share the same resource key.
 * <p>
 * Transaction propagation follows a {@code PROPAGATION_REQUIRED} semantic: if a transaction is already
 * active on the entity manager, the existing transaction is joined without taking ownership of its lifecycle.
 * <p>
 * Example usage:
 * <pre>{@code
 * EntityManagerProvider provider = () -> entityManager;
 * TransactionManager txManager = new EntityManagerTransactionManager(provider);
 * txManager.attachToProcessingLifecycle(processingLifecycle);
 * }</pre>
 *
 * @author Axon Framework Contributors
 * @since 5.1.0
 */
public class EntityManagerTransactionManager implements TransactionManager {

    /**
     * Canonical resource key for the {@link EntityManagerExecutor} supplier stored in a
     * {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}.
     * <p>
     * This key is the single authoritative definition shared by this class and
     * {@link JpaTransactionalExecutorProvider}.
     */
    public static final ResourceKey<Supplier<EntityManagerExecutor>> SUPPLIER_KEY =
            ResourceKey.withLabel(EntityManagerExecutor.class.getSimpleName());

    private final EntityManagerProvider entityManagerProvider;

    /**
     * Constructs a new {@code EntityManagerTransactionManager}.
     *
     * @param entityManagerProvider the provider of the {@link jakarta.persistence.EntityManager} to use,
     *                              cannot be {@code null}
     * @throws NullPointerException if {@code entityManagerProvider} is {@code null}
     */
    public EntityManagerTransactionManager(@Nonnull EntityManagerProvider entityManagerProvider) {
        this.entityManagerProvider = Objects.requireNonNull(entityManagerProvider, "entityManagerProvider");
    }

    /**
     * {@inheritDoc}
     * <p>
     * If a transaction is already active on the underlying {@link jakarta.persistence.EntityManager}, the
     * existing transaction is joined and its lifecycle is not managed by the returned {@link Transaction}
     * (commit and rollback are no-ops). Otherwise a new transaction is begun and the returned
     * {@link Transaction} controls its lifecycle.
     */
    @Nonnull
    @Override
    public Transaction startTransaction() {
        EntityTransaction tx = entityManagerProvider.getEntityManager().getTransaction();
        if (tx.isActive()) {
            return new Transaction() {
                @Override
                public void commit() {
                }

                @Override
                public void rollback() {
                }
            };
        }
        tx.begin();
        return new Transaction() {
            @Override
            public void commit() {
                if (tx.isActive() && !tx.getRollbackOnly()) {
                    tx.commit();
                }
            }

            @Override
            public void rollback() {
                if (tx.isActive()) {
                    tx.rollback();
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * In addition to the standard transaction lifecycle (start → commit/rollback), this method registers
     * a {@link CachingSupplier}-wrapped {@link EntityManagerExecutor} under {@link #SUPPLIER_KEY} in the
     * processing context, making the entity manager available to downstream components such as
     * {@link JpaTransactionalExecutorProvider}.
     */
    @Override
    public void attachToProcessingLifecycle(@Nonnull ProcessingLifecycle processingLifecycle) {
        processingLifecycle.runOnPreInvocation(pc -> {
            Transaction transaction = startTransaction();
            pc.putResource(SUPPLIER_KEY, CachingSupplier.of(() -> new EntityManagerExecutor(entityManagerProvider)));
            pc.runOnCommit(p -> transaction.commit());
            pc.onError((p, phase, e) -> transaction.rollback());
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} because JPA {@link jakarta.persistence.EntityManager} instances are not
     * thread-safe and must be accessed from the thread that initiated the transaction.
     */
    @Override
    public boolean requiresSameThreadInvocations() {
        return true;
    }
}
