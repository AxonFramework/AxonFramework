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

package org.axonframework.configuration;

import java.util.concurrent.CompletableFuture;

/**
 * Functional interface towards a lifecycle handler used during start up or shutdown of an application.
 *
 * @author Steven van Beelen
 * @since 4.3.0
 */
@FunctionalInterface
public interface LifecycleHandler {

    /**
     * Run the start-up or shutdown process this {@code LifecycleHandler} represents. Depending on the implementation
     * this might be asynchronous through the return value.
     *
     * @param configuration The configuration that provides access to the components in this application
     * @return a {@link CompletableFuture} of unknown type which enables chaining several {@code LifecycleHandler} calls
     */
    CompletableFuture<?> run(AxonConfiguration configuration);
}
