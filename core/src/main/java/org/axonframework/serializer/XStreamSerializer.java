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
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.CompactWriter;
import com.thoughtworks.xstream.io.xml.Dom4JReader;
import com.thoughtworks.xstream.mapper.Mapper;
import org.axonframework.common.Assert;
import org.axonframework.common.SerializationException;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.MetaData;
import org.dom4j.Document;
import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * @author Frank Versnel
 * @see com.thoughtworks.xstream.XStream
 * @since 1.2
 */
public class XStreamSerializer implements Serializer {

    private static final Charset DEFAULT_CHARSET_NAME = Charset.forName("UTF-8");
    private final XStream xStream;
    private final Charset charset;
    private volatile UpcasterChain upcasters;
    private ConverterFactory converterFactory;

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
        this(charset, new XStream());
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     *
     * @param charset The character set to use
     * @param xStream The XStream instance to use
     */
    public XStreamSerializer(Charset charset, XStream xStream) {
        this(charset, xStream, new ChainingConverterFactory());
    }

    /**
     * Initialize the serializer using the given <code>charset</code> and <code>xStream</code> instance. The given
     * <code>converterFactory</code> is used to convert serialized objects for use by Upcasters. The
     * <code>xStream</code> instance is configured with several converters for the most common types in Axon.
     *
     * @param charset          The character set to use
     * @param xStream          The XStream instance to use
     * @param converterFactory The factory providing the converter instances for upcasters
     */
    public XStreamSerializer(Charset charset, XStream xStream, ConverterFactory converterFactory) {
        this.charset = charset;
        this.xStream = xStream;
        this.converterFactory = converterFactory;
        this.upcasters = new UpcasterChain(converterFactory, new ArrayList<Upcaster>());
        xStream.registerConverter(new JodaTimeConverter());
        xStream.addImmutableType(UUID.class);
        xStream.aliasPackage("axon.domain", "org.axonframework.domain");
        xStream.aliasPackage("axon.es", "org.axonframework.eventsourcing");

        xStream.alias("domain-event", GenericDomainEventMessage.class);
        xStream.alias("event", GenericEventMessage.class);

        // for backward compatibility
        xStream.alias("localDateTime", DateTime.class);
        xStream.alias("dateTime", DateTime.class);
        xStream.alias("uuid", UUID.class);
        xStream.useAttributeFor("eventRevision", EventMessage.class);

        xStream.alias("meta-data", MetaData.class);
        xStream.registerConverter(new MetaDataConverter(xStream.getMapper()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation marshals the given <code>object</code> to Compact XML (see {@link
     * com.thoughtworks.xstream.io.xml.CompactWriter}) and write the bytes to the given <code>outputStream</code>.
     * Bytes are written using the character set provided during initialization of the serializer.
     *
     * @see com.thoughtworks.xstream.io.xml.CompactWriter
     */
    @Override
    public SerializedType serialize(Object object, OutputStream outputStream) {
        xStream.marshal(object, new CompactWriter(new OutputStreamWriter(outputStream, charset)));
        return new SimpleSerializedType(typeIdentifierOf(object.getClass()), revisionOf(object.getClass()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SerializedObject serialize(Object object) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serialize(object, baos);
        return new SimpleSerializedObject(baos.toByteArray(), typeIdentifierOf(object.getClass()),
                                          revisionOf(object.getClass()));
    }

    /**
     * Returns the revision number for the given <code>type</code>. The default implementation checks for an {@link
     * Revision @Revision} annotation, and returns <code>0</code> if none was found. This method can be safely
     * overridden by subclasses.
     * <p/>
     * The revision number is used by upcasters to decide whether they need to process a certain serialized event.
     * Generally, the revision number needs to be increased each time the structure of an event has been changed in an
     * incompatible manner.
     *
     * @param type The type for which to return the revision number
     * @return the revision number for the given <code>type</code>
     */
    protected int revisionOf(Class<?> type) {
        Revision revision = type.getAnnotation(Revision.class);
        return revision == null ? 0 : revision.value();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public List<Object> deserialize(SerializedObject serializedObject) {
        List<IntermediateRepresentation> upcastedSerializedObjects = upcasters.upcast(serializedObject);
        
        List<Object> deserializedObjects = new ArrayList<Object>();
        for(IntermediateRepresentation upcastedSerializedObject : upcastedSerializedObjects) {
            if ("org.dom4j.Document".equals(upcastedSerializedObject.getContentType().getName())) {
                deserializedObjects.add(xStream.unmarshal(new Dom4JReader((Document) upcastedSerializedObject.getData())));
            } else {
                ContentTypeConverter converter =
                        converterFactory.getConverter(upcastedSerializedObject.getContentType(), InputStream.class);
                IntermediateRepresentation convertedUpcastedSerializedObject =
                        converter.convert(upcastedSerializedObject);

                deserializedObjects.add(xStream.fromXML(
                        new InputStreamReader((InputStream) convertedUpcastedSerializedObject.getData(), charset)));
            }
        }
        return deserializedObjects;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Class> classForType(SerializedType type) {
        List<Class> classes = new ArrayList<Class>();
        for(SerializedType upcastedType : upcasters.upcast(type)) {
            classes.add(xStream.getMapper().realClass(upcastedType.getName()));
        }
        return classes;
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
     * Returns the ConverterFactory used by this serialized. The converter factory allows registration of
     * ContentTypeConverters needed by the upcasters.
     *
     * @return the ConverterFactory used by this serialized
     */
    public ConverterFactory getConverterFactory() {
        return converterFactory;
    }

    private String typeIdentifierOf(Class<?> type) {
        return xStream.getMapper().serializedClass(type);
    }

    /**
     * Sets the upcasters which allow older revisions of serialized objects to be deserialized. Upcasters are evaluated
     * in the order they are provided in the given List. That means that you should take special precaution when an
     * upcaster expects another upcaster to have processed an event.
     * <p/>
     * Any upcaster that relies on another upcaster doing its work first, should be placed <em>after</em> that other
     * upcaster in the given list. Thus for any <em>upcaster B</em> that relies on <em>upcaster A</em> to do its work
     * first, the following must be true: <code>upcasters.indexOf(B) > upcasters.indexOf(A)</code>.
     *
     * @param upcasters the upcasters for this serializer.
     */
    public void setUpcasters(List<Upcaster> upcasters) {
        this.upcasters = new UpcasterChain(converterFactory, upcasters);
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

    /**
     * Class that marshals MetaData in the least verbose way.
     */
    private static final class MetaDataConverter extends MapConverter {

        public MetaDataConverter(Mapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canConvert(Class type) {
            return MetaData.class.equals(type);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            MetaData metaData = (MetaData) source;
            if (!metaData.isEmpty()) {
                super.marshal(new HashMap<String, Object>(metaData), writer, context);
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            if (!reader.hasMoreChildren()) {
                return MetaData.emptyInstance();
            }
            Map<String, Object> contents = new HashMap<String, Object>();
            populateMap(reader, context, contents);
            if (contents.isEmpty()) {
                return MetaData.emptyInstance();
            } else {
                return MetaData.from(contents);
            }
        }
    }
}
