/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.serialization.avro;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.util.ClassUtils;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serialization.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Serializer providing support for Apache Avro and using Single Object Encoded binary encoding.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroSerializer implements Serializer {

    private final RevisionResolver revisionResolver;
    private final ChainingConverter converter = new ChainingConverter();
    private final List<AvroSerializerStrategy> serializerStrategies = new ArrayList<>();
    /*
     * Responsible for everything that is NOT Avro (e.g. MetaData).
     */
    private final Serializer serializerDelegate;

    /**
     * Creates the serializer instance.
     *
     * @param builder builder containing relevant settings.
     */
    protected AvroSerializer(@Nonnull Builder builder) {
        builder.validate();
        this.revisionResolver = builder.revisionResolver;
        this.serializerDelegate = builder.serializerDelegate;
        this.serializerStrategies.addAll(builder.serializerStrategies);
        this.serializerStrategies.add(new SpecificRecordBaseSerializerStrategy(
                        builder.schemaStore,
                        this.revisionResolver
                )
        );
        this.converter.registerConverter(new ByteArrayToGenericRecordConverter(builder.schemaStore));
    }

    /**
     * Creates a builder for Avro Serializer.
     *
     * @return fluent builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SerializedObject<T> serialize(Object object, @Nonnull Class<T> expectedRepresentation) {
        // assume: expectedRepresentation is byte[] (or possibly String), nothing else.
        // TODO: Question is this correct? What is the contract of null handling?
        Objects.requireNonNull(object, "Can't serialize a null object");

        Optional<AvroSerializerStrategy> serializerStrategy = serializerStrategies
                .stream()
                .filter(it -> it.test(object.getClass()))
                .findFirst();

        if (serializerStrategy.isPresent()) {
            if (byte[].class.equals(expectedRepresentation)) {
                return (SerializedObject<T>) serializerStrategy.get().serializeToSingleObjectEncoded(object);
            }
        }

        // when we do not have an avro record, we delegate to jackson/xstream/...
        return serializerDelegate.serialize(object, expectedRepresentation);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> T deserialize(@Nonnull SerializedObject<S> serializedObject) {
        if (SerializedType.isEmptyType(serializedObject.getType())) {
            return null;
        }
        Class<?> payloadType = classForType(serializedObject.getType());
        if (UnknownSerializedType.class.isAssignableFrom(payloadType)) {
            //noinspection unchecked
            return (T) new UnknownSerializedType(this, serializedObject);
        }

        Optional<AvroSerializerStrategy> serializerStrategy = serializerStrategies
                .stream()
                .filter(it -> it.test(payloadType))
                .findFirst();

        if (serializerStrategy.isPresent()) {

            // with upcasting:
            // GenericRecord -> T
            if (serializedObject.getContentType().equals(GenericRecord.class)) {
                return (T) serializerStrategy.get().deserializeFromGenericRecord(
                        (SerializedObject<GenericRecord>) serializedObject, payloadType
                );
            }

            // without upcasting:
            // byte[] -> T
            SerializedObject<byte[]> bytesSerialized = converter.convert(serializedObject, byte[].class);

            return (T) serializerStrategy.get().deserializeFromSingleObjectEncoded(bytesSerialized, payloadType);
        }

        // not an avro type, let delegate deal with it.
        return serializerDelegate.deserialize(serializedObject);
    }

    @Override
    public <T> boolean canSerializeTo(@Nonnull Class<T> expectedRepresentation) {
        return GenericRecord.class.equals(expectedRepresentation)
                || getConverter().canConvert(byte[].class, expectedRepresentation)
                || serializerDelegate.canSerializeTo(expectedRepresentation);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class classForType(@Nonnull SerializedType type) {
        if (SimpleSerializedType.emptyType().equals(type)) {
            return Void.class;
        }
        try {
            // TODO check if SpecificData#classForSchema is a better fit
            return ClassUtils.forName(type.getName());
        } catch (ClassNotFoundException e) {
            return UnknownSerializedType.class;
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public SerializedType typeForClass(@Nullable Class type) {
        if (type == null || Void.TYPE.equals(type) || Void.class.equals(type)) {
            return SimpleSerializedType.emptyType();
        }
        return new SimpleSerializedType(type.getName(), revisionResolver.revisionOf(type));
    }

    @Override
    public Converter getConverter() {
        // TODO Question: Do we have to build a union with the serializerDelegate.getConverter()?
        return converter;
    }

    /**
     * Builder to set up Avro Serializer.
     */
    public static class Builder {

        private final List<AvroSerializerStrategy> serializerStrategies = new ArrayList<>();
        private RevisionResolver revisionResolver;
        private SchemaStore schemaStore;
        private Serializer serializerDelegate;

        /**
         * Sets revision resolver.
         *
         * @param revisionResolver revision resolver to use.
         * @return builder instance.
         */
        public Builder revisionResolver(RevisionResolver revisionResolver) {
            this.revisionResolver = revisionResolver;
            return this;
        }

        /**
         * Sets schema store for Avro schema resolution.
         *
         * @param schemaStore schema store instance.
         * @return builder instance.
         */
        public Builder schemaStore(SchemaStore schemaStore) {
            this.schemaStore = schemaStore;
            return this;
        }

        /**
         * Sets serializer delegate, used for all types which can't be converted to Avro.
         *
         * @param serializerDelegate serializer delegate.
         * @return builder instance.
         */
        public Builder serializerDelegate(Serializer serializerDelegate) {
            this.serializerDelegate = serializerDelegate;
            return this;
        }

        /**
         * Adds a serialization strategy.
         *
         * @param strategy strategy responsible for the serialization and deserialization.
         * @return builder instance.
         */
        public Builder addSerializerStrategy(AvroSerializerStrategy strategy) {
            this.serializerStrategies.add(strategy);
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(revisionResolver, "RevisionResolver is mandatory");
            assertNonNull(schemaStore, "SchemaStore is mandatory");
            assertNonNull(serializerDelegate, "SerializerDelegate is mandatory");
        }

        /**
         * Creates an Avro Serializer instance.
         *
         * @return working instance.
         */
        public AvroSerializer build() {
            return new AvroSerializer(this);
        }
    }
}
