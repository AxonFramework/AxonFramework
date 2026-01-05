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

package org.axonframework.messaging.core.unitofwork.transaction;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

/**
 * Provider of {@link TransactionalExecutor TransactionalExecutors}.
 *
 * @param <T> The type of resource the {@link TransactionalExecutor} works with.
 * @author John Hendrikx
 * @since 5.0.2
 */
@Internal
public interface TransactionalExecutorProvider<T> {

    /**
     * Provides a {@link TransactionalExecutor}, using the optional processing context.
     *
     * @param processingContext A {@link ProcessingContext}, can be {@code null}.
     * @return A {@link TransactionalExecutor}, never {@code null}.
     */
    @Nonnull
    TransactionalExecutor<T> getTransactionalExecutor(@Nullable ProcessingContext processingContext);
}
