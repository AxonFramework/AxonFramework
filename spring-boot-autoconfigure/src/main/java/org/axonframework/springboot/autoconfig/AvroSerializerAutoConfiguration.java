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

import org.apache.avro.message.SchemaStore;
import org.axonframework.spring.serialization.avro.AvroSchemaPackages;
import org.axonframework.spring.serialization.avro.ClasspathAvroSchemaLoader;
import org.axonframework.spring.serialization.avro.SpecificRecordBaseClasspathAvroSchemaLoader;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Autoconfigures required beans for the Avro serializer.
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(name = {"org.apache.avro.message.SchemaStore"})
public class AvroSerializerAutoConfiguration {

    /**
     * Constructs a default in-memory schema store filled with schemas detected during class scanning of packages,
     * configured using {@link org.axonframework.spring.serialization.avro.AvroSchemaScan} annotations.
     *
     * @param beanFactory  spring bean factory.
     * @param schemaLoader list of schema loaders.
     * @return schema store instance.
     */
    @Bean("defaultAxonSchemaStore")
    // TODO we need to handle missing bean condition too in order to make it replaceable.
    @Conditional({AvroConfiguredCondition.class})
    public SchemaStore defaultAxonSchemaStore(BeanFactory beanFactory, List<ClasspathAvroSchemaLoader> schemaLoader) {
        SchemaStore.Cache cachingSchemaStore = new SchemaStore.Cache();
        List<String> packagesCandidates = AvroSchemaPackages.get(beanFactory).getPackages();
        final List<String> packagesToScan = new ArrayList<>();
        if (packagesCandidates.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            packagesToScan.addAll(AutoConfigurationPackages.get(beanFactory));
        } else {
            packagesToScan.addAll(packagesCandidates);
        }
        schemaLoader
                .stream().map(loader -> loader.load(packagesToScan)).flatMap(List::stream)
                .collect(Collectors.toSet())
                .forEach(cachingSchemaStore::addSchema);
        return cachingSchemaStore;
    }

    /**
     * Constructs default schema loader from Avro-Java-Maven-Generated classes.
     * @param resourceLoader resource loader.
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
     *     <li>The {@code general} serializer property is set to {@code avro}</li>
     *     <li>The {@code messages} serializer property is set to {@code avro}</li>
     *     <li>The {@code events} serializer property is set to {@code avro}</li>
     * </ul>
     */
    private static class AvroConfiguredCondition extends AnyNestedCondition {

        public AvroConfiguredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @SuppressWarnings("unused")
        // TODO: Question, the use case of events and messages makes sense for Avro, but general is artificial?
        @ConditionalOnProperty(name = "axon.serializer.general", havingValue = "avro")
        static class GeneralAvroCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.messages", havingValue = "avro")
        static class MessagesAvroCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.events", havingValue = "avro")
        static class EventsAvroCondition {

        }
    }
}
