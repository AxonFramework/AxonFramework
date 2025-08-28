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
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.avro.AvroSerializer;
import org.axonframework.serialization.avro.AvroUtil;
import org.axonframework.serialization.json.JacksonSerializer;
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
 * AvroSerializer Spring integration test, verifying classpath scan and serialize/deserialize.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
@Disabled("TODO #3496")
class AvroSchemaStoreAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues(
                "axon.axonserver.enabled=false"
        );
    }

    @Test
    void avroSerializerAutoConfigurationConstructsSchemaStore() {
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
    void avroSerializerAutoConfigurationSkipsToConstructSchemaStoreIfAlreadyPresent() {
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
    void axonAutoConfigurationConstructsAvroSerializerThatIsAbleToSerializeBecauseOfAnnotationScanRegisteredSchema() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro")
                .run(context -> {
                    Serializer serializer = context.getBean("eventSerializer", Serializer.class);
                    assertInstanceOf(AvroSerializer.class, serializer);
                    AvroSerializer avroSerializer = (AvroSerializer) serializer;
                    ComplexObject complexObject = ComplexObject.newBuilder()
                                                               .setValue1("foo")
                                                               .setValue2("bar")
                                                               .setValue3(42)
                                                               .build();
                    SerializedObject<byte[]> serialized = avroSerializer.serialize(complexObject,
                                                                                   byte[].class);

                    // back to original type
                    ComplexObject deserialized = avroSerializer.deserialize(serialized);
                    assertEquals(complexObject, deserialized);

                    // back to generic type (aka intermediate)
                    GenericRecord genericRecord = avroSerializer.deserialize(serialized);

                    // modify intermediate
                    genericRecord.put("value2", "newValue");

                    ComplexObject deserializedAfterUpcasting = avroSerializer.deserialize(
                            new SimpleSerializedObject<>(genericRecord,
                                                         GenericRecord.class,
                                                         serialized.getType())
                    );

                    assertEquals("newValue", deserializedAfterUpcasting.getValue2());
                });
    }


    @Test
    void axonAutoConfigurationConstructsAvroSerializerThatIsNotAbleToSerializeBecauseNoSchemasAreFound() {
        testApplicationContext
                .withUserConfiguration(ContextWithoutSchemaScan.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=avro")
                .run(context -> {
                    Serializer serializer = context.getBean("eventSerializer", Serializer.class);
                    assertInstanceOf(AvroSerializer.class, serializer);
                    AvroSerializer avroSerializer = (AvroSerializer) serializer;
                    ComplexObject complexObject = ComplexObject.newBuilder()
                                                               .setValue1("foo")
                                                               .setValue2("bar")
                                                               .setValue3(42)
                                                               .build();
                    SerializedObject<byte[]> serialized = avroSerializer.serialize(complexObject,
                                                                                   byte[].class);
                    SerializationException serializationException = assertThrows(SerializationException.class,
                                                                                 () -> avroSerializer.deserialize(
                                                                                         serialized));
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
    void axonAutoConfigurationFailsToConfigureGeneralAvroSerializer() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=avro",
                        "axon.converter.messages=jackson",
                        "axon.converter.events=jackson"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull().isInstanceOf(BeanCreationException.class);
                    assertThat((BeanCreationException) context.getStartupFailure().getCause())
                            .extracting("beanName").isEqualTo("serializer");
                    assertThat((BeanCreationException) context.getStartupFailure().getCause())
                            .extracting("cause")
                            .extracting("cause")
                            .isInstanceOf(AxonConfigurationException.class)
                            .extracting("message").isEqualTo(
                                    "Invalid serializer type [AVRO] configured as general serializer. "
                                            + "The Avro Serializer can be used as message or event serializer only.")

                    ;
                });
    }

    @Test
    void axonAutoConfigurationReturnsSameSerializerIfOfTheSameType() {
        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=avro"
                )
                .run(context -> {

                    Map<String, Serializer> serializers = context.getBeansOfType(Serializer.class);
                    assertThat(serializers).hasSize(3);
                    assertThat(serializers.get("serializer")).isInstanceOf(JacksonSerializer.class);
                    assertThat(serializers.get("messageSerializer")).isInstanceOf(AvroSerializer.class);
                    assertThat(serializers.get("eventSerializer")).isInstanceOf(AvroSerializer.class);
                    // check that this is the same object
                    assertThat(serializers.get("eventSerializer") == serializers.get("messageSerializer")).isTrue();
                });

        testApplicationContext
                .withUserConfiguration(DefaultContext.class)
                .withPropertyValues(
                        "axon.converter.general=jackson",
                        "axon.converter.messages=avro",
                        "axon.converter.event=avro"
                )
                .run(context -> {

                    Map<String, Serializer> serializers = context.getBeansOfType(Serializer.class);
                    assertThat(serializers).hasSize(3);
                    assertThat(serializers.get("serializer")).isInstanceOf(JacksonSerializer.class);
                    assertThat(serializers.get("messageSerializer")).isInstanceOf(AvroSerializer.class);
                    assertThat(serializers.get("eventSerializer")).isInstanceOf(AvroSerializer.class);
                    // check that this is the same object
                    assertThat(serializers.get("eventSerializer") == serializers.get("messageSerializer")).isTrue();
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
