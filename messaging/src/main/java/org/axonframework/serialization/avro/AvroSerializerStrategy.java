package org.axonframework.serialization.avro;

import org.apache.avro.generic.GenericRecord;
import org.axonframework.serialization.SerializedObject;

import java.util.function.Predicate;

public interface AvroSerializerStrategy extends Predicate<Class<?>> {

    SerializedObject<byte[]> serializeToSingleObjectEncoded(Object object);

    Object deserializeFromSingleObjectEncoded(SerializedObject<byte[]> serializedObject, Class<?> type);

    Object deserializeFromGenericRecord(SerializedObject<GenericRecord> serializedObject, Class<?> type);

    /**
     * Determines if strategy supports given payloadType, this means that we have either a SpecificRecordBase generated
     * from schema using apchae-avro-maven-plugin or a kotlinx serializable class, written using avro4k.
     *
     * @param payloadType for example BankAccountCreated.class
     * @return <code>true</code> if type is supported by this strategy
     */
    @Override
    boolean test(Class<?> payloadType);
}
