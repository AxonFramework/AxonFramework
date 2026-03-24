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

import org.apache.avro.Schema;
import org.apache.avro.message.SchemaStore;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.avro.AvroConverter;
import org.axonframework.conversion.avro.AvroConverterConfiguration;
import org.axonframework.conversion.avro.AvroConverterStrategy;
import org.axonframework.extension.spring.conversion.avro.AvroSchemaPackages;
import org.axonframework.extension.spring.conversion.avro.AvroSchemaScan;
import org.axonframework.extension.spring.conversion.avro.ClasspathAvroSchemaLoader;
import org.axonframework.extension.spring.conversion.avro.SpecificRecordBaseClasspathAvroSchemaLoader;
import org.axonframework.extension.springboot.ConverterProperties;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Autoconfigures required beans for the Avro {@link Converter}.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
@AutoConfiguration
@AutoConfigureBefore(ConverterAutoConfiguration.class)
@ConditionalOnClass(name = {"org.apache.avro.message.SchemaStore"})
@EnableConfigurationProperties(ConverterProperties.class)
public class AvroSchemaStoreAutoConfiguration implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    /**
     * Bean creation method to throw an {@link AxonConfigurationException}, indicating that the {@link AvroConverter} is
     * not supported as general converter.
     *
     * @return always throws {@link AxonConfigurationException} to indicate misconfiguration
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ignored = {MessageConverter.class, EventConverter.class})
    @ConditionalOnProperty(name = "axon.converter.general", havingValue = "avro")
    public Converter converter() {
        throw new AxonConfigurationException(format(
                "Invalid converter type [%s] configured as general converter. "
                        + "The Avro Converter can be used as message or event converter only.",
                ConverterProperties.ConverterType.AVRO
        ));
    }

    /**
     * Bean creation method constructing an {@link AvroConverter} as the {@link MessageConverter} to be used by Axon
     * Framework.
     *
     * @param schemaStore         the Avro {@link SchemaStore} to be used
     * @param converterStrategies the {@link AvroConverterStrategy AvroConverterStrategies} to be used
     * @return the {@link MessageConverter} to be used by Axon Framework
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "axon.converter.messages", havingValue = "avro")
    public MessageConverter messageConverter(SchemaStore schemaStore,
                                             Map<String, AvroConverterStrategy> converterStrategies) {
        return new DelegatingMessageConverter(buildConverter(schemaStore, converterStrategies));
    }

    /**
     * Bean creation method constructing an {@link EventConverter} delegating to the {@link MessageConverter} in case
     * both use {@code avro}.
     *
     * @param messageConverter the {@link MessageConverter}, used to construct the {@link EventConverter} in case both
     *                         use {@code cbor}
     * @return the {@link EventConverter} to be used by Axon Framework
     */
    @Bean(name = "eventConverter")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.converter.events}' == 'avro' && '${axon.converter.messages}' == 'avro'")
    public EventConverter delegatingEventConverter(MessageConverter messageConverter) {
        return new DelegatingEventConverter(messageConverter);
    }

    /**
     * Bean creation method constructing an {@link AvroConverter} as the {@link EventConverter} to be used by Axon
     * Framework.
     *
     * @param schemaStore         the Avro {@link SchemaStore} to be used
     * @param converterStrategies the {@link AvroConverterStrategy AvroConverterStrategies} to be used
     * @return the {@link EventConverter} to be used by Axon Framework
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.converter.events}' == 'avro' && '${axon.converter.messages}' != 'avro'")
    public EventConverter eventConverter(SchemaStore schemaStore,
                                         Map<String, AvroConverterStrategy> converterStrategies) {
        return new DelegatingEventConverter(buildConverter(schemaStore, converterStrategies));
    }

    private Converter buildConverter(SchemaStore schemaStore, Map<String, AvroConverterStrategy> converterStrategies) {
        return new AvroConverter(
                schemaStore,
                (c) -> {
                    AvroConverterConfiguration result = c;
                    for (AvroConverterStrategy strategy : converterStrategies.values()) {
                        result = result.addConverterStrategy(strategy);
                    }
                    return result;
                },
                new ChainingContentTypeConverter(classLoader)
        );
    }

    /**
     * Constructs a simple default in-memory schema store filled with schemas.
     *
     * @param schemas Avro schemas to put into the store.
     * @return Schema store instance.
     */
    @Bean("defaultAxonSchemaStore")
    @Conditional({AvroConfiguredCondition.class, OnMissingDefaultSchemaStoreCondition.class})
    public SchemaStore defaultAxonSchemaStore(Set<Schema> schemas) {
        SchemaStore.Cache cachingSchemaStore = new SchemaStore.Cache();
        schemas.forEach(cachingSchemaStore::addSchema);
        return cachingSchemaStore;
    }

    /**
     * Scans classpath for schemas, configured using {@link AvroSchemaScan}
     * annotations.
     *
     * @param beanFactory  Spring bean factory.
     * @param schemaLoader List of schema loaders.
     * @return Set of schemas detected on the classpath.
     */
    @Bean
    @Conditional({AvroConfiguredCondition.class})
    public Set<Schema> collectAvroSchemasFromClassPath(BeanFactory beanFactory,
                                                       List<ClasspathAvroSchemaLoader> schemaLoader) {
        final List<String> packagesCandidates = AvroSchemaPackages.get(beanFactory).getPackages();
        final List<String> packagesToScan = new ArrayList<>();
        if (packagesCandidates.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            packagesToScan.addAll(AutoConfigurationPackages.get(beanFactory));
        } else {
            packagesToScan.addAll(packagesCandidates);
        }
        return schemaLoader
                .stream()
                .map(loader -> loader.load(packagesToScan))
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Constructs default schema loader from Avro-Java-Maven-Generated classes.
     *
     * @param resourceLoader The resource loader.
     * @return ClasspathAvroSchemaLoader instance.
     */
    @Bean("specificRecordBaseClasspathAvroSchemaLoader")
    @Conditional({AvroConfiguredCondition.class})
    public ClasspathAvroSchemaLoader specificRecordBaseClasspathAvroSchemaLoader(ResourceLoader resourceLoader) {
        return new SpecificRecordBaseClasspathAvroSchemaLoader(resourceLoader);
    }

    /**
     * An {@link AnyNestedCondition} implementation, to support the following use cases:
     * <ul>
     *     <li>The {@code messages} serializer property is set to {@code avro}</li>
     *     <li>The {@code events} serializer property is set to {@code avro}</li>
     * </ul>
     */
    private static class AvroConfiguredCondition extends AnyNestedCondition {

        public AvroConfiguredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.messages", havingValue = "avro")
        static class MessagesAvroCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.events", havingValue = "avro")
        static class EventsAvroCondition {

        }
    }

    /**
     * Condition checking if a schema store exists.
     */
    private static class OnMissingDefaultSchemaStoreCondition extends AllNestedConditions {

        public OnMissingDefaultSchemaStoreCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnMissingBean(SchemaStore.class)
        @SuppressWarnings("unused")
        static class SchemaStoreIsMissingCondition {

        }
    }

    /**
     * Sets the class loader used by the {@link ChainingContentTypeConverter} to load
     * {@link org.axonframework.conversion.ContentTypeConverter ContentTypeConverters}.
     *
     * @param classLoader The class loader used by the {@link ChainingContentTypeConverter} to load
     *                    {@link org.axonframework.conversion.ContentTypeConverter ContentTypeConverters}.
     */
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "The ClassLoader cannot be null.");
    }
}
