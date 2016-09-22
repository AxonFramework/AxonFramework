package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventHandlingConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;

public class SpringEventHandlingConfiguration implements ApplicationContextAware, InitializingBean, SmartLifecycle {

    private final EventHandlingConfiguration delegate;
    private final List<String> beans = new ArrayList<>();
    private ApplicationContext applicationContext;
    private Configuration config;
    private volatile boolean running = false;

    public SpringEventHandlingConfiguration() {
        delegate = EventHandlingConfiguration.assigningHandlersByPackage();
    }

    public void setEventHandlers(List<Object> beans) {
        beans.forEach(b -> delegate.registerEventHandler(c -> b));
    }

    public void setAxonConfiguration(AxonConfiguration axonConfiguration) {
        this.config = axonConfiguration;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
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
        for (String beanName : beans) {
            Object bean = applicationContext.getBean(beanName);
            delegate.registerEventHandler(c -> bean);
        }
        delegate.initialize(config);
    }
}
