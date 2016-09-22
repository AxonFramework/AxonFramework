package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.function.Supplier;

@org.springframework.context.annotation.Configuration
public class AxonConfiguration implements Configuration, InitializingBean, ApplicationContextAware, SmartLifecycle {

    private Configuration config;
    private final Configurer configurer;
    private volatile boolean running = false;

    public AxonConfiguration(Configurer configurer) {
        this.configurer = configurer;
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
    public List<CorrelationDataProvider> correlationDataProviders() {
        return config.correlationDataProviders();
    }

    @Override
    public void start() {
        config.start();
        this.running = true;
    }

    @Override
    public void shutdown() {
        config.shutdown();
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

    @Override
    public void afterPropertiesSet() throws Exception {
        // TODO: Build configurer based on blocks available in context
        config = configurer.buildConfiguration();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        configurer.registerComponent(ApplicationContext.class, c -> applicationContext);
    }
}

