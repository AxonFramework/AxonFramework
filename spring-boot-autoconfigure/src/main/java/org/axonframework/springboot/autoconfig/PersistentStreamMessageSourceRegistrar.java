/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.springboot.autoconfig;

import io.axoniq.axonserver.connector.event.PersistentStreamProperties;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceDefinition;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;

import static io.axoniq.axonserver.connector.impl.ObjectUtils.nonNullOrDefault;

/**
 * Post-processor to create Spring beans for persistent streams defined in the application context.
 *
 * @author Marc Gathier
 * @since 4.10.0
 */
public class PersistentStreamMessageSourceRegistrar implements BeanDefinitionRegistryPostProcessor {

    private final Map<String, AxonServerConfiguration.PersistentStreamSettings> persistentStreams;
    private final PersistentStreamScheduledExecutorBuilder executorBuilder;

    /**
     * Instantiates a {@link PersistentStreamMessageSourceRegistrar} instance.
     * <p>
     * This registrar will retrieve the
     * {@link AxonServerConfiguration.PersistentStreamSettings persistent stream definitions} from the given
     * {@code environment}.
     *
     * @param environment     Application configuration environment.
     * @param executorBuilder The {@link java.util.concurrent.ScheduledExecutorService} builder used during
     *                        construction of the {@link PersistentStreamMessageSourceDefinition}.
     */
    public PersistentStreamMessageSourceRegistrar(Environment environment,
                                                  PersistentStreamScheduledExecutorBuilder executorBuilder) {
        Binder binder = Binder.get(environment);
        this.persistentStreams =
                binder.bind(
                              "axon.axonserver.persistent-streams",
                              Bindable.mapOf(String.class, AxonServerConfiguration.PersistentStreamSettings.class)
                      )
                      .orElse(Collections.emptyMap());
        this.executorBuilder = executorBuilder;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(
            @Nonnull BeanDefinitionRegistry beanDefinitionRegistry
    ) throws BeansException {
        persistentStreams.forEach((name, settings) -> {
            BeanDefinitionBuilder beanDefinition =
                    BeanDefinitionBuilder.genericBeanDefinition(PersistentStreamMessageSourceDefinition.class);
            String streamName = nonNullOrDefault(settings.getName(), name);
            beanDefinition.addConstructorArgValue(streamName);

            BeanDefinitionBuilder streamProperties =
                    BeanDefinitionBuilder.genericBeanDefinition(PersistentStreamProperties.class);
            streamProperties.addConstructorArgValue(streamName);
            streamProperties.addConstructorArgValue(settings.getInitialSegmentCount());
            streamProperties.addConstructorArgValue(settings.getSequencingPolicy());
            streamProperties.addConstructorArgValue(settings.getSequencingPolicyParameters());
            streamProperties.addConstructorArgValue(settings.getInitialPosition());
            streamProperties.addConstructorArgValue(settings.getFilter());

            beanDefinition.addConstructorArgValue(streamProperties.getBeanDefinition());
            beanDefinition.addConstructorArgValue(executorBuilder.build(settings.getThreadCount(), streamName));
            beanDefinition.addConstructorArgValue(settings.getBatchSize());
            beanDefinition.addConstructorArgValue(null);
            beanDefinition.addConstructorArgValue(new RuntimeBeanReference(PersistentStreamMessageSourceFactory.class));

            beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition.getBeanDefinition());
        });
    }

    @Override
    public void postProcessBeanFactory(
            @Nonnull ConfigurableListableBeanFactory configurableListableBeanFactory
    ) throws BeansException {
        // No actions needed here
    }
}
