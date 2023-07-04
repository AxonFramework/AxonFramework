/*
 * Copyright (c) 2010-2022. Axon Framework
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.axonframework.springboot.SerializerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
@EnableConfigurationProperties(value = SerializerProperties.class)
public class ObjectMapperAutoConfiguration {

    @Bean("defaultAxonObjectMapper")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.serializer.general}' == 'jackson' || '${axon.serializer.events}' == 'jackson' || '${axon.serializer.messages}' == 'jackson'")
    public ObjectMapper defaultAxonObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean("defaultAxonCborMapper")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.serializer.general}' == 'jackson-cbor' || '${axon.serializer.events}' == 'jackson-cbor' || '${axon.serializer.messages}' == 'jackson-cbor'")
    public ObjectMapper defaultAxonCborMapper() {
        return new CBORMapper().findAndRegisterModules();
    }
}
