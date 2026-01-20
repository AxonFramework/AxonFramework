/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.conversion.avro;

import jakarta.annotation.Nonnull;
import org.apache.avro.generic.GenericRecord;
import org.axonframework.common.infra.DescribableComponent;

import java.util.function.Predicate;

/**
 * Strategy for Avro Converter. A strategy is selected upon the representation of the object in your runtime, assuming
 * that the binary representation is always a binary byte array.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public interface AvroConverterStrategy extends Predicate<Class<?>>, DescribableComponent {

    /**
     * Determines if this strategy supports given <code>payloadType</code>. This means that we have either a
     * {@link org.apache.avro.specific.SpecificRecordBase} generated from a schema using apache-avro-maven-plugin or a
     * kotlinx serializable class, written using avro4k.
     *
     * @param payloadType The payload type of object to de-/serialize, for example
     *                    <code>BankAccountCreated.class</code>.
     * @return <code>true</code> if type is supported by this strategy, <code>false</code> otherwise.
     */
    @Override
    boolean test(Class<?> payloadType);

    /**
     * Converts the given {@code object} to byte array using Avro single-object-encoding.
     *
     * @param object The object to convert.
     * @return The byte array containing Avro Single Object Encoded bytes.
     */
    @Nonnull
    byte[] convertToSingleObjectEncoded(@Nonnull Object object);

    /**
     * Converts from single object encoded byte array.
     *
     * @param bytes An array containing single-object-encoded bytes.
     * @param type  The class of resulting object.
     * @param <T>   The payload type to convert to.
     * @return The deserialized object.
     */
    @Nonnull
    <T> T convertFromSingleObjectEncoded(@Nonnull byte[] bytes,
                                         @Nonnull Class<T> type);

    /**
     * Converts from Apache Avro generic record (intermediate representation).
     *
     * @param genericRecord The input object containing the generic record.
     * @param type          The class of resulting object.
     * @param <T>           The payload type to convert to.
     * @return deserialized object.
     */
    <T> T convertFromGenericRecord(@Nonnull GenericRecord genericRecord,
                                   @Nonnull Class<T> type);

    /**
     * Sets the configuration for the strategy.
     * <p>This method is called during the construction of {@link AvroConverter},
     * passing the configuration to the strategy. The default implementation does nothing, but a strategy might use this
     * method to set up internals.
     * </p>
     * <p>This method is intended to be implemented by the strategy, if it supports configuration options
     * passed via {@link AvroConverterStrategyConfiguration}</p>
     *
     * @param avroConverterStrategyConfiguration configuration passed after construction.
     */
    default void applyStrategyConfiguration(
            @Nonnull AvroConverterStrategyConfiguration avroConverterStrategyConfiguration) {
    }
}
