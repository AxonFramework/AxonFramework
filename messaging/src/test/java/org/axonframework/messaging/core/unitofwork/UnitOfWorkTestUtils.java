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

package org.axonframework.messaging.core.unitofwork;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentNotFoundException;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.EmptyApplicationContext;

import java.util.UUID;

/**
 * Test utilities when dealing with {@link UnitOfWork}.
 *
 * @author Mateusz Nowak
 */
public final class UnitOfWorkTestUtils {

    public static final SimpleUnitOfWorkFactory SIMPLE_FACTORY = new SimpleUnitOfWorkFactory(
            EmptyApplicationContext.INSTANCE
    );

    /**
     * Creates a new {@link UnitOfWork} with the given identifier.
     * <p>
     * Please note this instance will be created using the {@link SimpleUnitOfWorkFactory} with an
     * {@link EmptyApplicationContext}, so you will not be able to get any components from the
     * {@link ProcessingContext#component} method - it will always throw a
     * {@link ComponentNotFoundException}.
     *
     * @return A new {@link UnitOfWork} with the random identifier.
     */
    @Nonnull
    public static UnitOfWork aUnitOfWork() {
        return SIMPLE_FACTORY.create(UUID.randomUUID().toString());
    }

    /**
     * Creates a new {@link UnitOfWork} with the given identifier.
     * <p>
     * Please note this instance will be created using the {@link SimpleUnitOfWorkFactory} with an
     * {@link EmptyApplicationContext}, so you will not be able to get any components from the
     * {@link ProcessingContext#component} method - it will always throw a
     * {@link ComponentNotFoundException}.
     *
     * @param identifier The identifier for the {@link UnitOfWork}.
     * @return A new {@link UnitOfWork} with the given identifier.
     */
    @Nonnull
    public static UnitOfWork aUnitOfWork(@Nonnull String identifier) {
        return SIMPLE_FACTORY.create(identifier);
    }

    /**
     * Creates a {@link TransactionalUnitOfWorkFactory} configured with the given {@link TransactionManager}. The
     * resulting factory creates {@link UnitOfWork} instances bound to transactions managed by the specified
     * {@link TransactionManager}.
     * <p>
     * Please note this will delegate to the {@link SimpleUnitOfWorkFactory} with an {@link EmptyApplicationContext}, so
     * you will not be able to get any components from the {@link ProcessingContext#component} method - it will always
     * throw a {@link ComponentNotFoundException}.
     *
     * @param transactionManager The transaction manager used to manage transactions for the units of work.
     * @return A new instance of {@link TransactionalUnitOfWorkFactory} using the provided transaction manager.
     */
    public static TransactionalUnitOfWorkFactory transactionalUnitOfWorkFactory(TransactionManager transactionManager) {
        return new TransactionalUnitOfWorkFactory(transactionManager, SIMPLE_FACTORY);
    }

    private UnitOfWorkTestUtils() {
        // Utility class
    }
}
