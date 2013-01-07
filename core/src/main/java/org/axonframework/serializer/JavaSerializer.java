/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Serializer implementation that uses Java serialization to serialize and deserialize object instances. This
 * implementation is very suitable if the life span of the serialized objects allows classes to remain unchanged. If
 * Class definitions need to be changed during the object's life cycle, another implementation, like the
 * {@link org.axonframework.serializer.xml.XStreamSerializer} might be a more suitable alternative.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class JavaSerializer implements Serializer {

    private final ConverterFactory converterFactory = new ChainingConverterFactory();
    private final RevisionResolver revisionResolver;

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
        this.revisionResolver = revisionResolver;
    }

    @Override
    public <T> SerializedObject<T> serialize(Object instance, Class<T> expectedType) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            try {
                oos.writeObject(instance);
            } finally {
                oos.flush();
            }
        } catch (IOException e) {
            throw new SerializationException("An exception occurred writing serialized data to the output stream", e);
        }
        new SimpleSerializedType(instance.getClass().getName(), revisionOf(instance.getClass()));
        T converted = converterFactory.getConverter(byte[].class, expectedType)
                                      .convert(baos.toByteArray());
        return new SimpleSerializedObject<T>(converted, expectedType, instance.getClass().getName(),
                                             revisionOf(instance.getClass()));
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return (converterFactory.hasConverter(byte[].class, expectedRepresentation));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        SerializedObject<InputStream> converted = converterFactory.getConverter(serializedObject.getContentType(),
                                                                                InputStream.class)
                                                                  .convert(serializedObject);
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(converted.getData());
            return (T) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new SerializationException("An error occurred while deserializing: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SerializationException("The theoretically impossible has just happened: "
                                                     + "An IOException while reading to a ByteArrayInputStream.", e);
        } finally {
            IOUtils.closeQuietly(ois);
        }
    }

    @Override
    public Class classForType(SerializedType type) {
        try {
            return Class.forName(type.getName());
        } catch (ClassNotFoundException e) {
            throw new UnknownSerializedTypeException(type, e);
        }
    }

    @Override
    public SerializedType typeForClass(Class type) {
        return new SimpleSerializedType(type.getName(), revisionOf(type));
    }

    @Override
    public ConverterFactory getConverterFactory() {
        return converterFactory;
    }

    private String revisionOf(Class<?> type) {
        return revisionResolver.revisionOf(type);
    }
}
