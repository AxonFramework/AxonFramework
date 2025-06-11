/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.configuration.LifecycleHandler;
import org.axonframework.configuration.LifecycleRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LifecycleRegistry implementation that registers all lifecycle handlers as Spring SmartLifecycle beans to ensure
 * Spring weaves these lifecycles into the other Spring bean lifecycles.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public class SpringLifecycleRegistry implements LifecycleRegistry, BeanFactoryAware {

    private ConfigurableListableBeanFactory beanFactory;
    private final AtomicInteger uniqueId = new AtomicInteger(0);

    @Override
    public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        // not supported - lifecycle is managed by Spring
        // TODO - Add some WARN logging to indicate this is managed by Spring
        return this;
    }

    @Override
    public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        beanFactory.registerSingleton("lifecyclehandler-" + uniqueId.getAndIncrement(),
                                      new SpringLifecycleStartHandler(phase, () -> startHandler.run(beanFactory.getBean(AxonConfiguration.class))));
        return this;
    }

    @Override
    public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        beanFactory.registerSingleton("lifecyclehandler-" + uniqueId.getAndIncrement(),
                                      new SpringLifecycleShutdownHandler(phase, () -> shutdownHandler.run(beanFactory.getBean(AxonConfiguration.class))));
        return this;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }
}
