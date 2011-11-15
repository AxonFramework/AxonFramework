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

package org.axonframework.eventstore;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.Dom4JReader;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.SerializationException;
import org.axonframework.domain.EventMessage;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.XStreamSerializer;
import org.dom4j.Document;
import org.dom4j.io.STAXEventReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;

/**
 * Implementation of the serializer that uses XStream as underlying serialization mechanism. Events are serialized to
 * XML. This Serializer wraps a generic XStreamSerializer. In addition to the {@link XStreamSerializer}, this
 * implementation allows for an upcasting mechanism (see {@link EventUpcaster}.
 * <p/>
 * The downside of this implementation compared to the XStreamSerializer is that it is limited to Events.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class XStreamEventSerializer implements Serializer<Object> {

    private final XStreamSerializer delegate;
    private final List<EventUpcaster<Document>> upcasters = new ArrayList<EventUpcaster<Document>>();

    /**
     * Initialize an XStreamEventSerializer that uses XStream to serialize Events. The bytes are returned using UTF-8
     * encoding.
     */
    public XStreamEventSerializer() {
        delegate = new XStreamSerializer();
    }

    /**
     * Initialize an XStreamEventSerializer that uses XStream to serialize Events. The bytes are returned using UTF-8
     * encoding.
     *
     * @param xStream XStream instance to use
     */
    public XStreamEventSerializer(XStream xStream) {
        delegate = new XStreamSerializer(xStream);
    }

    /**
     * Initialize an XStreamEventSerializer that uses XStream to serialize Events. The bytes are returned using thy
     * character set with the given name. If the character set is not supported by the JVM an
     * UnsupportedCharsetException is thrown.
     *
     * @param charsetName The name of the character set to use.
     */
    public XStreamEventSerializer(String charsetName) {
        delegate = new XStreamSerializer(Charset.forName(charsetName));
    }

    /**
     * Initialize an XStreamEventSerializer that uses XStream to serialize Events. The bytes are returned using given
     * character set. If the character set is not supported by the JVM an UnsupportedCharsetException is thrown.
     *
     * @param charset The character set to use.
     */
    public XStreamEventSerializer(Charset charset) {
        delegate = new XStreamSerializer(charset);
    }

    /**
     * Initialize an XStreamEventSerializer that uses XStream to serialize Events. The bytes are returned using given
     * character set.
     *
     * @param charset         The character set to use.
     * @param providedXStream XStream instance to use
     */
    public XStreamEventSerializer(Charset charset, XStream providedXStream) {
        if (providedXStream != null) {
            delegate = new XStreamSerializer(charset, providedXStream);
        } else {
            delegate = new XStreamSerializer(charset);
        }
    }

    /**
     * Initializes an XStreamEventSerializer with the given <code>delegate</code> to perform the actual serialization
     * logic.
     *
     * @param delegate The (generic) serializer to perform actual serialization with
     */
    public XStreamEventSerializer(XStreamSerializer delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize(Object event) {
        return delegate.serialize(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(Object object, OutputStream outputStream) {
        delegate.serialize(object, outputStream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object deserialize(byte[] serializedEvent) {
        try {
            return deserialize(new ByteArrayInputStream(serializedEvent));
        } catch (IOException e) {
            throw new SerializationException(
                    "The impossible has happened: a ByteArrayInputStream throwing an IO Exception", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object deserialize(InputStream inputStream) throws IOException {
        if (upcasters.isEmpty()) {
            return delegate.deserialize(inputStream);
        } else {
            Document document = readDocument(inputStream);
            for (EventUpcaster<Document> upcaster : upcasters) {
                document = upcaster.upcast(document);
            }
            return (EventMessage) delegate.deserialize(new Dom4JReader(document));
        }
    }

    /**
     * Reads the given <code>serializedEvent</code> into a Dom4J document. The default implementation uses a StAX
     * reader.
     * <p/>
     * This method can be safely overridden to alter the deserialization mechanism. Make sure to use the correct
     * charset
     * (using {@link #getCharset()}) when converting characters to bytes and vice versa.
     *
     * @param serializedEvent The bytes containing the serialized event
     * @return a Dom4J Document representation of the event
     */
    protected Document readDocument(byte[] serializedEvent) {
        return readDocument(new ByteArrayInputStream(serializedEvent));
    }

    /**
     * Reads the event from the given <code>stream</code> into a Dom4J document. The default implementation uses a StAX
     * reader.
     * <p/>
     * This method can be safely overridden to alter the deserialization mechanism. Make sure to use the correct
     * charset
     * (using {@link #getCharset()}) when converting characters to bytes and vice versa.
     *
     * @param stream The stream containing the serialized event
     * @return a Dom4J Document representation of the event
     */
    protected Document readDocument(InputStream stream) {
        try {
            STAXEventReader reader = new STAXEventReader();
            return reader.readDocument(new InputStreamReader(stream, delegate.getCharset()));
        } catch (XMLStreamException e) {
            throw new SerializationException("Exception while preprocessing events", e);
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
        delegate.addAlias(name, type);
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
        delegate.addPackageAlias(alias, pkgName);
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
        delegate.addFieldAlias(alias, definedIn, fieldName);
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
        return delegate.getXStream();
    }

    /**
     * Returns the character set used to serialize XML to bytes and vice versa.
     *
     * @return the character set used to serialize XML to bytes and vice versa
     */
    public Charset getCharset() {
        return delegate.getCharset();
    }

    /**
     * Sets the event upcasters the serializer may use. Note that this serializer only supports the dom4j Document
     * representation of upcasters. This means they should all implement <code>EventUpcaster&lt;Document&gt;</code>.
     *
     * @param eventUpcasters The upcasters to assign to this serializer
     */
    public void setEventUpcasters(List<EventUpcaster<Document>> eventUpcasters) {
        assertSupportDom4jDocument(eventUpcasters);
        this.upcasters.clear();
        this.upcasters.addAll(eventUpcasters);
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
