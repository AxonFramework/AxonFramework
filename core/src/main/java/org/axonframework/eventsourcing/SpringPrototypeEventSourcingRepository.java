/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventsourcing;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Repository implementation that creates and initializes aggregates based on a Spring prototype bean. This allows
 * aggregates to be easily configured and injected with Spring dependencies.
 * <p/>
 * The <code>prototypeBeanName</code> should be set to the name of the bean that this repository should use as a
 * prototype. It is required that the bean implements {@link org.axonframework.eventsourcing.EventSourcedAggregateRoot},
 * and it should have an accessible constructor that takes a single {@link java.util.UUID} argument.
 *
 * @param <T> The type of bean managed by this repository.
 * @author Allard Buijze
 * @since 0.6
 */
public class SpringPrototypeEventSourcingRepository<T extends EventSourcedAggregateRoot>
        extends EventSourcingRepository<T> implements InitializingBean, ApplicationContextAware, BeanNameAware {

    private final SpringPrototypeAggregateFactory<T> prototypeFactory;

    /**
     *
     */
    @Deprecated
    public SpringPrototypeEventSourcingRepository() {
        super(new SpringPrototypeAggregateFactory<T>());
        this.prototypeFactory = (SpringPrototypeAggregateFactory<T>) getAggregateFactory();
    }

    /**
     * Sets the name of the prototype bean this repository serves. Note that the the bean should have the prototype
     * scope and have a constructor that takes a single UUID argument.
     *
     * @param prototypeBeanName the name of the prototype bean this repository serves.
     */
    @Required
    public void setPrototypeBeanName(String prototypeBeanName) {
        prototypeFactory.setPrototypeBeanName(prototypeBeanName);
    }

    /**
     * Sets the type identifier of the aggregate served by this repository. The type identifier is used to identify
     * events in the event store as belonging to an aggregate served by this repository.
     * <p/>
     * Defaults to the bean name of the prototype bean.
     *
     * @param typeIdentifier the type identifier of the aggregate served by this repository.
     */
    public void setTypeIdentifier(String typeIdentifier) {
        prototypeFactory.setTypeIdentifier(typeIdentifier);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        prototypeFactory.afterPropertiesSet();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        prototypeFactory.setApplicationContext(applicationContext);
    }

    @Override
    public void setBeanName(String name) {
        prototypeFactory.setBeanName(name);
    }
}
