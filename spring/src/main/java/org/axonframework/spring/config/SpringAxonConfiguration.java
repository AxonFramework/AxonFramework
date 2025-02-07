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

import jakarta.annotation.Nonnull;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.spring.event.AxonStartedEvent;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Factory Bean implementation that creates an Axon {@link Configuration} for a {@link Configurer}. This allows a
 * {@code Configuration} bean to be available in an Application Context that already defines a {@code Configurer}.
 * <p>
 * This factory bean will also ensure the {@code Configuration's} lifecycle is attached to the Spring Application
 * lifecycle.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringAxonConfiguration
        implements FactoryBean<Configuration>, SmartLifecycle, ApplicationListener<ContextClosedEvent> {

    /**
     * The {@link SmartLifecycle#getPhase()} value of this is set to be safely lower than the typical values listed in
     * the Spring WebServer lifecycles.
     * <p>
     * This means we make sure that:
     * - Axon is ready when the web server starts
     * - The web server is stopped before tearing down Axon
     */
    public static final int LIFECYCLE_PHASE = SmartLifecycle.DEFAULT_PHASE
            - 1024 // this puts us next to the "graceful" stop of web servers...
            - 1024 // this puts us next to the regular, start and non-graceful stop of web servers...
            - 1024; // we place ourselves healthily below that, starting before web servers and stopping after.

    private final Configurer configurer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicReference<Configuration> configuration = new AtomicReference<>();
    private ApplicationContext applicationContext;
    private final CountDownLatch contextClosedLatch = new CountDownLatch(1);

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
        throw new UnsupportedOperationException("Stop must not be invoked directly");
    }

    @Override
    public void stop(@Nonnull Runnable callback) {
        new Thread(() -> {
            try {
                contextClosedLatch.await(30, TimeUnit.SECONDS); // todo: configure time?
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                Configuration c = this.configuration.get();
                if (isRunning.compareAndSet(true, false) && c != null) {
                    c.shutdown();
                    callback.run();
                }
            }
        }).start();
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
        return LIFECYCLE_PHASE;
    }

    @Override
    public void onApplicationEvent(@Nonnull ContextClosedEvent event) {
        contextClosedLatch.countDown();
    }
}
