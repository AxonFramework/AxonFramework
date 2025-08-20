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

package org.axonframework.messaging.unitofwork;

import jakarta.annotation.Nonnull;
import org.axonframework.common.DirectExecutor;
import org.axonframework.messaging.ApplicationContext;
import org.axonframework.messaging.EmptyApplicationContext;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Configuration used for the {@link UnitOfWork} creation in the {@link UnitOfWorkFactory}.
 * <p>
 * Defines the work scheduler and application context used during unit of work processing.
 *
 * @param workScheduler      The {@link Executor} for processing unit of work actions.
 * @param applicationContext The {@link ApplicationContext} for component resolution.
 *
 * @since 5.0.0
 * @author Mateusz Nowak
 */
public record UnitOfWorkConfiguration(
        @Nonnull Executor workScheduler,
        @Nonnull ApplicationContext applicationContext
) {

    /**
     * Creates default configuration with direct execution and empty application context.
     *
     * @return Default {@link UnitOfWorkConfiguration} instance.
     */
    @Nonnull
    public static UnitOfWorkConfiguration defaultValues() {
        return new UnitOfWorkConfiguration(DirectExecutor.instance(), new EmptyApplicationContext());
    }

    /**
     * Creates new configuration with specified work scheduler.
     *
     * @param workScheduler The {@link Executor} for processing actions.
     * @return New {@link UnitOfWorkConfiguration} with updated work scheduler.
     */
    @Nonnull
    public UnitOfWorkConfiguration workScheduler(@Nonnull Executor workScheduler) {
        Objects.requireNonNull(workScheduler, "workScheduler may not be null");
        return new UnitOfWorkConfiguration(workScheduler, applicationContext);
    }

    /**
     * Creates new configuration with specified application context.
     *
     * @param applicationContext The {@link ApplicationContext} for component access.
     * @return New {@link UnitOfWorkConfiguration} with updated application context.
     */
    @Nonnull
    public UnitOfWorkConfiguration applicationContext(@Nonnull ApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext may not be null");
        return new UnitOfWorkConfiguration(workScheduler, applicationContext);
    }
}
