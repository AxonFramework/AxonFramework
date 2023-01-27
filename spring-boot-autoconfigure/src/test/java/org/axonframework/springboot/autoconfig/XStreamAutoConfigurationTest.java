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

import com.thoughtworks.xstream.XStream;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link XStreamAutoConfiguration}.
 *
 * @author Steven van Beelen
 */
class XStreamAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void xStreamAutoConfigurationConstructsXStreamInstanceWhenGeneralSerializerPropertyIsXStream() {
        testApplicationContext.withUserConfiguration(DefaultContext.class)
                              .withPropertyValues("axon.serializer.general:xstream")
                              .run(context -> {
                                  assertThat(context).hasSingleBean(XStream.class);
                                  assertThat(context).getBean("defaultAxonXStream")
                                                     .isInstanceOf(XStream.class);
                              });
    }

    @Test
    void xStreamAutoConfigurationConstructsXStreamInstanceWhenGeneralSerializerPropertyIsDefault() {
        testApplicationContext.withUserConfiguration(DefaultContext.class)
                              .withPropertyValues("axon.serializer.general:default")
                              .run(context -> {
                                  assertThat(context).hasSingleBean(XStream.class);
                                  assertThat(context).getBean("defaultAxonXStream")
                                                     .isInstanceOf(XStream.class);
                              });
    }

    @Test
    void xStreamAutoConfigurationConstructsXStreamInstanceWhenMessagesSerializerPropertyIsXStream() {
        testApplicationContext.withUserConfiguration(DefaultContext.class)
                              .withPropertyValues("axon.serializer.messages:xstream")
                              .run(context -> {
                                  assertThat(context).hasSingleBean(XStream.class);
                                  assertThat(context).getBean("defaultAxonXStream")
                                                     .isInstanceOf(XStream.class);
                              });
    }

    @Test
    void xStreamAutoConfigurationConstructsXStreamInstanceWhenEventsSerializerPropertyIsXStream() {
        testApplicationContext.withUserConfiguration(DefaultContext.class)
                              .withPropertyValues("axon.serializer.events:xstream")
                              .run(context -> {
                                  assertThat(context).hasSingleBean(XStream.class);
                                  assertThat(context).getBean("defaultAxonXStream")
                                                     .isInstanceOf(XStream.class);
                              });
    }

    @Test
    void xStreamAutoConfigurationDoesNotConstructXStreamInstanceWhenNoPropertyIsXStream() {
        testApplicationContext.withUserConfiguration(DefaultContext.class)
                              .withPropertyValues(
                                      "axon.serializer.general:jackson",
                                      "axon.serializer.messages:jackson",
                                      "axon.serializer.events:jackson"
                              )
                              .run(context -> assertThat(context).getBean(XStream.class).isNull());
    }

    @Test
    void xStreamAutoConfigurationDoesNotConstructXStreamInstanceForExistingBean() {
        testApplicationContext.withUserConfiguration(ContextWithXStreamInstance.class)
                              .run(context -> {
                                  assertThat(context).hasSingleBean(XStream.class);
                                  assertThat(context).getBean("defaultAxonXStream")
                                                     .isNull();
                              });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class DefaultContext {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @SpringBootApplication
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class ContextWithXStreamInstance {

        @Bean
        private XStream customInstance() {
            return new XStream();
        }
    }
}