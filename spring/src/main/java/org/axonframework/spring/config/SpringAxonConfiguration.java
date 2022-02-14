package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Factory Bean implementation that creates an Axon Configuration for a Configurer. This allows a Configuration bean
 * to be available in an Application Context that already defines a Configurer.
 * <p>
 * This factory bean will also ensure the Configuration's lifecycle is attached to the Spring Application lifecycle
 */
public class SpringAxonConfiguration implements FactoryBean<Configuration>, SmartLifecycle {

    private final Configurer configurer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicReference<Configuration> configuration = new AtomicReference<>();

    /**
     * Initialize the Configuration instance
     *
     * @param configurer The configurer to get the Configuration from
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
}
