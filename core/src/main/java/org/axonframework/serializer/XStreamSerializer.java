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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;
import org.axonframework.common.SerializationException;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.StringAggregateIdentifier;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.joda.time.DateTime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Serializer that uses XStream to serialize and deserialize arbitrary objects. The XStream instance is configured to
 * deal with the Classes used in Axon Framework in the most compact fashion.
 * <p/>
 * When running on a Sun JVM, XStream does not pose any restrictions on classes to serialize. On other JVM's, however,
 * you need to either implement Serializable, or provide a default constructor (accessible under the JVM's security
 * policy). That means that for portability, you should do either of these two.
 *
 * @author Allard Buijze
 * @see com.thoughtworks.xstream.XStream
 * @since 1.2
 */
public class XStreamSerializer implements Serializer<Object> {

    private static final Charset DEFAULT_CHARSET_NAME = Charset.forName("UTF-8");

    private final XStream xStream;
    private final Charset charset;

    /**
     * Initialize a generic serializer using the UTF-8 character set. A default XStream instance (with {@link
     * com.thoughtworks.xstream.io.xml.XppDriver}) is used to perform the serialization.
     */
    public XStreamSerializer() {
        this(DEFAULT_CHARSET_NAME);
    }

    /**
     * Initialize a generic serializer using the UTF-8 character set. The provided XStream instance  is used to perform
     * the serialization.
     *
     * @param xStream XStream instance to use
     */
    public XStreamSerializer(XStream xStream) {
        this(DEFAULT_CHARSET_NAME, xStream);
    }

    /**
     * Initialize the serializer using the given <code>charset</code>. A default XStream instance (with {@link
     * com.thoughtworks.xstream.io.xml.XppDriver}) is used to perform the serialization.
     *
     * @param charset The character set to use
     */
    public XStreamSerializer(Charset charset) {
        this(charset, new XStream(new XppDriver()));
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    public XStreamSerializer(Charset charset, XStream xStream) {
        this.charset = charset;
        this.xStream = xStream;
        xStream.registerConverter(new JodaTimeConverter());
        xStream.addImmutableType(UUID.class);
        xStream.addImmutableType(AggregateIdentifier.class);
        xStream.addImmutableType(StringAggregateIdentifier.class);
        xStream.addImmutableType(UUIDAggregateIdentifier.class);
        xStream.registerConverter(new AggregateIdentifierConverter());
        xStream.aliasPackage("axon.domain", "org.axonframework.domain");
        xStream.aliasPackage("axon.es", "org.axonframework.eventsourcing");

        xStream.addDefaultImplementation(StringAggregateIdentifier.class, AggregateIdentifier.class);
        xStream.alias("uuid-id", UUIDAggregateIdentifier.class);
        xStream.alias("aggregate-id", StringAggregateIdentifier.class);
        xStream.alias("string-id", StringAggregateIdentifier.class);
        xStream.alias("domain-event", GenericDomainEventMessage.class);
        xStream.alias("event", GenericEventMessage.class);

        // for backward compatibility
        xStream.alias("localDateTime", DateTime.class);
        xStream.alias("dateTime", DateTime.class);
        xStream.alias("uuid", UUID.class);
        xStream.useAttributeFor("eventRevision", EventMessage.class);
    }

    /**
     * Serialize the given <code>object</code> to Compact XML (see {@link com.thoughtworks.xstream.io.xml.CompactWriter})
     * and write the bytes to the given <code>outputStream</code>. Bytes are written using the character set provided
     * during initialization of the serializer.
     *
     * @param object       The object to serialize.
     * @param outputStream The stream to write bytes to
     * @see com.thoughtworks.xstream.io.xml.CompactWriter
     */
    @Override
    public void serialize(Object object, OutputStream outputStream) {
        xStream.marshal(object, new CompactWriter(new OutputStreamWriter(outputStream, charset)));
    }

    /**
     * Serialize the given <code>object</code> and write the bytes to the given <code>writer</code>.
     *
     * @param object The object to serialize
     * @param writer The writer to write the serialized object o
     */
    public void serialize(Object object, HierarchicalStreamWriter writer) {
        xStream.marshal(object, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] serialize(Object object) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(object, baos);
        return baos.toByteArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object deserialize(InputStream inputStream) {
        return xStream.fromXML(new InputStreamReader(inputStream, charset));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object deserialize(byte[] bytes) {
        return xStream.fromXML(new InputStreamReader(new ByteArrayInputStream(bytes), charset));
    }

    /**
     * Deserialize the object provided by the given <code>hierarchicalStreamReader</code>.
     *
     * @param hierarchicalStreamReader The reader providing the hierarchical (e.g. xml) data.
     * @return the deserialized object
     */
    public Object deserialize(HierarchicalStreamReader hierarchicalStreamReader) {
        return xStream.unmarshal(hierarchicalStreamReader);
    }

    /**
     * Adds an alias to use instead of the fully qualified class name.
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

    /**
     * Returns the character set used to convert character to bytes and vice versa.
     *
     * @return the character set used to convert character to bytes and vice versa
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * XStream Converter to serialize DateTime classes as a String.
     */
    private static final class JodaTimeConverter implements Converter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type != null && DateTime.class.getPackage().equals(type.getPackage());
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            writer.setValue(source.toString());
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            try {
                Constructor constructor = context.getRequiredType().getConstructor(Object.class);
                return constructor.newInstance(reader.getValue());
            } catch (Exception e) {
                throw new SerializationException(String.format(
                        "An exception occurred while deserializing a Joda Time object: %s",
                        context.getRequiredType().getSimpleName()), e);
            }
        }
    }
}
