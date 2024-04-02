/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.spring.event.AxonStartedEvent;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Factory Bean implementation that creates an Axon {@link Configuration} for a {@link Configurer}. This allows a {@code
 * Configuration} bean to be available in an Application Context that already defines a {@code Configurer}.
 * <p>
 * This factory bean will also ensure the {@code Configuration's} lifecycle is attached to the Spring Application
 * lifecycle.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringAxonConfiguration implements FactoryBean<Configuration>, SmartLifecycle {

    private final Configurer configurer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicReference<Configuration> configuration = new AtomicReference<>();
    private ApplicationContext applicationContext;

    /**
     * Initialize this {@link Configuration} instance.
     *
     * @param configurer The configurer to get the {@link Configuration} from.
     */
    public SpringAxonConfiguration(Configurer configurer) {
        this.configurer = configurer;
    }

    @NonNull
    @Override
    public Configuration getObject() {
        Configuration c = configuration.get();
        if (c != null) {
            return c;
        }
        configuration.compareAndSet(null, configurer.buildConfiguration());
        return configuration.get();
    }

    @Override
    public Class<?> getObjectType() {
        return Configuration.class;
    }

    @Override
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            getObject().start();
            this.applicationContext.publishEvent(new AxonStartedEvent());
        }
    }

    @Override
    public void stop() {
        Configuration c = this.configuration.get();
        if (isRunning.compareAndSet(true, false) && c != null) {
            c.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public int getPhase() {
        // Run this with a safely lower phase value than what is set in the spring WebServer start/stop Lifecycle.
        // This to make sure:
        // - Axon is ready when the web server starts
        // - The web server is stopped before tearing down Axon
        int webServerGracefulShutdownLifecyclePhase = SmartLifecycle.DEFAULT_PHASE - 1024;
        int webServerDefaultLifecyclePhase = webServerGracefulShutdownLifecyclePhase - 1024;
        return webServerDefaultLifecyclePhase - 1024;
    }
}
