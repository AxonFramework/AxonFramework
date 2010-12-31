/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import com.thoughtworks.xstream.XStream;
import org.axonframework.saga.Saga;
import org.axonframework.serializer.GenericXStreamSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Implementation of the SagaSerializer that uses XStream to serialize Saga instances to XML. The serialized form
 * returned by this serializer is more flexible, making it safe to use if the Saga's class definition needs to change,
 * and existing Sagas need to be converted.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class XStreamSagaSerializer implements SagaSerializer {

    private final GenericXStreamSerializer serializer;

    /**
     * Initialize an XStreamSagaSerializer with UTF-8 character set and default XStream instance.
     */
    public XStreamSagaSerializer() {
        serializer = new GenericXStreamSerializer();
    }

    /**
     * Initialize an XStreamSagaSerializer with UTF-8 character set and the given <code>xStream</code> instance.
     *
     * @param xStream The XStream instance to use. Default converters and aliases will be registered to it.
     */
    public XStreamSagaSerializer(XStream xStream) {
        this(Charset.forName("UFT-8"), xStream);
    }

    /**
     * Initialize an XStreamSagaSerializer with given <code>charset</code> and <code>xStream</code> instance.
     *
     * @param charset The character set to use to convert the XML to bytes.
     * @param xStream The XStream instance to use. Default converters and aliases will be registered to it.
     */
    public XStreamSagaSerializer(Charset charset, XStream xStream) {
        serializer = new GenericXStreamSerializer(charset, xStream);
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
}
