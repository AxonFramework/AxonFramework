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

/**
 * Defines a contract for components that can register handlers with a {@link ProcessingLifecycle}.
 * <p>
 * Implementations of this interface are responsible for customizing or managing the lifecycle of a
 * {@link ProcessingLifecycle} by adding specific behavior through registered handlers.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public interface ProcessingLifecycleHandlerRegistrar {

    /**
     * Registers handlers with the provided {@link ProcessingLifecycle}. This method is intended to customize or enhance
     * the lifecycle by adding specific behaviors or operational logic through the registered handlers.
     *
     * @param processingLifecycle The lifecycle object where handlers will be registered.
     */
    void registerHandlers(@Nonnull ProcessingLifecycle processingLifecycle);

    /**
     * Indicates whether the invocations of handlers registered through this registrar must occur on the same thread.
     * <p>
     * By default, this method returns {@code false}, meaning there are no thread affinity requirements for the
     * invocations. Implementations can override this method if there is a specific need for handler methods to be
     * invoked on the same thread.
     *
     * @return {@code true} if the handler invocations must occur on the same thread; {@code false} otherwise.
     */
    default boolean requiresSameThreadInvocations() {
        return false;
    }
}
