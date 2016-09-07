package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.function.Supplier;

@org.springframework.context.annotation.Configuration
public class AxonConfiguration implements Configuration {

    private final Configuration config;

    public AxonConfiguration(Configuration config) {
        this.config = config;
    }

    @NoBeanOfType(CommandBus.class)
    @Bean
    public CommandBus commandBus() {
        return config.commandBus();
    }

    @NoBeanOfType(EventBus.class)
    @Bean
    @Override
    public EventBus eventBus() {
        return config.eventBus();
    }

    @Bean
    public ConfigurationLifecycle configurationLifecycle() {
        return new ConfigurationLifecycle();
    }

    @Override
    public <T> Repository<T> repository(Class<T> aggregate) {
        return config.repository(aggregate);
    }

    @Override
    public <T> T getComponent(Class<T> componentType, Supplier<T> defaultImpl) {
        return config.getComponent(componentType, defaultImpl);
    }

    @Override
    public <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName) {
        return config.messageMonitor(componentType, componentName);
    }

    @Override
    public void start() {
        config.start();
    }

    @Override
    public void shutdown() {
        config.shutdown();
    }

    @Override
    public List<CorrelationDataProvider> correlationDataProviders() {
        return config.correlationDataProviders();
    }

    public class ConfigurationLifecycle implements SmartLifecycle {

        private volatile boolean running = false;

        @Override
        public void start() {
            config.start();
            this.running = true;
        }

        @Override
        public void stop() {
            shutdown();
            this.running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }


        @Override
        public boolean isAutoStartup() {
            return true;
        }

        @Override
        public void stop(Runnable callback) {
            stop();
            callback.run();
        }

        @Override
        public int getPhase() {
            return 0;
        }
    }
}
