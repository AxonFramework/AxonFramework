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

package org.axonframework.lifecycle;

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.LifecycleHandler;
import org.axonframework.configuration.LifecycleRegistry;

/**
 * Interface for components that can be started and shut down as part of the Application lifecycle.
 *
 * @author Allard Buijze
 * @since 4.6
 */
public interface Lifecycle {

    /**
     * Registers the activities to be executed in the various phases of an application's lifecycle. This could either be
     * at startup, shutdown, or both.
     *
     * @param lifecycle the lifecycle instance to register the handlers with
     * @see LifecycleRegistry#onShutdown(int, Runnable)
     * @see LifecycleRegistry#onShutdown(int, LifecycleHandler)
     * @see LifecycleRegistry#onStart(int, Runnable)
     * @see LifecycleRegistry#onStart(int, LifecycleHandler)
     */
    void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle);
}
