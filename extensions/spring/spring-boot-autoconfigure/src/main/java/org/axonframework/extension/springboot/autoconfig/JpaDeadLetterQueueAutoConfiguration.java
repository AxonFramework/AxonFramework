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

package org.axonframework.extension.springboot.autoconfig;

import jakarta.persistence.EntityManagerFactory;
import org.axonframework.conversion.Converter;
import org.axonframework.extension.spring.config.ProcessorConfigurationExtensionCustomizer;
import org.axonframework.extension.springboot.DeadLetterQueueProcessorProperties;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfigurationExtension;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration that registers a JPA-backed {@link SequencedDeadLetterQueueFactory} bean.
 * <p>
 * This configuration activates when a {@code EntityManagerFactory} bean is present (i.e. JPA is on the
 * classpath and configured). The registered factory creates a {@link JpaSequencedDeadLetterQueue}
 * instance per event handling component, using the application's {@code EntityManagerFactory},
 * {@link EventConverter}, and {@link Converter}. Each queue is scoped by a component-level processing
 * group identifier (e.g. {@code "DeadLetterQueue[myProcessor][0]"}).
 * <p>
 * To enable Dead Letter Queue processing for a specific processor, set:
 * <pre>{@code
 * axon.eventhandling.processors.<processorName>.dlq.enabled=true
 * }</pre>
 * <p>
 * The cache size used for sequence identifier caching can be tuned per processor:
 * <pre>{@code
 * axon.eventhandling.processors.<processorName>.dlq.cache.size=2048
 * }</pre>
 * <p>
 * To replace the default JPA factory with a custom backend, declare your own {@link SequencedDeadLetterQueueFactory}
 * bean — the {@code @ConditionalOnMissingBean} guard on the default will yield to it.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see SequencedDeadLetterQueueFactory
 * @see JpaSequencedDeadLetterQueue
 */
@AutoConfiguration(after = {JpaAutoConfiguration.class, ConverterAutoConfiguration.class})
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties(DeadLetterQueueProcessorProperties.class)
public class JpaDeadLetterQueueAutoConfiguration {

    /**
     * Creates a JPA-backed {@link SequencedDeadLetterQueueFactory} that instantiates a
     * {@link JpaSequencedDeadLetterQueue} per event handling component.
     * <p>
     * The {@code processingGroup} passed to the factory is a component-scoped identifier following the pattern
     * {@code "DeadLetterQueue[processorName][componentName]"}, used to scope dead letters in the database.
     * The {@code configuration} parameter is ignored in this Spring implementation since all dependencies are
     * wired via Spring bean injection.
     *
     * @param entityManagerFactory The JPA {@link EntityManagerFactory} used for persistence.
     * @param eventConverter       The {@link EventConverter} used to convert event payloads and metadata.
     * @param genericConverter     The generic {@link Converter} used for type conversion.
     * @return A {@link SequencedDeadLetterQueueFactory} backed by JPA.
     */
    @Bean
    @ConditionalOnMissingBean
    public SequencedDeadLetterQueueFactory jpaDeadLetterQueueFactory(EntityManagerFactory entityManagerFactory,
                                                                     EventConverter eventConverter,
                                                                     Converter genericConverter) {
        var provider = new JpaTransactionalExecutorProvider(entityManagerFactory);
        return (processingGroup, configuration) -> JpaSequencedDeadLetterQueue.builder()
                                                                               .processingGroup(processingGroup)
                                                                               .transactionalExecutorProvider(provider)
                                                                               .eventConverter(eventConverter)
                                                                               .genericConverter(genericConverter)
                                                                               .build();
    }

    /**
     * Creates a {@link ProcessorConfigurationExtensionCustomizer} that enables the Dead Letter Queue extension
     * on each processor where {@code axon.eventhandling.processors.<name>.dlq.enabled=true}.
     * <p>
     * The customizer uses the {@link DeadLetterQueueProcessorProperties} to read per-processor DLQ settings
     * and configures the {@link DeadLetterQueueConfigurationExtension} accordingly.
     *
     * @param properties The DLQ processor properties.
     * @param factory    The {@link SequencedDeadLetterQueueFactory} to use for queue creation.
     * @return A customizer that applies DLQ extension settings per processor.
     */
    @Bean
    ProcessorConfigurationExtensionCustomizer jpaDlqExtensionCustomizer(
            DeadLetterQueueProcessorProperties properties,
            SequencedDeadLetterQueueFactory factory
    ) {
        return (axonConfig, processorName, processorConfig) -> {
            var dlqProps = properties.forProcessor(processorName);
            if (dlqProps.getDlq().isEnabled()) {
                processorConfig.extend(DeadLetterQueueConfigurationExtension.class)
                               .deadLetterQueue(dlq -> dlq.enabled()
                                                          .factory(factory)
                                                          .cacheMaxSize(dlqProps.getDlq().getCache().getSize()));
            }
        };
    }
}
