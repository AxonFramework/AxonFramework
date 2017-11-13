package org.axonframework.spring.config;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;

/**
 * Registers Spring beans that implement {@link QueryHandlerAdapter} with the query bus.
 * @since 3.1
 * @author Marc Gathier
 *
 */
public class QueryHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {
    private ApplicationContext applicationContext;
    private boolean started;
    private Collection<QueryHandlerAdapter> queryHandlers;
    private QueryBus queryBus;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setQueryHandlers(Collection<QueryHandlerAdapter> queryHandlers) {
        this.queryHandlers = queryHandlers;
    }

    public void setQueryBus(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable runnable) {
        stop();
        runnable.run();
    }

    @Override
    public void start() {
        if (queryBus == null && !applicationContext.getBeansOfType(QueryBus.class).isEmpty()) {
            queryBus = applicationContext.getBean(QueryBus.class);
        }
        if (queryHandlers == null) {
            queryHandlers = applicationContext.getBeansOfType(QueryHandlerAdapter.class).values();
        }
        queryHandlers.forEach(queryHandler -> queryHandler.subscribe(queryBus));
        this.started = true;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE/2;
    }
}
