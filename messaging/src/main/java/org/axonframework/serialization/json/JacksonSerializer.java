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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.ChainingContentTypeConverter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializationException;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.UnknownSerializedType;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Serializer implementation that uses Jackson to serialize objects into a JSON format. Although the Jackson serializer
 * requires classes to be compatible with this specific serializer, it provides much more compact serialization, while
 * still being human-readable.
 *
 * @author Allard Buijze
 * @since 2.2
 * @deprecated in favor of the {@link JacksonConverter}.
 */
@Deprecated(forRemoval = true, since = "5.0.0")
public class JacksonSerializer implements Serializer {

    private final RevisionResolver revisionResolver;
    private final Converter converter;
    private final ObjectMapper objectMapper;
    private final Set<String> unknownClasses = new ConcurrentSkipListSet<>();
    private final boolean cacheUnknownClasses;

    /**
     * Instantiate a Builder to be able to create a {@link JacksonSerializer}.
     * <p>
     * The {@link RevisionResolver} is defaulted to an {@link AnnotationRevisionResolver}, the {@link Converter} to a
     * {@link ChainingContentTypeConverter}, the {@link ObjectMapper} defaults to a {@link ObjectMapper#ObjectMapper()}
     * result and the {@link ClassLoader} to the ClassLoader of {@code this} class.
     * <p>
     * Upon instantiation, the ObjectMapper will get two modules registered to it by default, (1) the
     * {@link MetaDataDeserializer} and the (2) {@link JavaTimeModule}. Lastly, if the provided converter is of type
     * ChainingContentTypeConverter, the {@link JacksonSerializer#registerConverters} is performed to automatically add
     * the {@link JsonNodeToByteArrayConverter} and {@link ByteArrayToJsonNodeConverter}.
     *
     * @return a Builder to be able to create a {@link JacksonSerializer}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a default {@link JacksonSerializer}.
     * <p>
     * The {@link RevisionResolver} is defaulted to an {@link AnnotationRevisionResolver}, the {@link Converter} to a
     * {@link ChainingContentTypeConverter}, the {@link ObjectMapper} defaults to a {@link ObjectMapper#ObjectMapper()}
     * result and the {@link ClassLoader} to the ClassLoader of {@code this} class.
     * <p>
     * Upon instantiation, the ObjectMapper will get two modules registered to it by default, (1) the
     * {@link MetaDataDeserializer} and the (2) {@link JavaTimeModule}. Lastly, if the provided converter is of type
     * ChainingContentTypeConverter, the {@link JacksonSerializer#registerConverters} is performed to automatically add
     * the {@link JsonNodeToByteArrayConverter} and {@link ByteArrayToJsonNodeConverter}.
     *
     * @return a {@link JacksonSerializer}
     */
    public static JacksonSerializer defaultSerializer() {
        return builder().build();
    }

    /**
     * Instantiate a {@link JacksonSerializer} based on the fields contained in the {@link Builder}.
     * <p>
     * Upon instantiation, the ObjectMapper will get two modules registered to it by default, (1) the
     * {@link MetaDataDeserializer} and the (2) {@link JavaTimeModule}. Lastly, if the provided converter is of type
     * ChainingContentTypeConverter, the {@link JacksonSerializer#registerConverters} is performed to automatically add
     * the {@link JsonNodeToByteArrayConverter} and {@link ByteArrayToJsonNodeConverter}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JacksonSerializer} instance
     */
    protected JacksonSerializer(Builder builder) {
        builder.validate();
        this.revisionResolver = builder.revisionResolver;
        this.converter = builder.converter;
        this.objectMapper = builder.objectMapper;

        this.objectMapper.registerModule(
                new SimpleModule("Axon-Jackson Module").addDeserializer(MetaData.class, new MetaDataDeserializer())
        );
        this.objectMapper.registerModule(new JavaTimeModule());
        if (converter instanceof ChainingContentTypeConverter) {
            registerConverters((ChainingContentTypeConverter) converter);
        }
        this.cacheUnknownClasses = builder.cacheUnknownClasses;
    }

    /**
     * Registers converters with the given {@code converter} which depend on the actual contents of the serialized form
     * to represent a JSON format.
     *
     * @param converter The ChainingContentTypeConverter instance to register the converters with.
     */
    protected void registerConverters(ChainingContentTypeConverter converter) {
        converter.registerConverter(new JsonNodeToByteArrayConverter(objectMapper));
        converter.registerConverter(new ByteArrayToJsonNodeConverter(objectMapper));
        converter.registerConverter(new JsonNodeToObjectNodeConverter());
        converter.registerConverter(new ObjectNodeToJsonNodeConverter());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T convert(@Nullable Object source, @Nonnull Type targetRepresentation) {
        if (source == null) {
            return null;
        }
        Class<?> sourceType = source.getClass();
        JavaType valueType = objectMapper.constructType(targetRepresentation);
        if (converter.canConvert(sourceType, valueType.getRawClass())) {
            return (T) converter.convert(source, valueType.getRawClass());
        } else if (converter.canConvert(sourceType, byte[].class)) {
            // must be a serialized form
            byte[] bytes = converter.convert(source, byte[].class);
            try {
                return objectMapper.readValue(bytes, valueType);
            } catch (IOException e) {
                throw new SerializationException(
                        "Exception when trying to convert object of type '" + sourceType.getTypeName() + "' to '"
                                + targetRepresentation.getTypeName() + "'", e);
            }
        } else if (converter.canConvert(valueType.getRawClass(),
                                        byte[].class)) {
            // the target is a serialized form
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(source);
                return (T) converter.convert(bytes, valueType.getRawClass());
            } catch (JsonProcessingException e) {
                throw new SerializationException(
                        "Exception when trying to convert object of type '" + sourceType.getTypeName() + "' to '"
                                + targetRepresentation.getTypeName() + "'", e);
            }
        } else {
            return objectMapper.convertValue(source, valueType);
        }
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, @Nonnull Class<T> expectedRepresentation) {
        try {
            if (String.class.equals(expectedRepresentation)) {
                //noinspection unchecked
                return new SimpleSerializedObject<>((T) getWriter().writeValueAsString(object), expectedRepresentation,
                                                    typeForClass(ObjectUtils.nullSafeTypeOf(object)));
            }

            byte[] serializedBytes = getWriter().writeValueAsBytes(object);
            T serializedContent = converter.convert(serializedBytes, expectedRepresentation);
            return new SimpleSerializedObject<>(serializedContent, expectedRepresentation,
                                                typeForClass(ObjectUtils.nullSafeTypeOf(object)));
        } catch (JsonProcessingException e) {
            throw new SerializationException("Unable to serialize object", e);
        }
    }

    /**
     * Returns the ObjectMapper used by this serializer, allowing for configuration of the serialization settings.
     *
     * @return the ObjectMapper instance used by his serializer
     */
    public final ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Provides the ObjectWriter, with which objects are serialized to JSON form. This method may be overridden to
     * change the configuration of the writer to use.
     *
     * @return The writer to serialize objects with
     */
    protected ObjectWriter getWriter() {
        return objectMapper.writer();
    }

    /**
     * Provides the ObjectReader, with which objects are read from the JSON form. This method may be overridden to
     * change the configuration of the reader to use.
     *
     * @param type The type of object to create a reader for
     * @return The writer to serialize objects with
     */
    protected ObjectReader getReader(Class<?> type) {
        return objectMapper.readerFor(type);
    }

    @Override
    public <T> boolean canSerializeTo(@Nonnull Class<T> expectedRepresentation) {
        return JsonNode.class.equals(expectedRepresentation) || String.class.equals(expectedRepresentation) ||
                converter.canConvert(byte[].class, expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(@Nonnull SerializedObject<S> serializedObject) {
        try {
            if (SerializedType.emptyType().equals(serializedObject.getType())) {
                return null;
            }
            Class<?> type = classForType(serializedObject.getType());
            if (UnknownSerializedType.class.isAssignableFrom(type)) {
                //noinspection unchecked
                return (T) new UnknownSerializedType(this, serializedObject);
            }
            if (JsonNode.class.equals(serializedObject.getContentType())) {
                return getReader(type)
                        .readValue((JsonNode) serializedObject.getData());
            }
            SerializedObject<byte[]> byteSerialized = converter.convert(serializedObject, byte[].class);
            return getReader(type).readValue(byteSerialized.getData());
        } catch (IOException e) {
            throw new SerializationException("Error while deserializing object", e);
        }
    }

    @Override
    public Class classForType(@Nonnull SerializedType type) {
        if (SimpleSerializedType.emptyType().equals(type)) {
            return Void.class;
        }
        String className = resolveClassName(type);
        if (cacheUnknownClasses && unknownClasses.contains(className)) {
            return UnknownSerializedType.class;
        }
        try {
            return objectMapper.getTypeFactory().findClass(className);
        } catch (ClassNotFoundException e) {
            unknownClasses.add(className);
            return UnknownSerializedType.class;
        }
    }

    /**
     * Resolve the class name from the given {@code serializedType}. This method may be overridden to customize the
     * names used to denote certain classes, for example, by leaving out a certain base package for brevity.
     *
     * @param serializedType The serialized type to resolve the class name for
     * @return The fully qualified name of the class to load
     */
    protected String resolveClassName(SerializedType serializedType) {
        return serializedType.getName();
    }

    @Override
    public SerializedType typeForClass(Class type) {
        if (type == null || Void.TYPE.equals(type) || Void.class.equals(type)) {
            return SimpleSerializedType.emptyType();
        }
        return new SimpleSerializedType(type.getName(), revisionResolver.revisionOf(type));
    }

    @Override
    public Converter getConverter() {
        return converter;
    }

    /**
     * Returns the revision resolver used by this serializer.
     *
     * @return the revision resolver
     */
    protected RevisionResolver getRevisionResolver() {
        return revisionResolver;
    }

    /**
     * Builder class to instantiate a {@link JacksonSerializer}.
     * <p>
     * The {@link RevisionResolver} is defaulted to an {@link AnnotationRevisionResolver}, the {@link Converter} to a
     * {@link ChainingContentTypeConverter}, the {@link ObjectMapper} defaults to a {@link ObjectMapper#ObjectMapper()}
     * result and the {@link ClassLoader} to the ClassLoader of {@code this} class.
     * <p>
     * Upon instantiation, the ObjectMapper will get two modules registered to it by default, (1) the
     * {@link MetaDataDeserializer} and the (2) {@link JavaTimeModule}. Lastly, if the provided converter is of type
     * ChainingContentTypeConverter, the {@link JacksonSerializer#registerConverters} is performed to automatically add
     * the {@link JsonNodeToByteArrayConverter} and {@link ByteArrayToJsonNodeConverter}.
     */
    public static class Builder {

        private RevisionResolver revisionResolver = new AnnotationRevisionResolver();
        private Converter converter = new ChainingContentTypeConverter();
        private ObjectMapper objectMapper = new ObjectMapper();
        private boolean lenientDeserialization = false;
        private boolean defaultTyping = false;
        private ClassLoader classLoader;
        private boolean cacheUnknownClasses = true;

        /**
         * Sets the {@link RevisionResolver} used to resolve the revision from an object to be serialized. Defaults to
         * an {@link AnnotationRevisionResolver} which resolves the revision based on the contents of the
         * {@link org.axonframework.serialization.Revision} annotation on the serialized classes.
         *
         * @param revisionResolver a {@link RevisionResolver} used to resolve the revision from an object to be
         *                         serialized
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder revisionResolver(RevisionResolver revisionResolver) {
            assertNonNull(revisionResolver, "RevisionResolver may not be null");
            this.revisionResolver = revisionResolver;
            return this;
        }

        /**
         * Sets the {@link Converter} used as a converter factory providing converter instances utilized by upcasters to
         * convert between different content types. Defaults to a {@link ChainingContentTypeConverter}.
         *
         * @param converter a {@link Converter} used as a converter factory providing converter instances utilized by
         *                  upcasters to convert between different content types
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder converter(Converter converter) {
            assertNonNull(converter, "Converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Sets the {@link ObjectMapper} used to serialize and parse the objects to JSON. This ObjectMapper allows for
         * customization of the serialized form. Defaults to the output of {@link ObjectMapper#ObjectMapper()}.
         *
         * @param objectMapper an {@link ObjectMapper} used to serialize and parse the objects to JSON
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            assertNonNull(objectMapper, "ObjectMapper may not be null");
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Sets the {@link ClassLoader} used as an override for default {@code ClassLoader} used in the
         * {@link ObjectMapper}. The same solution could thus be achieved by configuring the `ObjectMapper` instance
         * directly.
         *
         * @param classLoader the {@link ClassLoader} used to load classes with when deserializing
         * @return the current Builder instance, for fluent interfacing
         * @see #objectMapper(ObjectMapper)
         * @see com.fasterxml.jackson.databind.type.TypeFactory#withClassLoader(ClassLoader)
         */
        public Builder classLoader(ClassLoader classLoader) {
            assertNonNull(classLoader, "ClassLoader may not be null");
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Configures the underlying ObjectMapper to be lenient when deserializing JSON into Java objects. Specifically,
         * enables the {@link DeserializationFeature#ACCEPT_SINGLE_VALUE_AS_ARRAY} and
         * {@link DeserializationFeature#UNWRAP_SINGLE_VALUE_ARRAYS}, and disables
         * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder lenientDeserialization() {
            lenientDeserialization = true;
            return this;
        }

        /**
         * Disables the caching of the names for classes which couldn't be resolved to a class.
         * <p>
         * It is recommended to disable this in case the class loading is dynamic, and classes that are not available at
         * one point in time, may become available at any time later.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder disableCachingOfUnknownClasses() {
            this.cacheUnknownClasses = false;
            return this;
        }

        /**
         * Configures the underlying {@link ObjectMapper} to include type information when serializing Java objects into
         * JSON. Specifically, it calls {@link ObjectMapper#enableDefaultTyping(ObjectMapper.DefaultTyping)} method,
         * using {@link ObjectMapper.DefaultTyping#NON_CONCRETE_AND_ARRAYS}. This can be toggled on to allow
         * {@link java.util.Collection}s of objects, for example query {@link java.util.List} responses, to
         * automatically include the types without require the use of
         * {@link com.fasterxml.jackson.annotation.JsonTypeInfo} on the objects themselves.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultTyping() {
            defaultTyping = true;
            return this;
        }

        /**
         * Initializes a {@link JacksonSerializer} as specified through this Builder.
         *
         * @return a {@link JacksonSerializer} as specified through this Builder
         */
        public JacksonSerializer build() {
            if (lenientDeserialization) {
                objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
                objectMapper.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS);
                objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            }
            if (defaultTyping) {
                objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                                                   ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS);
            }
            if (classLoader != null) {
                objectMapper.setTypeFactory(objectMapper.getTypeFactory().withClassLoader(classLoader));
            }
            return new JacksonSerializer(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            // Method kept for overriding
        }
    }
}
