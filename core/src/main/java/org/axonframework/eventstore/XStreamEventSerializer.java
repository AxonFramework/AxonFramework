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

package org.axonframework.eventstore;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.axonframework.domain.DomainEvent;
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
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    private final String charsetName;

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events. The bytes are returned using UTF-8
     * encoding.
     */
    public XStreamEventSerializer() {
        this(DEFAULT_CHARSET_NAME);
    }

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events. The bytes are returned using given character
     * set. If the character set is not supported by the JVM, any attempt to serialize or deserialize a DomainEvent will
     * result in an EventStoreException.
     *
     * @param charsetName The name of the character set to use.
     */
    public XStreamEventSerializer(String charsetName) {
        xStream = new XStream(new XppDriver());
        xStream.registerConverter(new LocalDateTimeConverter());
        this.charsetName = charsetName;
    }

    /**
     * {@inheritDoc}
     *
     * @throws EventStoreException if the configured encoding is not available in this JVM.
     */
    @Override
    public byte[] serialize(DomainEvent event) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            xStream.marshal(event, new CompactWriter(new OutputStreamWriter(baos, charsetName)));
        } catch (UnsupportedEncodingException e) {
            throw new EventStoreException("The '" + charsetName + "' encoding is not supported.", e);
        }
        return baos.toByteArray();
    }

    /**
     * {@inheritDoc}
     *
     * @throws EventStoreException if the configured encoding is not available in this JVM.
     */
    @Override
    public DomainEvent deserialize(byte[] serializedEvent) {
        try {
            return (DomainEvent) xStream.fromXML(new InputStreamReader(new ByteArrayInputStream(serializedEvent),
                                                                       charsetName));
        } catch (UnsupportedEncodingException e) {
            throw new EventStoreException("The '" + charsetName + "' encoding is not supported.", e);
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

    /**
     * Adds an alias to use instead of the fully qualified class name
     *
     * @param name The alias to use
     * @param type The Class to use the alias for
     * @see XStream#alias(String, Class)
     */
    public void addAlias(String name, Class type) {
        xStream.alias(name, type);
    }

    /**
     * Add an alias for a package. This allows long package names to be shortened considerably. Will also use the alias
     * for subpackages of the provided package.
     * <p/>
     * E.g. an alias of "axoncore" for the package "org.axonframework.core" will use "axoncore.repository" for the
     * package "org.axonframework.core.repository".
     *
     * @param alias   The alias to use.
     * @param pkgName The package to use the alias for
     * @see XStream#aliasPackage(String, String)
     */
    public void addPackageAlias(String alias, String pkgName) {
        xStream.aliasPackage(alias, pkgName);
    }

    /**
     * Adds an alias to use for a given field in the given class.
     *
     * @param alias     The alias to use instead of the original field name
     * @param definedIn The class that defines the field.
     * @param fieldName The name of the field to use the alias for
     * @see XStream#aliasField(String, Class, String)
     */
    public void addFieldAlias(String alias, Class definedIn, String fieldName) {
        xStream.aliasField(alias, definedIn, fieldName);
    }

    /**
     * Returns a reference to the underlying {@link com.thoughtworks.xstream.XStream} instance, that does the actual
     * serialization.
     *
     * @return the XStream instance that does the actual (de)serialization.
     *
     * @see com.thoughtworks.xstream.XStream
     */
    public XStream getXStream() {
        return xStream;
    }
}
