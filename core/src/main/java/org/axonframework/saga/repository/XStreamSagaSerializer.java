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

package org.axonframework.saga.repository;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.StreamException;
import org.axonframework.saga.Saga;
import org.axonframework.serializer.GenericXStreamSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Implementation of the SagaSerializer that uses XStream to serialize Saga instances to XML. The serialized form
 * returned by this serializer is more flexible, making it safe to use if the Saga's class definition needs to change,
 * and existing Sagas need to be converted.
 * <p/>
 * <strong>Compatibility note:</strong><br/>
 * For backwards compatibility reasons, this serializer is backed by the JavaSagaSerializer to deserialize association
 * values. Prior to version 1.1, association values were always serialized using Java Serialization. New association
 * values will be written (and read) using XStream.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class XStreamSagaSerializer implements SagaSerializer {

    private final GenericXStreamSerializer serializer;
    private final JavaSagaSerializer backupSerializer;

    /**
     * Initialize an XStreamSagaSerializer with UTF-8 character set and default XStream instance.
     */
    public XStreamSagaSerializer() {
        serializer = new GenericXStreamSerializer();
        backupSerializer = new JavaSagaSerializer();
    }

    /**
     * Initialize an XStreamSagaSerializer with given <code>charset</code> and a default XStream instance.
     *
     * @param charset The character set to use to convert the XML to bytes.
     */
    public XStreamSagaSerializer(Charset charset) {
        serializer = new GenericXStreamSerializer(charset);
        backupSerializer = new JavaSagaSerializer();
    }

    /**
     * Initialize an XStreamSagaSerializer with UTF-8 character set and the given <code>xStream</code> instance.
     *
     * @param xStream The XStream instance to use. Default converters and aliases will be registered to it.
     */
    public XStreamSagaSerializer(XStream xStream) {
        serializer = new GenericXStreamSerializer(xStream);
        backupSerializer = new JavaSagaSerializer();
    }

    /**
     * Initialize an XStreamSagaSerializer with given <code>charset</code> and <code>xStream</code> instance.
     *
     * @param charset The character set to use to convert the XML to bytes.
     * @param xStream The XStream instance to use. Default converters and aliases will be registered to it.
     */
    public XStreamSagaSerializer(Charset charset, XStream xStream) {
        serializer = new GenericXStreamSerializer(charset, xStream);
        backupSerializer = new JavaSagaSerializer();
    }

    @Override
    public byte[] serialize(Saga saga) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.serialize(saga, baos);
        return baos.toByteArray();
    }

    @Override
    public Saga deserialize(byte[] serializedSaga) {
        return (Saga) serializer.deserialize(new ByteArrayInputStream(serializedSaga));
    }

    @Override
    public byte[] serializeAssociationValue(Object value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.serialize(value, baos);
        return baos.toByteArray();
    }

    @Override
    public Object deserializeAssociationValue(byte[] serializedAssociationValue) {
        try {
            return serializer.deserialize(new ByteArrayInputStream(serializedAssociationValue));
        } catch (StreamException e) {
            return backupSerializer.deserializeAssociationValue(serializedAssociationValue);
        }
    }
}
