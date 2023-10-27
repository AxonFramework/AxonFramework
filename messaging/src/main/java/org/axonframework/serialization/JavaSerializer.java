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

package org.axonframework.serialization;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Serializer implementation that uses Java serialization to serialize and deserialize object instances. This
 * implementation is very suitable if the life span of the serialized objects allows classes to remain unchanged. If
 * Class definitions need to be changed during the object's life cycle, another implementation, like the
 * {@link org.axonframework.serialization.xml.XStreamSerializer} might be a more suitable alternative.
 *
 * @author Allard Buijze
 * @since 2.0
 * @deprecated in favor of the {@link org.axonframework.serialization.xml.XStreamSerializer} and
 * {@link org.axonframework.serialization.json.JacksonSerializer}, as direct Java serialization is relatively error
 * prone. We hence strongly encourage to use either the XStream or Jackson solution in favor of this {@link Serializer}
 * implementation.
 */
@Deprecated
public class JavaSerializer implements Serializer {

    private final RevisionResolver revisionResolver;

    private final Converter converter = new ChainingConverter();

    /**
     * Instantiate a {@link JavaSerializer} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JavaSerializer} instance
     */
    protected JavaSerializer(Builder builder) {
        builder.validate();
        this.revisionResolver = builder.revisionResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link JavaSerializer}.
     * <p>
     * The {@link RevisionResolver} is defaulted to an {@link SerialVersionUIDRevisionResolver}.
     *
     * @return a Builder to be able to create a {@link JavaSerializer}
     */
    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings({"NonSerializableObjectPassedToObjectStream", "ThrowFromFinallyBlock"})
    @Override
    public <T> SerializedObject<T> serialize(Object instance, @Nonnull Class<T> expectedType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            try {
                oos.writeObject(instance);
            } finally {
                oos.flush();
            }
        } catch (IOException e) {
            throw new SerializationException("An exception occurred writing serialized data to the output stream", e);
        }
        T converted = converter.convert(baos.toByteArray(), expectedType);
        return new SimpleSerializedObject<>(converted, expectedType, getSerializedType(instance));
    }

    private SerializedType getSerializedType(Object instance) {
        SerializedType serializedType;
        if (instance == null) {
            serializedType = SimpleSerializedType.emptyType();
        } else {
            serializedType = new SimpleSerializedType(instance.getClass().getName(), revisionOf(instance.getClass()));
        }
        return serializedType;
    }

    @Override
    public <T> boolean canSerializeTo(@Nonnull Class<T> expectedRepresentation) {
        return converter.canConvert(byte[].class, expectedRepresentation);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> T deserialize(@Nonnull SerializedObject<S> serializedObject) {
        if (SerializedType.emptyType().equals(serializedObject.getType())) {
            return null;
        }
        if (UnknownSerializedType.class.isAssignableFrom(classForType(serializedObject.getType()))) {
            return (T) new UnknownSerializedType(this, serializedObject);
        }

        SerializedObject<InputStream> converted =
                converter.convert(serializedObject, InputStream.class);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(converted.getData());
            return (T) ois.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new SerializationException("An error occurred while deserializing: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(ois);
        }
    }

    @Override
    public Class classForType(@Nonnull SerializedType type) {
        if (SerializedType.emptyType().equals(type)) {
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
        return new SimpleSerializedType(type.getName(), revisionOf(type));
    }

    @Override
    public Converter getConverter() {
        return converter;
    }

    private String revisionOf(Class<?> type) {
        return revisionResolver.revisionOf(type);
    }

    /**
     * Builder class to instantiate a {@link JavaSerializer}.
     * <p>
     * The {@link RevisionResolver} is defaulted to an {@link SerialVersionUIDRevisionResolver}.
     */
    public static class Builder {

        private RevisionResolver revisionResolver = new SerialVersionUIDRevisionResolver();

        /**
         * Sets the {@link RevisionResolver} used to resolve the revision from an object to be serialized. Defaults to
         * an {@link SerialVersionUIDRevisionResolver} which resolves {@code serialVersionUID} of a class as the
         * revision.
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
         * Initializes a {@link JavaSerializer} as specified through this Builder.
         *
         * @return a {@link JavaSerializer} as specified through this Builder
         */
        public JavaSerializer build() {
            return new JavaSerializer(this);
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
