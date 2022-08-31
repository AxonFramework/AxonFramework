/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.springboot;

import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration
@EnableAutoConfiguration(exclude = {JmxAutoConfiguration.class, WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class})
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class AxonAutoConfigurationWithOnlyEventSerializerTest {

    @Autowired
    private Serializer serializer;

    @Autowired
    @Qualifier("eventSerializer")
    private Serializer eventSerializer;

    @Autowired
    @Qualifier("messageSerializer")
    private Serializer messageSerializer;

    @Test
    void revertsToDefaultSerializer() {
        assertNotNull(serializer);
        assertNotNull(eventSerializer);
        assertNotNull(messageSerializer);
        assertEquals(XStreamSerializer.class, serializer.getClass());
        assertEquals(JacksonSerializer.class, eventSerializer.getClass());
        assertEquals(XStreamSerializer.class, messageSerializer.getClass());
        assertNotSame(eventSerializer, serializer);
        assertNotSame(eventSerializer, messageSerializer);
        assertNotSame(serializer, messageSerializer);
    }

    @org.springframework.context.annotation.Configuration
    public static class Configuration {

        @Bean
        @Qualifier("eventSerializer")
        public Serializer myEventSerializer() {
            return JacksonSerializer.builder().build();
        }

        @Bean
        @Qualifier("messageSerializer")
        public Serializer myMessageSerializer(XStream xStream) {
            return XStreamSerializer.builder()
                                    .xStream(xStream)
                                    .build();
        }
    }
}
