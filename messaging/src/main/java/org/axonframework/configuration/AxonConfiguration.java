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

/**
 * The configuration of any Axon Framework application.
 * <p>
 * Provides a means to {@link #start()} and {@link #shutdown() stop} the application, besides containing all
 * {@link Component components}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface AxonConfiguration extends Configuration {

    /**
     * All components defined in this {@code AxonConfiguration} will be started.
     */
    void start();

    /**
     * Shuts down the components defined in this {@code AxonConfiguration}.
     */
    void shutdown();
}
