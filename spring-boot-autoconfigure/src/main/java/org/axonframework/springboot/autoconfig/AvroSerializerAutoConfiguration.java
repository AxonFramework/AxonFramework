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
import org.axonframework.spring.serialization.avro.ClasspathAvroSchemaLoader;
import org.axonframework.spring.serialization.avro.SpecificRecordBaseClasspathAvroSchemaLoader;
import org.axonframework.spring.serialization.avro.AvroSchemaPackages;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
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

    @Bean("defaultAxonSchemaStore")
    @ConditionalOnMissingBean
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
            .stream().map(loader -> loader.scan(packagesToScan)).flatMap(List::stream)
            .collect(Collectors.toSet())
            .forEach(cachingSchemaStore::addSchema);
        return cachingSchemaStore;
    }

    @Bean("specificRecordBaseClasspathAvroSchemaLoader")
    @ConditionalOnMissingBean
    public ClasspathAvroSchemaLoader specificRecordBaseClasspathAvroSchemaLoader(ResourceLoader resourceLoader) {
        return new SpecificRecordBaseClasspathAvroSchemaLoader(resourceLoader);
    }

}
