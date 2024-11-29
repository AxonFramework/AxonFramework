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
import org.axonframework.serialization.SerializedObject;

import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * Serialization strategy for Avro Serializer.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
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
    <T> T deserializeFromSingleObjectEncoded(@Nonnull SerializedObject<byte[]> serializedObject,
                                             @Nonnull Class<T> type);

    /**
     * Deserializes from Apache Avro generic record (intermediate representation).
     *
     * @param serializedObject serialized object containing the generic record.
     * @param type             class of resulting object.
     * @param <T>              payload type to deserialize to.
     * @return deserialized object.
     */
    <T> T deserializeFromGenericRecord(SerializedObject<GenericRecord> serializedObject, Class<T> type);

    /**
     * Sets the configuration for the strategy.
     * <p>This method is called by the {@link AvroSerializer.Builder} during instantiation of {@link AvroSerializer},
     * passing the builder configuration to the strategy. The default implementation does nothing, but a strategy might
     * use this method to set up internal.
     * </p>
     * <p>This method is intended to be implemented by the strategy, if it supports configuration options set by the
     * {@link AvroSerializer.Builder}.</p>
     *
     * @param avroSerializerStrategyConfig configuration passed by the builder.
     */
    default void applyConfig(AvroSerializerStrategyConfig avroSerializerStrategyConfig) {

    }
}
