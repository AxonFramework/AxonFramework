package org.axonframework.serialization.avro;

import org.apache.avro.generic.GenericRecord;
import org.axonframework.serialization.SerializedObject;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public interface AvroSerializerStrategy extends Predicate<Class<?>> {

    /**
     * Determines if strategy supports given payloadType, this means that we have either a SpecificRecordBase generated
     * from schema using apchae-avro-maven-plugin or a kotlinx serializable class, written using avro4k.
     *
     * @param payloadType for example BankAccountCreated.class
     * @return <code>true</code> if type is supported by this strategy
     */
    @Override
    boolean test(Class<?> payloadType);

    /**
     * Serializes object to byte array using Avro single-object-encoding.
     *
     * @param object object to serialize.
     * @return byte array.
     */
    @Nonnull
    SerializedObject<byte[]> serializeToSingleObjectEncoded(@Nonnull Object object);

    /**
     * Deserializes from single object encoded byte array.
     *
     * @param serializedObject serialized object containing single-object-encoded bytes.
     * @param type             class of resulting object.
     * @param <T>              payload type to deserialize to.
     * @return deserialized object.
     */
    @Nonnull
    <T> T deserializeFromSingleObjectEncoded(@Nonnull SerializedObject<byte[]> serializedObject, @Nonnull Class<T> type);

    /**
     * Deserializes from Apache Avro generic record (intermediate representation).
     *
     * @param serializedObject serialized object containing the generic record.
     * @param type             class of resulting object.
     * @param <T>              payload type to deserialize to.
     * @return deserialized object.
     */
    <T> T deserializeFromGenericRecord(SerializedObject<GenericRecord> serializedObject, Class<T> type);

}
