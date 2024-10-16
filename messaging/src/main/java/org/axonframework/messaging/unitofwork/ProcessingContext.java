/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.common.Context;

/**
 * Implementation of the {@link ProcessingLifecycle} adding resource management operations by implementing
 * {@link Context}.
 * <p>
 * It is recommended to construct a {@link ResourceKey} instance when adding/updating/removing resources from the
 * {@link ProcessingContext} to allow cross-referral by sharing the key or personalization when the resource should be
 * private to a specific service.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ProcessingContext extends ProcessingLifecycle, Context {

    /**
     * Constant referring to a no-op {@link ProcessingContext} implementation, the {@link NoProcessingContext}.
     */
    ProcessingContext NONE = NoProcessingContext.INSTANCE;

    /**
     * Constructs a new {@link ProcessingContext}, branching off from {@code this} {@code ProcessingContext}. The given
     * {@code resource} as added to the branched {@code ProcessingContext} under the given {@code key}.
     *
     * @param key      The key under which to register the {@code resource} in the branched {@link ProcessingContext}.
     * @param resource The resource to register in the branched {@link ProcessingContext}.
     * @param <T>      The type of resource associated with the {@code key}.
     * @return A new {@link ProcessingContext}, branched off from {@code this} {@code ProcessingContext}.
     */
    default <T> ProcessingContext branchedWithResource(ResourceKey<T> key, T resource) {
        return new ResourceOverridingProcessingContext<>(this, key, resource);
    }
}
