package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * Spring Bean that registers Event Handler beans with the Configuration, which assigns them to Event Processors based
 * on their package name.
 *
 * To customize this behavior, define a Bean in the application context that implements
 * {@link EventHandlingConfiguration}.
 */
public class DefaultSpringEventHandlingConfiguration implements InitializingBean, SmartLifecycle {

    private final EventHandlingConfiguration delegate;
    private Configuration config;
    private volatile boolean running = false;

    public DefaultSpringEventHandlingConfiguration(EventHandlingConfiguration configuration) {
        delegate = configuration;
    }

    public void setEventHandlers(List<Object> beans) {
        beans.forEach(b -> delegate.registerEventHandler(c -> b));
    }

    public void setAxonConfiguration(AxonConfiguration axonConfiguration) {
        this.config = axonConfiguration;
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
    public void start() {
        delegate.start();
        running = true;
    }

    @Override
    public void stop() {
        delegate.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        delegate.initialize(config);
    }
}
