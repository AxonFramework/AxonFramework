/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.spring.config;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.LifecycleHandler;
import org.axonframework.common.configuration.LifecycleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link LifecycleRegistry} implementation that registers all lifecycle handlers as Spring
 * {@link org.springframework.context.SmartLifecycle} beans to ensure Spring weaves these lifecycles into the other
 * Spring bean lifecycles.
 * <p>
 * By being a {@link BeanFactory} implementation, this {@code LifecycleRegistry} is capable of registering the
 * aforementioned {@code SmartLifecycle} beans based on the {@link LifecycleHandler LifecycleHandlers} provided through
 * {@link #onStart(int, LifecycleHandler)}  and {@link #onShutdown(int, LifecycleHandler)}.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public class SpringLifecycleRegistry implements BeanFactoryAware, LifecycleRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final AtomicInteger uniqueId = new AtomicInteger(0);

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public LifecycleRegistry registerLifecyclePhaseTimeout(long timeout, @Nonnull TimeUnit timeUnit) {
        logger.warn("Registering lifecycle phase timeout on a Spring-based LifecycleRegistry is not supported. "
                            + "Please use Spring Boot's \"Graceful Shutdown\" support instead.");
        return this;
    }

    @Override
    public LifecycleRegistry onStart(int phase, @Nonnull LifecycleHandler startHandler) {
        SpringLifecycleStartHandler springStartHandler = new SpringLifecycleStartHandler(
                phase,
                () -> startHandler.run(beanFactory.getBean(AxonConfiguration.class))
        );
        beanFactory.registerSingleton(getBeanName("start"), springStartHandler);
        return this;
    }

    @Override
    public LifecycleRegistry onShutdown(int phase, @Nonnull LifecycleHandler shutdownHandler) {
        SpringLifecycleShutdownHandler springShutdownHandler = new SpringLifecycleShutdownHandler(
                phase,
                () -> shutdownHandler.run(beanFactory.getBean(AxonConfiguration.class))
        );
        beanFactory.registerSingleton(getBeanName("shutdown"), springShutdownHandler);
        return this;
    }

    private String getBeanName(@Nonnull String type) {
        return "axon-" + type + "-lifecycle-handler-" + uniqueId.getAndIncrement();
    }
}
