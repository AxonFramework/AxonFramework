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

package org.axonframework.serialization;

import org.axonframework.common.AxonNonTransientException;

import static java.lang.String.format;

/**
 * Exception indicating that an object could not be deserialized, because its serialized type cannot be mapped to a
 * class.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class UnknownSerializedTypeException extends AxonNonTransientException {

    private static final long serialVersionUID = 5423163966186330707L;

    /**
     * Initialize the constructor with a default message, containing details of the given <code>serializedType</code>
     *
     * @param serializedType The serialized type of the object being deserialized
     */
    public UnknownSerializedTypeException(SerializedType serializedType) {
        super(format("Could not deserialize a message. The serialized type is unknown: %s (rev. %s)",
                     serializedType.getName(), serializedType.getRevision()));
    }

    /**
     * Initialize the constructor with a default message, containing details of the given <code>serializedType</code>
     *
     * @param serializedType The serialized type of the object being deserialized
     * @param cause          The cause of this exception
     */
    public UnknownSerializedTypeException(SerializedType serializedType, Throwable cause) {
        super(format("Could not deserialize a message. The serialized type is unknown: %s (rev. %s)",
                     serializedType.getName(), serializedType.getRevision()),
              cause);
    }
}
