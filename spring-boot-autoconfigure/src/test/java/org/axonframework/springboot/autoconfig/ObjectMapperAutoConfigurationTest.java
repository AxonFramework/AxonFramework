/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.serializertest.properties")
class ObjectMapperAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void eventSerializerIsOfTypeJacksonSerializerAndUsesDefaultAxonObjectMapperBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final CBORMapper cborMapper = applicationContext.getBean("defaultAxonCborMapper", CBORMapper.class);

        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final ObjectMapper objectMapper = applicationContext.getBean("defaultAxonObjectMapper", ObjectMapper.class);

        assertTrue(serializer instanceof JacksonSerializer);
        assertEquals(cborMapper, ((JacksonSerializer) serializer).getObjectMapper());
        assertTrue(eventSerializer instanceof JacksonSerializer);
        assertEquals(objectMapper, ((JacksonSerializer) eventSerializer).getObjectMapper());
    }
}