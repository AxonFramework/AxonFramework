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

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandlerAdapter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;
import javax.annotation.Nonnull;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * Registers Spring beans that implement {@link QueryHandlerAdapter} with the query bus.
 *
 * @author Marc Gathier
 * @since 3.1
 * @deprecated Replaced by the {@link MessageHandlerLookup} and {@link MessageHandlerConfigurer}.
 */
@Deprecated
public class QueryHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {
    private ApplicationContext applicationContext;
    private boolean started;
    private Collection<QueryHandlerAdapter> queryHandlers;
    private QueryBus queryBus;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
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
        if (queryBus == null && !beansOfTypeIncludingAncestors( applicationContext, QueryBus.class ).isEmpty()) {
            queryBus = applicationContext.getBean(QueryBus.class);
        }
        if (queryHandlers == null) {
            queryHandlers = beansOfTypeIncludingAncestors( applicationContext, QueryHandlerAdapter.class ).values();
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
