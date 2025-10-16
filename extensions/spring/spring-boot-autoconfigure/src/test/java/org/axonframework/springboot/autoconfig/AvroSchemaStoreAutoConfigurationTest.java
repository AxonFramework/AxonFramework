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

package org.axonframework.springboot.autoconfig;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.conversion.DelegatingMessageConverter;
import org.axonframework.serialization.ConversionException;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.avro.AvroConverter;
import org.axonframework.serialization.avro.AvroUtil;
import org.axonframework.serialization.json.JacksonConverter;
import org.axonframework.spring.serialization.avro.AvroSchemaScan;
import org.axonframework.spring.serialization.avro.ClasspathAvroSchemaLoader;
import org.axonframework.springboot.fixture.avro.test2.ComplexObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AvroConverter Spring integration test, verifying classpath scan and conversion use cases.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
class AvroSchemaStoreAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues(
                "axon.axonserver.enabled=false"
        );
    }

    @Test
    void avroConverterAutoConfigurationConstructsSchemaStore() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SchemaStore.class);
                    assertThat(context).getBean("defaultAxonSchemaStore").isInstanceOf(SchemaStore.class);
                    SchemaStore schemaStore = (SchemaStore) context.getBean("defaultAxonSchemaStore");
                    assertThat(
                            schemaStore.findByFingerprint(
                                    AvroUtil.fingerprint(ComplexObject.getClassSchema())
                            )
                    ).isEqualTo(ComplexObject.getClassSchema());
                    assertThat(context).hasSingleBean(ClasspathAvroSchemaLoader.class);
                    assertThat(context).getBean("specificRecordBaseClasspathAvroSchemaLoader")
                                       .isInstanceOf(ClasspathAvroSchemaLoader.class);
                });
    }

    @Test
    void avroConverterAutoConfigurationSkipsToConstructSchemaStoreIfAlreadyPresent() {
        testApplicationContext
                .withUserConfiguration(ContextWithCustomSchemaStore.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SchemaStore.class);
                    assertThat(context).getBean("defaultAxonSchemaStore").isInstanceOf(SchemaStore.class);
                    SchemaStore schemaStore = (SchemaStore) context.getBean("defaultAxonSchemaStore");
                    assertThat(schemaStore).isEqualTo(ContextWithCustomSchemaStore.store); // ours
                    assertThat(
                            schemaStore.findByFingerprint(
                                    AvroUtil.fingerprint(ComplexObject.getClassSchema())
                            )
                    ).isEqualTo(ComplexObject.getClassSchema());
                    assertThat(context).hasSingleBean(ClasspathAvroSchemaLoader.class);
                    assertThat(context).getBean("specificRecordBaseClasspathAvroSchemaLoader")
                                       .isInstanceOf(ClasspathAvroSchemaLoader.class);
                });
    }

    @Test
    void axonAutoConfigurationConstructsAvroConverterThatIsAbleToSerializeBecauseOfAnnotationScanRegisteredSchema() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro")
                .run(context -> {
                    Converter converter = context.getBean("eventConverter", Converter.class);
                    ComplexObject complexObject = ComplexObject.newBuilder()
                                                               .setValue1("foo")
                                                               .setValue2("bar")
                                                               .setValue3(42)
                                                               .build();
                    byte[] serialized = converter.convert(complexObject, byte[].class);

                    // back to original type
                    ComplexObject deserialized = converter.convert(serialized, ComplexObject.class);
                    assertEquals(complexObject, deserialized);

                    // back to generic type (aka intermediate)
                    GenericRecord genericRecord = converter.convert(serialized, GenericRecord.class);
                    assertThat(genericRecord).isNotNull();

                    // modify intermediate
                    genericRecord.put("value2", "newValue");

                    ComplexObject deserializedAfterUpcasting = converter.convert(
                            genericRecord,
                            ComplexObject.class
                    );

                    assertThat(deserializedAfterUpcasting).isNotNull();
                    assertEquals("newValue", deserializedAfterUpcasting.getValue2());
                });
    }


    @Test
    void axonAutoConfigurationConstructsAvroConverterThatIsNotAbleToSerializeBecauseNoSchemasAreFound() {
        testApplicationContext
                .withUserConfiguration(ContextWithoutSchemaScan.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro")
                .run(context -> {
                    Converter converter = context.getBean("eventConverter", Converter.class);
                    ComplexObject complexObject = ComplexObject.newBuilder()
                                                               .setValue1("foo")
                                                               .setValue2("bar")
                                                               .setValue3(42)
                                                               .build();
                    byte[] serialized = converter.convert(complexObject, byte[].class);
                    ConversionException serializationException = assertThrows(ConversionException.class,
                                                                              () -> converter.convert(serialized,
                                                                                                          ComplexObject.class));
                    assertEquals(AvroUtil.createExceptionNoSchemaFound(
                            ComplexObject.class,
                            AvroUtil.fingerprint(ComplexObject.getClassSchema())
                    ).getMessage(), serializationException.getMessage());
                });
    }


    @Test
    void axonAutoConfigurationDoesNotConstructAnythingIfNotEnabled() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=jackson"
                )
                .run(context -> {
                    assertThat(context).getBean(SchemaStore.class).isNull();
                    assertThat(context).getBean(ClasspathAvroSchemaLoader.class).isNull();
                });
    }

    @Test
    void axonAutoConfigurationFailsToConfigureGeneralAvroConverter() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=avro",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=jackson"
                )
                .run(context -> {
                    var startupFailure = context.getStartupFailure();
                    assertThat(startupFailure).isNotNull().isInstanceOf(BeanCreationException.class);
                    assertThat((BeanCreationException) startupFailure)
                            .extracting("beanName").isEqualTo("converter");
                    assertThat((BeanCreationException) startupFailure)
                            .extracting("cause") // BeanInstantiationException
                            .extracting("cause") // AxonConfigurationException
                            .isInstanceOf(AxonConfigurationException.class)
                            .extracting("message").isEqualTo(
                                    "Invalid converter type [AVRO] configured as general converter. "
                                            + "The Avro Converter can be used as message or event converter only.")

                    ;
                });
    }

    @Test
    void axonAutoConfigurationReturnsSameConverterIfOfTheSameType() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=avro"
                )
                .run(context -> {

                    Map<String, Converter> converters = context.getBeansOfType(Converter.class);
                    assertThat(converters).hasSize(3);
                    assertThat(converters.get("converter")).isInstanceOf(JacksonConverter.class);

                    assertThat(converters.get("messageConverter")).isInstanceOf(DelegatingMessageConverter.class);
                    DelegatingMessageConverter messageConverter = (DelegatingMessageConverter) converters.get("messageConverter");
                    assertThat(messageConverter.delegate()).isInstanceOf(AvroConverter.class);

                    assertThat(converters.get("eventConverter")).isInstanceOf(DelegatingEventConverter.class);
                    DelegatingEventConverter eventConverter = (DelegatingEventConverter) converters.get("eventConverter");
                    assertThat(eventConverter.delegate()).isInstanceOf(DelegatingMessageConverter.class);
                    DelegatingMessageConverter delegatingEventConverter = (DelegatingMessageConverter)eventConverter.delegate();
                    assertThat(delegatingEventConverter.delegate()).isInstanceOf(AvroConverter.class);

                    // check that this is the same object
                    assertThat(messageConverter.delegate() == delegatingEventConverter.delegate()).isTrue();
                });

        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=avro",
                        "axon.converter.event=avro"
                )
                .run(context -> {

                    Map<String, Converter> converters = context.getBeansOfType(Converter.class);
                    assertThat(converters).hasSize(3);
                    assertThat(converters.get("converter")).isInstanceOf(JacksonConverter.class);

                    assertThat(converters.get("messageConverter")).isInstanceOf(DelegatingMessageConverter.class);
                    DelegatingMessageConverter messageConverter = (DelegatingMessageConverter) converters.get("messageConverter");
                    assertThat(messageConverter.delegate()).isInstanceOf(AvroConverter.class);

                    assertThat(converters.get("eventConverter")).isInstanceOf(DelegatingEventConverter.class);
                    DelegatingEventConverter eventConverter = (DelegatingEventConverter) converters.get("eventConverter");
                    assertThat(eventConverter.delegate()).isInstanceOf(DelegatingMessageConverter.class);
                    DelegatingMessageConverter delegatingEventConverter = (DelegatingMessageConverter)eventConverter.delegate();
                    assertThat(delegatingEventConverter.delegate()).isInstanceOf(AvroConverter.class);

                    // check that this is the same object
                    assertThat(messageConverter.delegate() == delegatingEventConverter.delegate()).isTrue();
                });
    }


    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @AvroSchemaScan(
            basePackages = "org.axonframework.springboot.fixture.avro.test1",
            basePackageClasses = ComplexObject.class
    )
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
    private static class ContextWithCustomSchemaStore {

        public static SchemaStore.Cache store = new SchemaStore.Cache();

        static {
            store.addSchema(org.axonframework.springboot.fixture.avro.test1.ComplexObject.SCHEMA$);
            store.addSchema(org.axonframework.springboot.fixture.avro.test2.ComplexObject.SCHEMA$);
        }

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @Bean
        public SchemaStore defaultAxonSchemaStore() {
            return store;
        }

        @SpringBootApplication
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class ContextWithoutSchemaScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @SpringBootApplication
        private static class MainClass {

        }
    }
}
