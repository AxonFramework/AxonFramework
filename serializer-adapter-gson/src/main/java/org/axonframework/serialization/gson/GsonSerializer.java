/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.serialization.gson;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ObjectUtils;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Serializer implementation that uses Gson to serialize objects into a JSON format. Although the Gson serializer
 * requires classes to be compatible with this specific serializer, it providers much more compact serialization, while
 * still being human readable.
 *
 * @since 4.4
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class GsonSerializer implements Serializer {

    private static final Logger LOG = LoggerFactory.getLogger(GsonSerializer.class);

    private final RevisionResolver revisionResolver;
    private final Converter converter;
    private final Gson gson;

    public static Builder builder() {
        return new Builder();
    }

    public static GsonSerializer defaultSerializer() {
        return builder().build();
    }

    protected GsonSerializer(Builder builder) {
        builder.validate();

        this.revisionResolver = builder.revisionResolver;
        this.converter = builder.converter;

        this.gson = builder.gsonBuilder.create();

        if (converter instanceof ChainingConverter) {
            registerConverters((ChainingConverter) converter);
        }
    }

    protected void registerConverters(ChainingConverter converter) {
        converter.registerConverter(new JsonElementToByteArrayConverter(this.gson));
        converter.registerConverter(new org.axonframework.serialization.gson.ByteArrayToJsonNodeConverter(this.gson));
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedRepresentation) {
        SerializedType serializedType = typeForClass(ObjectUtils.nullSafeTypeOf(object));

        if (JsonElement.class.equals(expectedRepresentation)) {
            return new SimpleSerializedObject<>(
                    (T) gson.toJsonTree(object, object.getClass()),
                    expectedRepresentation,
                    serializedType
            );
        }

        String jsonString = gson.toJson(object);
        if (String.class.equals(expectedRepresentation)) {
            return new SimpleSerializedObject<>((T) jsonString, expectedRepresentation,
                    serializedType);
        } else {
            byte[] serializedBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            T serializedContent = converter.convert(serializedBytes, expectedRepresentation);
            return new SimpleSerializedObject<>(serializedContent, expectedRepresentation,
                    serializedType);
        }
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        try {
            return gson.getAdapter(expectedRepresentation) != null;
        } catch (IllegalArgumentException ex) {
            LOG.debug("cannot serialize " + expectedRepresentation, ex);
            return false;
        }
    }

    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        Class<T> type = classForType(serializedObject.getType());

        if (Void.class.equals(type)) {
            return null;
        } else if (UnknownSerializedType.class.isAssignableFrom(type)) {
            return (T) new UnknownSerializedType(this, serializedObject);
        } else if (JsonElement.class.equals(serializedObject.getContentType())) {
            return gson.fromJson(
                    ((JsonElement) serializedObject.getData()),
                    type);
        } else {
            SerializedObject<byte[]> byteSerialized = converter.convert(serializedObject, byte[].class);
            return gson.fromJson(
                    new String(byteSerialized.getData(), StandardCharsets.UTF_8),
                    type);
        }
    }

    @Override
    public Class classForType(SerializedType type) {
        if (SimpleSerializedType.emptyType().equals(type)) {
            return Void.class;
        }

        try {
            return Class.forName(type.getName());
        } catch (ClassNotFoundException e) {
            return UnknownSerializedType.class;
        }
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

    public static class Builder implements SerializerBuilder{

        private RevisionResolver revisionResolver = new AnnotationRevisionResolver();
        private Converter converter = new ChainingConverter();
        private GsonBuilder gsonBuilder = new GsonBuilder();
        private boolean lenientDeserialization;

        public Builder revisionResolver(RevisionResolver revisionResolver) {
            assertNonNull(revisionResolver, "RevisionResolver may not be null");
            this.revisionResolver = revisionResolver;
            return this;
        }

        @Override
        public SerializerBuilder beanClassLoader(ClassLoader beanClassLoader) {
            // ignore
            return this;
        }

        @Override
        public SerializerBuilder externalInjections(Map<Class, Object> beansByClass) {
            // ignore
            return this;
        }

        public Builder converter(Converter converter) {
            assertNonNull(converter, "Converter may not be null");
            this.converter = converter;
            return this;
        }

        public Builder gsonBuilder(GsonBuilder gsonBuilder) {
            assertNonNull(gsonBuilder, "GsonBuilder may not be null");
            this.gsonBuilder = gsonBuilder;
            return this;
        }

        public Builder lenientDeserialization() {
            this.lenientDeserialization = true;
            return this;
        }

        public GsonSerializer build() {
            if (lenientDeserialization) {
                this.gsonBuilder.setLenient();
            }

            this.gsonBuilder.registerTypeAdapter(MetaData.class, new MetaDataDeserializer());
            this.gsonBuilder.registerTypeAdapter(Class.class, new ClassTypeAdapter());

            // register javatime-serializers
            Converters.registerAll(this.gsonBuilder);

            return new GsonSerializer(this);
        }

        protected void validate() throws AxonConfigurationException {
            // Kept to be overridden
        }
    }
}
