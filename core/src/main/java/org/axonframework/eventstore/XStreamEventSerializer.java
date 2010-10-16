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
import com.thoughtworks.xstream.io.xml.Dom4JReader;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.EventBase;
import org.axonframework.serializer.GenericXStreamSerializer;
import org.axonframework.util.AxonConfigurationException;
import org.axonframework.util.SerializationException;
import org.dom4j.Document;
import org.dom4j.io.XPP3Reader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the serializer that uses XStream as underlying serialization mechanism. Events are serialized to
 * XML.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class XStreamEventSerializer implements EventSerializer {

    private GenericXStreamSerializer genericXStreamSerializer;
    private static final Charset DEFAULT_CHARSET_NAME = Charset.forName("UTF-8");
    private List<EventUpcaster<Document>> upcasters = new ArrayList<EventUpcaster<Document>>();
    private Charset charset;

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events. The bytes are returned using UTF-8
     * encoding.
     */
    public XStreamEventSerializer() {
        this(DEFAULT_CHARSET_NAME);
    }

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events. The bytes are returned using thy character
     * set with the given name. If the character set is not supported by the JVM an UnsupportedCharsetException is
     * thrown.
     *
     * @param charsetName The name of the character set to use.
     */
    public XStreamEventSerializer(String charsetName) {
        this(Charset.forName(charsetName));
    }

    /**
     * Initialize an EventSerializer that uses XStream to serialize Events. The bytes are returned using given character
     * set. If the character set is not supported by the JVM an UnsupportedCharsetException is thrown.
     *
     * @param charset The character set to use.
     */
    public XStreamEventSerializer(Charset charset) {
        this.charset = charset;
        genericXStreamSerializer = new GenericXStreamSerializer(charset);
        XStream xStream = genericXStreamSerializer.getXStream();
        xStream.useAttributeFor(EventBase.class, "eventRevision");
        xStream.addImmutableType(AggregateIdentifier.class);
        xStream.aliasType("aggregateIdentifier", AggregateIdentifier.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize(DomainEvent event) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        genericXStreamSerializer.serialize(event, baos);
        return baos.toByteArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DomainEvent deserialize(byte[] serializedEvent) {
        if (upcasters.isEmpty()) {
            return (DomainEvent) genericXStreamSerializer.deserialize(new ByteArrayInputStream(serializedEvent));
        } else {
            XPP3Reader reader = new XPP3Reader();
            Document document;
            try {
                document = reader.read(new InputStreamReader(new ByteArrayInputStream(serializedEvent), charset));
            } catch (Exception e) {
                throw new SerializationException("Exception while preprocessing events", e);
            }
            for (EventUpcaster<Document> upcaster : upcasters) {
                document = upcaster.upcast(document);
            }
            return (DomainEvent) genericXStreamSerializer.deserialize(new Dom4JReader(document));
        }

    }

    /**
     * Adds an alias to use instead of the fully qualified class name.
     *
     * @param name The alias to use
     * @param type The Class to use the alias for
     * @see XStream#alias(String, Class)
     */
    public void addAlias(String name, Class type) {
        genericXStreamSerializer.addAlias(name, type);
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
        genericXStreamSerializer.addPackageAlias(alias, pkgName);
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
        genericXStreamSerializer.addFieldAlias(alias, definedIn, fieldName);
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
        return genericXStreamSerializer.getXStream();
    }

    /**
     * Sets the event upcasters the serializer may use. Note that this serializer only supports the dom4j Document
     * representation of upcasters. This means they should all implement <code>EventUpcaster&lt;Document&gt;</code>.
     *
     * @param eventUpcasters The upcasters to assign to this serializer
     */
    public void setEventUpcasters(List<EventUpcaster<Document>> eventUpcasters) {
        assertSupportDom4jDocument(eventUpcasters);
        this.upcasters = eventUpcasters;
    }

    private void assertSupportDom4jDocument(List<EventUpcaster<Document>> eventUpcasters) {
        for (EventUpcaster<Document> upcaster : eventUpcasters) {
            if (!upcaster.getSupportedRepresentation().isAssignableFrom(Document.class)) {
                throw new AxonConfigurationException(String.format(
                        "The given upcaster [%s] does not support the dom4j Document representation",
                        upcaster.getClass().getSimpleName()));
            }
        }
    }
}
