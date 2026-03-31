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

import org.axonframework.conversion.Converter;
import org.axonframework.extension.spring.config.ProcessorConfigurationExtensionCustomizer;
import org.axonframework.extension.springboot.DeadLetterQueueProcessorProperties;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.DeadLetterQueueConfigurationExtension;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Spring Boot autoconfiguration that registers a JDBC-backed {@link SequencedDeadLetterQueueFactory} bean.
 * <p>
 * This configuration activates when a {@code DataSource} bean is present (i.e. JDBC is on the
 * classpath and configured). The registered factory creates a {@link JdbcSequencedDeadLetterQueue}
 * instance per event handling component, using the application's {@code DataSource},
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
 * To replace the default JDBC factory with a custom backend, declare your own {@link SequencedDeadLetterQueueFactory}
 * bean — the {@code @ConditionalOnMissingBean} guard on the default will yield to it.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 * @see SequencedDeadLetterQueueFactory
 * @see JdbcSequencedDeadLetterQueue
 */
@AutoConfiguration(after = {JdbcAutoConfiguration.class, ConverterAutoConfiguration.class, JpaDeadLetterQueueAutoConfiguration.class})
@ConditionalOnClass(DataSource.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(DeadLetterQueueProcessorProperties.class)
public class JdbcDeadLetterQueueAutoConfiguration {

    /**
     * Creates a default {@link DeadLetterSchema} bean using the default column and table names.
     * <p>
     * To customize the schema, declare your own {@link DeadLetterSchema} bean — the
     * {@code @ConditionalOnMissingBean} guard on this default will yield to it.
     *
     * @return A {@link DeadLetterSchema} with default table and column names.
     */
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterSchema deadLetterSchema() {
        return DeadLetterSchema.defaultSchema();
    }

    /**
     * Creates a JDBC-backed {@link SequencedDeadLetterQueueFactory} that instantiates a
     * {@link JdbcSequencedDeadLetterQueue} per event handling component.
     * <p>
     * The {@code processingGroup} passed to the factory is a component-scoped identifier following the pattern
     * {@code "DeadLetterQueue[processorName][componentName]"}, used to scope dead letters in the database.
     * The {@code configuration} parameter is ignored in this Spring implementation since all dependencies are
     * wired via Spring bean injection.
     *
     * @param dataSource       The JDBC {@link DataSource} used for persistence.
     * @param eventConverter   The {@link EventConverter} used to convert event payloads and metadata.
     * @param genericConverter The generic {@link Converter} used for type conversion.
     * @param schema           The {@link DeadLetterSchema} describing the table and column names.
     * @return A {@link SequencedDeadLetterQueueFactory} backed by JDBC.
     */
    @Bean
    @ConditionalOnMissingBean
    public SequencedDeadLetterQueueFactory jdbcDeadLetterQueueFactory(DataSource dataSource,
                                                                      EventConverter eventConverter,
                                                                      Converter genericConverter,
                                                                      DeadLetterSchema schema) {
        var provider = new JdbcTransactionalExecutorProvider(dataSource);
        return (processingGroup, configuration) -> JdbcSequencedDeadLetterQueue.builder()
                                                                                .processingGroup(processingGroup)
                                                                                .transactionalExecutorProvider(provider)
                                                                                .eventConverter(eventConverter)
                                                                                .genericConverter(genericConverter)
                                                                                .schema(schema)
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
    ProcessorConfigurationExtensionCustomizer jdbcDlqExtensionCustomizer(
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
