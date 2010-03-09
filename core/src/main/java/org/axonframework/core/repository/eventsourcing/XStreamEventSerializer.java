/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.repository.eventsourcing;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.axonframework.core.DomainEvent;
import org.joda.time.LocalDateTime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/**
 * Implementation of the serializer that uses XStream as underlying serialization mechanism. Events are serialized to
 * XML.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class XStreamEventSerializer implements EventSerializer {

    private final XStream xStream;

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events.
     */
    public XStreamEventSerializer() {
        xStream = new XStream(new XppDriver());
        xStream.registerConverter(new LocalDateTimeConverter());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize(DomainEvent event) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            xStream.marshal(event, new CompactWriter(new OutputStreamWriter(baos, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new EventStoreException("The 'UTF-8' encoding is not supported.", e);
        }
        return baos.toByteArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEvent deserialize(byte[] serializedEvent) {
        try {
            return (DomainEvent) xStream.fromXML(new InputStreamReader(new ByteArrayInputStream(serializedEvent),
                                                                       "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new EventStoreException("The 'UTF-8' encoding is not supported.", e);
        }
    }

    /**
     * XStream Converter to serialize LocalDateTime classes as a String.
     */
    private static class LocalDateTimeConverter implements SingleValueConverter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type.equals(LocalDateTime.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString(Object obj) {
            return obj.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object fromString(String str) {
            return new LocalDateTime(str);
        }
    }
}
