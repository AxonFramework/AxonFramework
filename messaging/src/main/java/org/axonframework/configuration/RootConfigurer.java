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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nonnull;

/**
 * The root {@link NewConfigurer configurer} of any Axon Framework application.
 * <p>
 * Provides a means to register {@link #onStart(int, LifecycleHandler) start} and
 * {@link #onShutdown(int, LifecycleHandler) shutdown} handlers for this application, besides containing all
 * {@link Component components}, {@link ComponentDecorator component decorators}, and {@link Module modules}.
 * <p>
 * Once the configuring phase is completed, the {@link RootConfiguration} of the application can be retrieved by
 * invoking {@link #build()}. Starting the {@code NewConfiguration} can be done through a separate
 * {@link RootConfiguration#start()} invocation, or {@link #start()} can be used to build-and-start the project
 * immediately.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface RootConfigurer extends StartableConfigurer<RootConfigurer> {

    /**
     * Returns a {@code RootConfigurer} instance to start configuring {@link Component components},
     * {@link ComponentDecorator component decorators}, {@link ConfigurationEnhancer enhancers}, and {@link Module modules}
     * for an Axon Framework application.
     *
     * @return A {@code RootConfigurer} instance for further configuring.
     */
    static RootConfigurer defaultConfigurer() {
        return configurer(true);
    }

    /**
     * Returns a {@code RootConfigurer} instance to start configuring {@link Component components},
     * {@link ComponentDecorator component decorators}, {@link ConfigurationEnhancer enhancers}, and {@link Module modules}
     * for an Axon Framework application.
     * <p>
     * When {@code autoLocateEnhancers} is {@code true}, a {@link ServiceLoader} will be used to locate all declared
     * instances of type {@link ConfigurationEnhancer}. Each of the discovered instances will be invoked, allowing it to
     * set default values for the returned {@code RootConfigurer}.
     *
     * @param autoLocateEnhancers Flag indicating whether {@link ConfigurationEnhancer} on the classpath should be
     *                            automatically retrieved. Should be set to {@code false} when using an application
     *                            container, such as Spring or CDI.
     * @return A {@code RootConfigurer} instance for further configuring.
     */
    static RootConfigurer configurer(boolean autoLocateEnhancers) {
        RootConfigurer configurer = new DefaultRootConfigurer();
        if (!autoLocateEnhancers) {
            return configurer;
        }

        ServiceLoader<ConfigurationEnhancer> enhancerLoader =
                ServiceLoader.load(ConfigurationEnhancer.class, configurer.getClass().getClassLoader());
        List<ConfigurationEnhancer> enhancers = new ArrayList<>();
        enhancerLoader.forEach(enhancers::add);
        enhancers.forEach(configurer::registerEnhancer);
        return configurer;
    }

    /**
     * Configures the timeout of each lifecycle phase. The {@code Configurer} invokes lifecycle phases during start-up
     * and shutdown of an application.
     * <p>
     * Note that if a lifecycle phase exceeds the configured {@code timeout} and {@code timeUnit} combination, the
     * {@code Configurer} will proceed with the following phase. A phase-skip is marked with a warn logging message, as
     * the chances are high this causes undesired side effects.
     * <p>
     * The default lifecycle phase timeout is <b>five</b> seconds.
     *
     * @param timeout  The amount of time to wait for lifecycle phase completion.
     * @param timeUnit The unit in which the {@code timeout} is expressed.
     * @return The current instance of the {@code RootConfigurer}, for chaining purposes.
     * @see org.axonframework.lifecycle.Phase
     * @see LifecycleHandler
     */
    RootConfigurer registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit);

    @Override
    default RootConfiguration start() {
        RootConfiguration configuration = build();
        configuration.start();
        return configuration;
    }
}
