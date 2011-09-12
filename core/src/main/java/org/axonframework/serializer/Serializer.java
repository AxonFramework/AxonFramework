/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface describing a serialization mechanism. Implementations can serialize objects of given type <code>T</code>
 * to an output stream and read the object back in from an input stream.
 *
 * @param <T> The base type the serializer handles.
 * @author Allard Buijze
 * @since 1.2
 */
public interface Serializer<T> {

    /**
     * Serializes the given <code>object</code> to the given <code>outputStream</code>. This method does not close the
     * <code>outputStream</code> after writing the <code>object</code>.
     *
     * @param object       The object to serialize
     * @param outputStream The outputStream to write the serialized data to
     * @throws IOException when an error occurs while writing to the stream
     */
    void serialize(T object, OutputStream outputStream) throws IOException;

    /**
     * Serialize the given <code>object</code> into a byte[].
     *
     * @param object The object to serialize
     * @return the byte array representing the serialized object.
     */
    byte[] serialize(T object);

    /**
     * Deserializes the first object read from the given <code>inputStream</code>. The <code>inputStream</code> is not
     * closed after reading. The resulting object instance is cast to the expected type.
     * <p/>
     * Implementations are encouraged to only read the data needed for deserialization of the first encountered
     * instance.
     *
     * @param inputStream the stream providing the serialized data
     * @return the serialized object, cast to the expected type
     *
     * @throws IOException        when an error occurs while reading from the stream
     * @throws ClassCastException if the first object in the stream is not an instance of &lt;T&gt;.
     */
    T deserialize(InputStream inputStream) throws IOException;

    /**
     * Deserializes the first object read from the given <code>bytes</code>. The <code>bytes</code> are not consumed
     * from the array or modified in any way. The resulting object instance is cast to the expected type.
     *
     * @param bytes the bytes providing the serialized data
     * @return the serialized object, cast to the expected type
     *
     * @throws ClassCastException if the first object in the stream is not an instance of &lt;T&gt;.
     */
    T deserialize(byte[] bytes);
}
