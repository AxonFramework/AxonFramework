/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.spring.config;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.axonframework.spring.config.event.QueryHandlersSubscribedEvent;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * Registers Spring beans that implement {@link QueryHandlerAdapter} with the {@link QueryBus}.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class QueryHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private ApplicationContext applicationContext;
    private QueryBus queryBus;
    private Collection<QueryHandlerAdapter> queryHandlers;
    private boolean started;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the {@link QueryBus} to use when subscribing query handlers. If not set the {@code QueryBus} is taken from
     * Spring's {@link ApplicationContext}.
     *
     * @param queryBus the {@link QueryBus} to use when subscribing query handlers
     */
    public void setQueryBus(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    /**
     * Sets the query handlers to subscribe to the {@link QueryBus}. If not set the query handlers are taken from
     * Spring's {@link ApplicationContext} by scanning for beans of type {@link QueryHandlerAdapter}.
     *
     * @param queryHandlers query handlers to subscribe to the {@link QueryBus}
     */
    public void setQueryHandlers(Collection<QueryHandlerAdapter> queryHandlers) {
        this.queryHandlers = queryHandlers;
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
        if (queryBus == null && !beansOfTypeIncludingAncestors(applicationContext, QueryBus.class).isEmpty()) {
            queryBus = applicationContext.getBean(QueryBus.class);
        }
        if (queryHandlers == null) {
            queryHandlers = beansOfTypeIncludingAncestors(applicationContext, QueryHandlerAdapter.class).values();
        }
        queryHandlers.forEach(queryHandler -> queryHandler.subscribe(queryBus));
        applicationContext.publishEvent(new QueryHandlersSubscribedEvent(this));
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
        return Integer.MIN_VALUE / 2;
    }
}
