/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serializer.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.serializer.SerializationException;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * Jackson Serializer used to serialize and deserialize Joda DateTime classes. These classes use the convention that
 * the toString() method returns an ISO8601 formatted string, while a constructor accepting an Object will allow
 * one to reconstruct the instant from this String.
 *
 * @param <T> The type of Joda object to serialize
 * @author Allard Buijze
 * @since 2.2
 */
public class JodaDeserializer<T> extends JsonDeserializer<T> {

    private final Constructor<T> constructor;

    /**
     * @param instantType The type of object to serialize into
     * @throws org.axonframework.common.AxonConfigurationException if the given <code>instantType</code> is
     * incompatible with this serializer
     */
    public JodaDeserializer(Class<T> instantType) {
        try {
            this.constructor = instantType.getConstructor(Object.class);
        } catch (NoSuchMethodException e) {
            throw new AxonConfigurationException(
                    "The type " + instantType.getName() + " isn't compatible with the JodaDeserializer", e);
        }
    }

    @Override
    public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        try {
            return constructor.newInstance(jp.readValueAs(String.class));
        } catch (Exception e) {
            throw new SerializationException("Unable to read instant from JSON document", e);
        }
    }
}
