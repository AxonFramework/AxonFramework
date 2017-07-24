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

import org.axonframework.common.io.IOUtils;

import java.io.*;

/**
 * Serializer implementation that uses Java serialization to serialize and deserialize object instances. This
 * implementation is very suitable if the life span of the serialized objects allows classes to remain unchanged. If
 * Class definitions need to be changed during the object's life cycle, another implementation, like the
 * {@link org.axonframework.serialization.xml.XStreamSerializer} might be a more suitable alternative.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JavaSerializer extends AbstractJavaSerializer{

    /**
     * Initialize the serializer using a SerialVersionUIDRevisionResolver, which uses the SerialVersionUID field of the
     * serializable object as the Revision.
     */
    public JavaSerializer() {
        this(new SerialVersionUIDRevisionResolver());
    }

    /**
     * Initialize the serializer using a SerialVersionUIDRevisionResolver.
     *
     * @param revisionResolver The revision resolver providing the revision numbers for a given class
     */
    public JavaSerializer(RevisionResolver revisionResolver) {
        super(revisionResolver);
    }

    @Override
    protected void doSerialize(OutputStream outputStream, Object instance) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        try {
            oos.writeObject(instance);
        } finally {
            oos.flush();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T doDeserialize(InputStream inputStream) throws ClassNotFoundException, IOException {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(inputStream);
            return (T) ois.readObject();
        } finally {
            IOUtils.closeQuietly(ois);
        }
    }
}
