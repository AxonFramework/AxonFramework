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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.axonserver.connector.event.axon.DefaultPersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamMessageSourceFactory;
import org.axonframework.axonserver.connector.event.axon.PersistentStreamScheduledExecutorBuilder;
import org.axonframework.extension.springboot.util.ConditionalOnMissingQualifiedBean;
import org.axonframework.extension.springboot.util.ConditionalOnQualifiedBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Auto-configuration for Persistent Stream support with Axon Server.
 * <p>
 * This configuration provides the beans necessary for creating and managing persistent streams
 * from Axon Server. Persistent streams provide a subscribable event source that can be used
 * with subscribing event processors.
 * <p>
 * The configuration is only active when the Axon Server event store is enabled
 * (controlled by {@code axon.axonserver.event-store.enabled} property, defaults to {@code true}).
 *
 * @author Steven van Beelen
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@AutoConfiguration
@ConditionalOnClass(PersistentStreamMessageSourceFactory.class)
public class PersistentStreamAutoConfiguration {

    /**
     * Creates a {@link PersistentStreamScheduledExecutorBuilder} that constructs
     * {@link ScheduledExecutorService ScheduledExecutorServices} for each persistent stream.
     * <p>
     * Defaults to {@link PersistentStreamScheduledExecutorBuilder#defaultFactory()}.
     * <p>
     * This bean is only created if no {@code ScheduledExecutorService} bean with qualifier
     * {@code "persistentStreamScheduler"} is present in the context.
     *
     * @return A {@link PersistentStreamScheduledExecutorBuilder} that constructs
     * {@link ScheduledExecutorService ScheduledExecutorServices} for each persistent stream.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingQualifiedBean(
            beanClass = ScheduledExecutorService.class,
            qualifier = "persistentStreamScheduler"
    )
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamScheduledExecutorBuilder persistentStreamScheduledExecutorBuilder() {
        return PersistentStreamScheduledExecutorBuilder.defaultFactory();
    }

    /**
     * Creates a {@link PersistentStreamScheduledExecutorBuilder} defaulting to the same given
     * {@code persistentStreamScheduler} on each invocation.
     * <p>
     * This bean-creation method is in place for backwards compatibility with 4.10.0, which defaulted
     * to this behavior based on a bean of type {@link ScheduledExecutorService} with qualified
     * {@code persistentStreamScheduler}.
     *
     * @param persistentStreamScheduler The existing {@link ScheduledExecutorService} to use for all persistent streams.
     * @return A {@link PersistentStreamScheduledExecutorBuilder} that always returns the given scheduler.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnQualifiedBean(
            beanClass = ScheduledExecutorService.class,
            qualifier = "persistentStreamScheduler"
    )
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamScheduledExecutorBuilder backwardsCompatiblePersistentStreamScheduledExecutorBuilder(
            @Qualifier("persistentStreamScheduler") ScheduledExecutorService persistentStreamScheduler
    ) {
        return (threadCount, streamName) -> persistentStreamScheduler;
    }

    /**
     * Creates a bean of type {@link PersistentStreamMessageSourceFactory} if one is not already defined.
     * <p>
     * This factory is used to create instances of {@code PersistentStreamMessageSource} with specified
     * configurations. The returned factory creates message sources that are properly configured for
     * Axon Framework 5's async-native API, including proper event conversion with
     * {@code PersistentStreamEventConverter}.
     *
     * @return A {@link PersistentStreamMessageSourceFactory} that constructs
     * {@code PersistentStreamMessageSource} instances.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "axon.axonserver.event-store.enabled", matchIfMissing = true)
    public PersistentStreamMessageSourceFactory persistentStreamMessageSourceFactory() {
        return new DefaultPersistentStreamMessageSourceFactory();
    }
}
