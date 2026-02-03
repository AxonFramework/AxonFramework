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

package org.axonframework.serialization;

import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.jackson3.Jackson3Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.serialization.xml.XStreamSerializer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.TypeMatcher;

import java.beans.ConstructorProperties;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;

import static com.fasterxml.jackson.dataformat.cbor.CBORGenerator.Feature.WRITE_TYPE_HEADER;

/**
 * Enumeration of serializers for testing purposes.
 *
 * @author JohT
 */
@SuppressWarnings("unused")
public enum TestSerializer {

    JAVA {
        @SuppressWarnings("deprecation")
        private final Serializer serializer = JavaSerializer.builder().build();

        @Override
        public Serializer getSerializer() {
            return serializer;
        }

        @Override
        protected byte[] serialize(Object object) {
            return getSerializer().serialize(object, byte[].class).getData();
        }

        @Override
        protected <T> T deserialize(byte[] serialized, Class<T> type) {
            return getSerializer().deserialize(asSerializedData(serialized, type));
        }
    },
    XSTREAM {
        private final Serializer serializer = createSerializer();

        private XStreamSerializer createSerializer() {
            return XStreamSerializer.builder()
                                    .xStream(new XStream(new CompactDriver()))
                                    .build();
        }

        @Override
        public Serializer getSerializer() {
            return serializer;
        }
    },
    JACKSON {
        private Serializer serializer;

        @Override
        public synchronized Serializer getSerializer() {
            if (serializer == null) {
                serializer = JacksonSerializer.defaultSerializer();
            }

            return serializer;
        }
    },
    JACKSON3 {
        private Serializer serializer;

        @Override
        public synchronized Serializer getSerializer() {
            if (serializer == null) {
                serializer = Jackson3Serializer.builder()
                    .jsonMapperBuilderCustomizer(builder -> {
                        builder.enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
                        builder.polymorphicTypeValidator(
                            BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("org.axonframework.")
                                .build()
                        );
                    })
                    .build();
            }

            return serializer;
        }
    },
    CBOR {
        private Serializer serializer;

        @Override
        public synchronized Serializer getSerializer() {
            if (serializer == null) {
                serializer = JacksonSerializer.builder()
                    .objectMapper(CBORMapper.builder().findAndAddModules().build())
                    .build();
            }

            return serializer;
        }
    },
    JACKSON_ONLY_ACCEPT_CONSTRUCTOR_PARAMETERS {
        private Serializer serializer;

        @Override
        public synchronized Serializer getSerializer() {
            if (serializer == null) {
                serializer = JacksonSerializer.builder()
                    .objectMapper(OnlyAcceptConstructorPropertiesAnnotation.attachTo(new ObjectMapper()))
                    .build();
            }

            return serializer;
        }
    },
    JACKSON_IGNORE_NULL {
        private Serializer serializer;

        @Override
        public synchronized Serializer getSerializer() {
            if (serializer == null) {
                ObjectMapper objectMapper = new ObjectMapper()
                    .setSerializationInclusion(Include.NON_NULL);

                serializer = JacksonSerializer.builder()
                    .objectMapper(objectMapper)
                    .build();
            }

            return serializer;
        }
    };

    protected byte[] serialize(Object object) {
        return getSerializer().serialize(object, byte[].class).getData();
    }

    protected <T> T deserialize(byte[] serialized, Class<T> type) {
        return getSerializer().deserialize(asSerializedData(serialized, type));
    }

    public abstract Serializer getSerializer();

    @SuppressWarnings("unchecked")
    public <T> T serializeDeserialize(T object) {
        return deserialize(serialize(object), (Class<T>) object.getClass());
    }

    public static Collection<TestSerializer> all() {
        return EnumSet.allOf(TestSerializer.class);
    }

    static <T> SerializedObject<byte[]> asSerializedData(byte[] serialized, Class<T> type) {
        SimpleSerializedType serializedType = new SimpleSerializedType(type.getName(), null);
        return new SimpleSerializedObject<>(serialized, byte[].class, serializedType);
    }

    private static class OnlyAcceptConstructorPropertiesAnnotation extends JacksonAnnotationIntrospector {

        private static final long serialVersionUID = 1L;

        public static ObjectMapper attachTo(ObjectMapper objectMapper) {
            return objectMapper.setAnnotationIntrospector(new OnlyAcceptConstructorPropertiesAnnotation());
        }

        @Override
        public Mode findCreatorAnnotation(MapperConfig<?> config, Annotated annotated) {
            return (annotated.hasAnnotation(ConstructorProperties.class))
                    ? super.findCreatorAnnotation(config, annotated)
                    : Mode.DISABLED;
        }
    }
}