package org.axonframework.eventsourcing;

import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author Allard Buijze
 */
public class SpringWiredAggregate extends AbstractAnnotatedAggregateRoot implements ApplicationContextAware {

    private transient ApplicationContext context;
    private transient String springConfiguredName;
    private transient boolean initialized;

    public SpringWiredAggregate() {
    }

    public ApplicationContext getContext() {
        return context;
    }

    public String getSpringConfiguredName() {
        return springConfiguredName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setSpringConfiguredName(String springConfiguredName) {
        this.springConfiguredName = springConfiguredName;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public void initialize() {
        this.initialized = true;
    }
}
