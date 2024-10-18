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

/**
 *
 */
public class AvroSerializer implements Serializer {

    private final RevisionResolver revisionResolver;
    private final ChainingConverter converter = new ChainingConverter();
    private final List<AvroSerializerStrategy> serializerStrategies = new ArrayList<>();
    /**
     * Responsible for everything that is NOT avro-java (SpecificRecordBase) or avro4k (KotlinX-Serialization).
     */
    private final Serializer serializerDelegate;

    protected AvroSerializer(@Nonnull Builder builder) {
        builder.validate();
        this.revisionResolver = builder.revisionResolver;
        this.serializerDelegate = builder().serializerDelegate;
        this.serializerStrategies.addAll(builder.serializerStrategies);
        this.serializerStrategies.add(new SpecificRecordBaseSerializerStrategy(
                builder().schemaStore,
                this.revisionResolver
            )
        );
        this.converter.registerConverter(new ByteArrayToGenericRecordConverter(builder().schemaStore));
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SerializedObject<T> serialize(Object object, @Nonnull Class<T> expectedRepresentation) {
        // assume: expectedRepresentation is byte[] (or possibly String), nothing else.
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

    public static class Builder {

        private final List<AvroSerializerStrategy> serializerStrategies = new ArrayList<>();
        private RevisionResolver revisionResolver;
        private SchemaStore schemaStore;
        private Serializer serializerDelegate;

        public Builder revisionResolver(RevisionResolver revisionResolver) {
            this.revisionResolver = revisionResolver;
            return this;
        }

        public Builder schemaStore(SchemaStore schemaStore) {
            this.schemaStore = schemaStore;
            return this;
        }

        public Builder serializerDelegate(Serializer serializerDelegate) {
            this.serializerDelegate = serializerDelegate;
            return this;
        }

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
            Objects.requireNonNull(revisionResolver);
            Objects.requireNonNull(schemaStore);
            Objects.requireNonNull(serializerDelegate);
        }

        public AvroSerializer build() {
            return new AvroSerializer(this);
        }
    }

}
